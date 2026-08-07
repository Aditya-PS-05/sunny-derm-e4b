#!/usr/bin/env python3
"""Short-lived private-beta access broker for Sunny.

This service intentionally keeps the reusable upstream credential off devices.
An installation exchanges its random app ID for a short-lived signed token. The
same service validates that token before proxying inference and signs model URLs
that Nginx serves through an internal location.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Optional, Tuple


INSTALLATION_RE = re.compile(r"^[A-Za-z0-9._-]{20,128}$")
REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9._-]{16,128}$")
MODEL_PREFIX = "sunny-pad-smolvlm-500m-v1-gguf/"
MODEL_PATHS = {
    MODEL_PREFIX + "sunny-pad-smolvlm-500m-Q4_K_M.gguf",
    MODEL_PREFIX + "sunny-pad-smolvlm-500m-mmproj-Q8_0.gguf",
    MODEL_PREFIX + "derm.gbnf",
    MODEL_PREFIX + "THIRD_PARTY_NOTICES.txt",
    MODEL_PREFIX + "Apache-2.0.txt",
    MODEL_PREFIX + "manifest.json",
}


class PublicError(Exception):
    def __init__(self, status: int, code: str, details: Optional[dict] = None):
        super().__init__(code)
        self.status = status
        self.code = code
        self.details = details or {}


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.environ.get(name, str(default)))
    except ValueError as error:
        raise RuntimeError(f"{name} must be an integer") from error
    if value <= 0:
        raise RuntimeError(f"{name} must be positive")
    return value


class Config:
    def __init__(self) -> None:
        self.bind = os.environ.get("SUNNY_BIND", "127.0.0.1")
        self.port = _positive_int("SUNNY_PORT", 8095)
        self.public_origin = os.environ["SUNNY_PUBLIC_ORIGIN"].rstrip("/")
        if not self.public_origin.startswith("https://"):
            raise RuntimeError("SUNNY_PUBLIC_ORIGIN must use HTTPS")
        secret_file = Path(os.environ.get("SUNNY_SECRET_FILE", "/etc/sunny-access/secret"))
        self.secret = secret_file.read_bytes().strip()
        if len(self.secret) < 32:
            raise RuntimeError("Sunny access secret must contain at least 32 bytes")
        self.database = Path(os.environ.get("SUNNY_DATABASE", "/var/lib/sunny-access/access.db"))
        self.database.parent.mkdir(parents=True, exist_ok=True)
        self.model_root = Path(os.environ.get("SUNNY_MODEL_ROOT", "/srv/sunny-models")).resolve()
        self.upstream = os.environ.get(
            "SUNNY_INFERENCE_UPSTREAM",
            "http://127.0.0.1:8080/v1/chat/completions",
        )
        if not self.upstream.startswith("http://127.0.0.1:"):
            raise RuntimeError("SUNNY_INFERENCE_UPSTREAM must remain loopback-only")
        self.plan = os.environ.get("SUNNY_BETA_PLAN", "FREE").upper()
        if self.plan not in {"FREE", "PRO"}:
            raise RuntimeError("SUNNY_BETA_PLAN must be FREE or PRO")
        self.token_ttl = _positive_int("SUNNY_ACCESS_TOKEN_TTL_SECONDS", 21_600)
        self.model_ttl = _positive_int("SUNNY_MODEL_URL_TTL_SECONDS", 21_600)
        self.free_monthly = _positive_int("SUNNY_FREE_MONTHLY_LIMIT", 5)
        self.free_daily = _positive_int("SUNNY_FREE_DAILY_LIMIT", 2)
        self.pro_monthly = _positive_int("SUNNY_PRO_MONTHLY_LIMIT", 100)
        self.pro_daily = _positive_int("SUNNY_PRO_DAILY_LIMIT", 25)

    def limits(self, plan: str) -> Tuple[int, int]:
        if plan == "PRO":
            return self.pro_monthly, self.pro_daily
        return self.free_monthly, self.free_daily


CONFIG: Config
DB_LOCK = threading.Lock()


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _b64decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def _json_bytes(value: Any) -> bytes:
    return json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")


def _signature(value: bytes) -> str:
    return _b64url(hmac.new(CONFIG.secret, value, hashlib.sha256).digest())


def sign_token(claims: dict) -> str:
    header = _b64url(_json_bytes({"alg": "HS256", "typ": "JWT"}))
    payload = _b64url(_json_bytes(claims))
    unsigned = f"{header}.{payload}"
    return f"{unsigned}.{_signature(unsigned.encode('ascii'))}"


def verify_token(token: str) -> dict:
    parts = token.split(".")
    if len(parts) != 3:
        raise PublicError(401, "invalid_authorization")
    expected = _signature(f"{parts[0]}.{parts[1]}".encode("ascii"))
    if not hmac.compare_digest(parts[2], expected):
        raise PublicError(401, "invalid_authorization")
    try:
        claims = json.loads(_b64decode(parts[1]).decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        raise PublicError(401, "invalid_authorization")
    if (
        not isinstance(claims, dict)
        or not isinstance(claims.get("sub"), str)
        or claims.get("plan") not in {"FREE", "PRO"}
        or not isinstance(claims.get("exp"), int)
        or claims["exp"] <= int(time.time())
    ):
        raise PublicError(401, "invalid_authorization")
    return claims


def _subject(installation_id: str) -> str:
    digest = hmac.new(CONFIG.secret, f"install:{installation_id}".encode(), hashlib.sha256).hexdigest()
    return digest[:40]


def _utc_day(now: int) -> str:
    return time.strftime("%Y-%m-%d", time.gmtime(now))


def _utc_month(now: int) -> str:
    return time.strftime("%Y-%m", time.gmtime(now))


def _next_utc_day_ms(now: int) -> int:
    current = time.gmtime(now)
    midnight = int(time.mktime((current.tm_year, current.tm_mon, current.tm_mday, 0, 0, 0, 0, 0, 0)))
    # mktime uses local time; calendar.timegm keeps this correct on non-UTC hosts.
    import calendar
    midnight = calendar.timegm((current.tm_year, current.tm_mon, current.tm_mday, 0, 0, 0))
    return (midnight + 86_400) * 1000


def _next_utc_month_ms(now: int) -> int:
    import calendar
    current = time.gmtime(now)
    year, month = current.tm_year, current.tm_mon + 1
    if month == 13:
        year, month = year + 1, 1
    return calendar.timegm((year, month, 1, 0, 0, 0)) * 1000


def _connect() -> sqlite3.Connection:
    connection = sqlite3.connect(str(CONFIG.database), timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


def init_database() -> None:
    with _connect() as connection:
        connection.execute(
            """CREATE TABLE IF NOT EXISTS usage_counters (
                subject TEXT NOT NULL,
                month_key TEXT NOT NULL,
                day_key TEXT NOT NULL,
                month_count INTEGER NOT NULL,
                day_count INTEGER NOT NULL,
                last_request_id TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(subject, month_key)
            )"""
        )


def quota_state(subject: str, monthly_limit: int, daily_limit: int, now: int) -> dict:
    with _connect() as connection:
        row = connection.execute(
            "SELECT month_count, day_count, day_key FROM usage_counters WHERE subject=? AND month_key=?",
            (subject, _utc_month(now)),
        ).fetchone()
    month_used = int(row["month_count"]) if row else 0
    day_used = int(row["day_count"]) if row and row["day_key"] == _utc_day(now) else 0
    return {
        "monthlyLimit": monthly_limit,
        "monthlyRemaining": max(0, monthly_limit - month_used),
        "dailyLimit": daily_limit,
        "dailyRemaining": max(0, daily_limit - day_used),
        "monthResetsAtMillis": _next_utc_month_ms(now),
        "dayResetsAtMillis": _next_utc_day_ms(now),
    }


def reserve_quota(claims: dict, request_id: str) -> Tuple[dict, bool]:
    now = int(time.time())
    subject = claims["sub"]
    monthly_limit = int(claims["monthlyLimit"])
    daily_limit = int(claims["dailyLimit"])
    month_key, day_key = _utc_month(now), _utc_day(now)
    charged = False
    with DB_LOCK, _connect() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute(
            "SELECT * FROM usage_counters WHERE subject=? AND month_key=?",
            (subject, month_key),
        ).fetchone()
        if row and row["last_request_id"] == request_id:
            pass
        else:
            month_count = int(row["month_count"]) if row else 0
            day_count = int(row["day_count"]) if row and row["day_key"] == day_key else 0
            if month_count >= monthly_limit or day_count >= daily_limit:
                connection.rollback()
                quota = quota_state(subject, monthly_limit, daily_limit, now)
                code = "monthly_quota_exceeded" if month_count >= monthly_limit else "daily_quota_exceeded"
                raise PublicError(429, code, {"quota": quota})
            month_count += 1
            day_count += 1
            connection.execute(
                """INSERT INTO usage_counters
                   (subject, month_key, day_key, month_count, day_count, last_request_id, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(subject, month_key) DO UPDATE SET
                     day_key=excluded.day_key, month_count=excluded.month_count,
                     day_count=excluded.day_count, last_request_id=excluded.last_request_id,
                     updated_at=excluded.updated_at""",
                (subject, month_key, day_key, month_count, day_count, request_id, now),
            )
            charged = True
        connection.commit()
    return quota_state(subject, monthly_limit, daily_limit, now), charged


def release_quota(claims: dict, request_id: str) -> None:
    now = int(time.time())
    with DB_LOCK, _connect() as connection:
        row = connection.execute(
            "SELECT * FROM usage_counters WHERE subject=? AND month_key=?",
            (claims["sub"], _utc_month(now)),
        ).fetchone()
        if row and row["last_request_id"] == request_id:
            connection.execute(
                """UPDATE usage_counters SET month_count=MAX(0, month_count-1),
                   day_count=CASE WHEN day_key=? THEN MAX(0, day_count-1) ELSE day_count END,
                   last_request_id='', updated_at=? WHERE subject=? AND month_key=?""",
                (_utc_day(now), now, claims["sub"], _utc_month(now)),
            )


def model_signature(subject: str, path: str, expiry: int) -> str:
    return _signature(f"{subject}\n{path}\n{expiry}".encode("utf-8"))


def signed_model_urls(subject: str, expires_at: int) -> dict:
    expiry = min(expires_at, int(time.time()) + CONFIG.model_ttl)
    assets: Dict[str, str] = {}
    for path in MODEL_PATHS:
        query = urllib.parse.urlencode(
            {"sub": subject, "exp": expiry, "sig": model_signature(subject, path, expiry)}
        )
        assets[path] = (
            f"{CONFIG.public_origin}/v1/models/{urllib.parse.quote(path, safe='/')}?{query}"
        )
    return {"expiresAtMillis": expiry * 1000, "assets": assets}


class SunnyHandler(BaseHTTPRequestHandler):
    server_version = "SunnyAccess/1"
    sys_version = ""

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{self.client_address[0]} {self.command} {self.path.split('?')[0]} " + (fmt % args))

    def _headers(self, status: int, content_type: str, length: Optional[int] = None) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        if length is not None:
            self.send_header("Content-Length", str(length))

    def _json(self, value: dict, status: int = 200, extra_headers: Optional[dict] = None) -> None:
        body = _json_bytes(value)
        self._headers(status, "application/json", len(body))
        for key, val in (extra_headers or {}).items():
            self.send_header(key, str(val))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _public_error(self, error: PublicError) -> None:
        self._json({"error": error.code, **error.details}, error.status)

    def _body(self, limit: int = 8 * 1024 * 1024) -> bytes:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise PublicError(400, "invalid_content_length")
        if length <= 0 or length > limit:
            raise PublicError(413 if length > limit else 400, "request_too_large" if length > limit else "invalid_request")
        body = self.rfile.read(length)
        if len(body) != length:
            raise PublicError(400, "invalid_request")
        return body

    def _bearer(self) -> dict:
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            raise PublicError(401, "authorization_required")
        return verify_token(authorization[7:])

    def do_GET(self) -> None:
        try:
            parsed = urllib.parse.urlsplit(self.path)
            if parsed.path == "/health":
                body = b"ok\n"
                self._headers(200, "text/plain; charset=utf-8", len(body))
                self.end_headers()
                self.wfile.write(body)
                return
            if parsed.path.startswith("/v1/models/"):
                self._serve_model(parsed)
                return
            raise PublicError(404, "not_found")
        except PublicError as error:
            self._public_error(error)
        except Exception:
            self._json({"error": "internal_error"}, 500)

    def do_HEAD(self) -> None:
        self.do_GET()

    def do_POST(self) -> None:
        try:
            parsed = urllib.parse.urlsplit(self.path)
            if parsed.path == "/v1/access/free":
                self._issue_access()
                return
            if parsed.path == "/v1/inference/v1/chat/completions":
                self._proxy_inference()
                return
            raise PublicError(404, "not_found")
        except PublicError as error:
            self._public_error(error)
        except Exception:
            self._json({"error": "internal_error"}, 500)

    def _issue_access(self) -> None:
        try:
            payload = json.loads(self._body(16 * 1024).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            raise PublicError(400, "invalid_request")
        installation_id = payload.get("installationId") if isinstance(payload, dict) else None
        if not isinstance(installation_id, str) or not INSTALLATION_RE.fullmatch(installation_id):
            raise PublicError(400, "invalid_installation_id")
        now = int(time.time())
        expires = now + CONFIG.token_ttl
        subject = _subject(installation_id)
        monthly, daily = CONFIG.limits(CONFIG.plan)
        quota = quota_state(subject, monthly, daily, now)
        claims = {
            "sub": subject,
            "plan": CONFIG.plan,
            "monthlyLimit": monthly,
            "dailyLimit": daily,
            "exp": expires,
            "nonce": secrets.token_urlsafe(18),
        }
        result = {
            "plan": CONFIG.plan,
            "proTrialEndsAtMillis": None,
            "verifiedUntilMillis": expires * 1000,
            "cloudInference": {
                "baseUrl": f"{CONFIG.public_origin}/v1/inference",
                "bearerToken": sign_token(claims),
                "expiresAtMillis": expires * 1000,
                "quota": quota,
            },
            "modelDownloads": signed_model_urls(subject, expires) if CONFIG.plan == "PRO" else None,
        }
        self._json(result)

    def _serve_model(self, parsed: urllib.parse.SplitResult) -> None:
        path = urllib.parse.unquote(parsed.path[len("/v1/models/"):])
        if path not in MODEL_PATHS:
            raise PublicError(404, "model_asset_not_found")
        query = urllib.parse.parse_qs(parsed.query)
        subject = query.get("sub", [""])[0]
        signature = query.get("sig", [""])[0]
        try:
            expiry = int(query.get("exp", ["0"])[0])
        except ValueError:
            raise PublicError(401, "invalid_download_signature")
        now = int(time.time())
        if not subject or expiry <= now or expiry > now + CONFIG.model_ttl + 60:
            raise PublicError(401, "download_url_expired")
        if not hmac.compare_digest(signature, model_signature(subject, path, expiry)):
            raise PublicError(401, "invalid_download_signature")
        model = (CONFIG.model_root / path).resolve()
        if CONFIG.model_root not in model.parents or not model.is_file():
            raise PublicError(404, "model_asset_not_found")
        self.send_response(200)
        self.send_header("X-Accel-Redirect", f"/_protected_sunny_models/{path}")
        self.send_header("Content-Disposition", f'attachment; filename="{model.name}"')
        self.send_header("Cache-Control", "private, no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()

    def _proxy_inference(self) -> None:
        claims = self._bearer()
        body = self._body()
        request_id = self.headers.get("X-Sunny-Analysis-Id", "")
        if not REQUEST_ID_RE.fullmatch(request_id):
            request_id = secrets.token_urlsafe(18)
        quota, charged = reserve_quota(claims, request_id)
        request = urllib.request.Request(
            CONFIG.upstream,
            data=body,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            try:
                response = urllib.request.urlopen(request, timeout=180)
                status, response_body = response.status, response.read(2 * 1024 * 1024)
                content_type = response.headers.get("Content-Type", "application/json")
            except urllib.error.HTTPError as error:
                status, response_body = error.code, error.read(2 * 1024 * 1024)
                content_type = error.headers.get("Content-Type", "application/json")
            if not 200 <= status < 300 and charged:
                release_quota(claims, request_id)
            self._headers(status, content_type, len(response_body))
            for key, value in {
                "X-Sunny-Monthly-Limit": quota["monthlyLimit"],
                "X-Sunny-Monthly-Remaining": quota["monthlyRemaining"],
                "X-Sunny-Daily-Limit": quota["dailyLimit"],
                "X-Sunny-Daily-Remaining": quota["dailyRemaining"],
                "X-Sunny-Month-Reset": quota["monthResetsAtMillis"],
                "X-Sunny-Day-Reset": quota["dayResetsAtMillis"],
            }.items():
                self.send_header(key, str(value))
            self.end_headers()
            self.wfile.write(response_body)
        except Exception:
            if charged:
                release_quota(claims, request_id)
            raise


def main() -> None:
    global CONFIG
    CONFIG = Config()
    init_database()
    server = ThreadingHTTPServer((CONFIG.bind, CONFIG.port), SunnyHandler)
    print(f"Sunny beta access listening on {CONFIG.bind}:{CONFIG.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
