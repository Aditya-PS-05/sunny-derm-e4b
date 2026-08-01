interface Env {
  GOOGLE_PLAY_PACKAGE: string;
  PRO_PRODUCT_ID: string;
  GOOGLE_SERVICE_ACCOUNT_EMAIL: string;
  GOOGLE_PRIVATE_KEY: string;
  MODEL_TOKEN_SECRET: string;
  INFERENCE_UPSTREAM_URL: string;
  INFERENCE_UPSTREAM_TOKEN: string;
  FREE_MONTHLY_LIMIT: string;
  FREE_DAILY_LIMIT: string;
  PRO_MONTHLY_LIMIT: string;
  PRO_DAILY_LIMIT: string;
  ACCESS_TOKEN_TTL_SECONDS: string;
  MODEL_URL_TTL_SECONDS: string;
  DB: D1Database;
  MODEL_BUCKET?: R2Bucket;
}

type Plan = "FREE" | "PRO";

interface AccessClaims {
  sub: string;
  plan: Plan;
  monthlyLimit: number;
  dailyLimit: number;
  exp: number;
  nonce: string;
}

interface UsageRow {
  month_count: number;
  day_count: number;
  day_key: string;
  last_request_id: string;
}

interface ReservedQuota { quota: QuotaState; charged: boolean }

interface QuotaState {
  monthlyLimit: number;
  monthlyRemaining: number;
  dailyLimit: number;
  dailyRemaining: number;
  monthResetsAtMillis: number;
  dayResetsAtMillis: number;
}

const encoder = new TextEncoder();
const GOOGLE_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const MODEL_PREFIX = "sunny-moe-2.2b-v4-gguf/";
const MODEL_PATHS = new Set([
  `${MODEL_PREFIX}sunny-moe-text-Q4_K_M.gguf`,
  `${MODEL_PREFIX}sunny-moe-mmproj-F16.gguf`,
  `${MODEL_PREFIX}manifest.json`,
]);
const ACTIVE_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);
let googleTokenCache: { value: string; expiresAtMillis: number } | null = null;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (request.method === "POST" && url.pathname === "/v1/access/free") {
        return await issueFreeAccess(request, env, url.origin);
      }
      if (request.method === "POST" && url.pathname === "/v1/entitlements/google-play") {
        return await verifyPlayPurchase(request, env, url.origin);
      }
      if (request.method === "POST" && url.pathname === "/v1/model-downloads") {
        return await refreshModelDownloads(request, env, url.origin);
      }
      if (request.method === "GET" && url.pathname.startsWith("/v1/models/")) {
        return await serveSignedModel(request, env, url);
      }
      if (request.method === "POST" &&
          url.pathname === "/v1/inference/v1/chat/completions") {
        return await proxyInference(request, env);
      }
      if (request.method === "GET" && url.pathname === "/health") {
        return response("ok\n", 200, "text/plain; charset=utf-8");
      }
      return json({ error: "not_found" }, 404);
    } catch (error) {
      const message = error instanceof PublicError ? error.message : "internal_error";
      const status = error instanceof PublicError ? error.status : 500;
      const details = error instanceof PublicError ? error.details : undefined;
      return json({ error: message, ...(details ?? {}) }, status);
    }
  },
  async scheduled(
    _controller: ScheduledController,
    env: Env,
    context: ExecutionContext,
  ): Promise<void> {
    const cutoff = Date.now() - 62 * 86_400_000;
    context.waitUntil(
      env.DB.prepare("DELETE FROM usage_counters WHERE updated_at < ?1")
        .bind(cutoff)
        .run()
        .then(() => undefined),
    );
  },
};

async function issueFreeAccess(request: Request, env: Env, origin: string): Promise<Response> {
  requireCoreBindings(env);
  const body = await readSmallJson(request);
  const installationId = requireInstallationId(body.installationId);
  const subject = await opaqueSubject(`free:${installationId}`, env.MODEL_TOKEN_SECRET);
  return accessResponse(env, origin, subject, "FREE", null);
}

async function verifyPlayPurchase(request: Request, env: Env, origin: string): Promise<Response> {
  requireCoreBindings(env);
  requirePlaySecrets(env);
  const body = await readSmallJson(request);
  const purchaseToken = body.purchaseToken;
  const claimedProducts = body.products;
  requireInstallationId(body.installationId);
  if (typeof purchaseToken !== "string" || purchaseToken.length < 20 || purchaseToken.length > 4096 ||
      !Array.isArray(claimedProducts) || !claimedProducts.every((p) => typeof p === "string")) {
    throw new PublicError(400, "invalid_request");
  }

  const googleToken = await googleAccessToken(env);
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
    `${encodeURIComponent(env.GOOGLE_PLAY_PACKAGE)}/purchases/subscriptionsv2/tokens/` +
    encodeURIComponent(purchaseToken);
  const google = await fetch(endpoint, {
    headers: { Authorization: `Bearer ${googleToken}` },
  });
  if (!google.ok) {
    throw new PublicError(google.status === 404 || google.status === 410 ? 401 : 502,
      "purchase_not_verified");
  }
  const purchase = await google.json<Record<string, unknown>>();
  if (!ACTIVE_STATES.has(String(purchase.subscriptionState ?? ""))) {
    throw new PublicError(401, "subscription_inactive");
  }
  const lineItems = Array.isArray(purchase.lineItems)
    ? purchase.lineItems.filter(isObject)
    : [];
  const now = Date.now();
  const proItems = lineItems.filter((item) => {
    const expiry = Date.parse(String(item.expiryTime ?? ""));
    return String(item.productId ?? "") === env.PRO_PRODUCT_ID &&
      Number.isFinite(expiry) && expiry > now;
  });
  if (!claimedProducts.includes(env.PRO_PRODUCT_ID) || proItems.length === 0) {
    throw new PublicError(401, "pro_subscription_required");
  }
  const expiry = Math.max(...proItems.map((item) => Date.parse(String(item.expiryTime))));
  const subject = await opaqueSubject(`pro:${purchaseToken}`, env.MODEL_TOKEN_SECRET);
  return accessResponse(env, origin, subject, "PRO", expiry);
}

async function accessResponse(
  env: Env,
  origin: string,
  subject: string,
  plan: Plan,
  paidExpiryMillis: number | null,
): Promise<Response> {
  const now = Date.now();
  const tokenTtlMillis = positiveInt(env.ACCESS_TOKEN_TTL_SECONDS, 21_600) * 1000;
  const expiresAtMillis = Math.min(paidExpiryMillis ?? Number.MAX_SAFE_INTEGER, now + tokenTtlMillis);
  const limits = planLimits(env, plan);
  const quota = await currentQuota(env.DB, subject, limits.monthly, limits.daily, now);
  const claims: AccessClaims = {
    sub: subject,
    plan,
    monthlyLimit: limits.monthly,
    dailyLimit: limits.daily,
    exp: Math.floor(expiresAtMillis / 1000),
    nonce: crypto.randomUUID(),
  };
  const bearerToken = await signAccessToken(claims, env.MODEL_TOKEN_SECRET);
  const modelDownloads = plan === "PRO"
    ? await signedModelDownloads(origin, subject, env, paidExpiryMillis ?? expiresAtMillis)
    : null;
  return json({
    plan,
    proTrialEndsAtMillis: null,
    verifiedUntilMillis: expiresAtMillis,
    cloudInference: {
      baseUrl: `${origin}/v1/inference`,
      bearerToken,
      expiresAtMillis,
      quota,
    },
    modelDownloads,
  });
}

async function refreshModelDownloads(request: Request, env: Env, origin: string): Promise<Response> {
  requireCoreBindings(env);
  const claims = await requireAccessClaims(request, env.MODEL_TOKEN_SECRET);
  if (claims.plan !== "PRO") throw new PublicError(403, "pro_subscription_required");
  return json({
    modelDownloads: await signedModelDownloads(
      origin,
      claims.sub,
      env,
      claims.exp * 1000,
    ),
  });
}

async function proxyInference(request: Request, env: Env): Promise<Response> {
  requireCoreBindings(env);
  const claims = await requireAccessClaims(request, env.MODEL_TOKEN_SECRET);
  if (!env.INFERENCE_UPSTREAM_URL?.startsWith("https://") || !env.INFERENCE_UPSTREAM_TOKEN) {
    throw new PublicError(503, "inference_unavailable");
  }
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > 8 * 1024 * 1024) throw new PublicError(413, "request_too_large");
  const body = await request.arrayBuffer();
  if (body.byteLength > 8 * 1024 * 1024) throw new PublicError(413, "request_too_large");

  const analysisId = validRequestId(request.headers.get("x-sunny-analysis-id")) ?? crypto.randomUUID();
  const reservation = await reserveQuota(env.DB, claims, analysisId);
  let upstream: Response;
  try {
    upstream = await fetch(env.INFERENCE_UPSTREAM_URL, {
      method: "POST",
      headers: {
        authorization: `Bearer ${env.INFERENCE_UPSTREAM_TOKEN}`,
        "content-type": "application/json",
        accept: "application/json",
      },
      body,
    });
  } catch (error) {
    if (reservation.charged) await releaseQuota(env.DB, claims.sub, analysisId, Date.now());
    throw error;
  }
  if (!upstream.ok && reservation.charged) {
    await releaseQuota(env.DB, claims.sub, analysisId, Date.now());
  }

  const headers = secureHeaders();
  headers.set("content-type", upstream.headers.get("content-type") ?? "application/json");
  headers.set("cache-control", "no-store");
  applyQuotaHeaders(headers, reservation.quota);
  return new Response(upstream.body, { status: upstream.status, headers });
}

async function reserveQuota(
  db: D1Database,
  claims: AccessClaims,
  analysisId: string,
): Promise<ReservedQuota> {
  const now = Date.now();
  const monthKey = utcMonthKey(now);
  const dayKey = utcDayKey(now);
  const before = await db.prepare(`
    SELECT month_count, day_count, day_key, last_request_id
    FROM usage_counters WHERE subject = ?1 AND month_key = ?2
  `).bind(claims.sub, monthKey).first<UsageRow>();
  const row = await db.prepare(`
    INSERT INTO usage_counters
      (subject, month_key, day_key, month_count, day_count, last_request_id, updated_at)
    VALUES (?1, ?2, ?3, 1, 1, ?4, ?5)
    ON CONFLICT(subject, month_key) DO UPDATE SET
      month_count = usage_counters.month_count +
        CASE WHEN usage_counters.last_request_id = excluded.last_request_id THEN 0 ELSE 1 END,
      day_count = CASE
        WHEN usage_counters.last_request_id = excluded.last_request_id AND
             usage_counters.day_key = excluded.day_key THEN usage_counters.day_count
        WHEN usage_counters.last_request_id = excluded.last_request_id THEN 0
        WHEN usage_counters.day_key = excluded.day_key THEN usage_counters.day_count + 1
        ELSE 1
      END,
      day_key = excluded.day_key,
      last_request_id = excluded.last_request_id,
      updated_at = excluded.updated_at
    WHERE usage_counters.last_request_id = excluded.last_request_id OR (
      usage_counters.month_count < ?6 AND
      (usage_counters.day_key != excluded.day_key OR usage_counters.day_count < ?7)
    )
    RETURNING month_count, day_count, day_key, last_request_id
  `).bind(
    claims.sub,
    monthKey,
    dayKey,
    analysisId,
    now,
    claims.monthlyLimit,
    claims.dailyLimit,
  ).first<UsageRow>();
  if (row === null) {
    const current = await db.prepare(`
      SELECT month_count, day_count, day_key, last_request_id
      FROM usage_counters WHERE subject = ?1 AND month_key = ?2
    `).bind(claims.sub, monthKey).first<UsageRow>();
    const dailyUsed = current?.day_key === dayKey ? current.day_count : 0;
    const monthlyUsed = current?.month_count ?? 0;
    const monthlyExhausted = monthlyUsed >= claims.monthlyLimit;
    throw new PublicError(429, monthlyExhausted ? "monthly_quota_exceeded" : "daily_quota_exceeded", {
      quota: quotaState(claims.monthlyLimit, monthlyUsed, claims.dailyLimit, dailyUsed, now),
    });
  }
  return {
    quota: quotaState(claims.monthlyLimit, row.month_count, claims.dailyLimit, row.day_count, now),
    charged: before === null || row.month_count > before.month_count,
  };
}

async function releaseQuota(
  db: D1Database,
  subject: string,
  analysisId: string,
  now: number,
): Promise<void> {
  await db.prepare(`
    UPDATE usage_counters SET
      month_count = MAX(0, month_count - 1),
      day_count = CASE WHEN day_key = ?3 THEN MAX(0, day_count - 1) ELSE day_count END,
      last_request_id = '',
      updated_at = ?4
    WHERE subject = ?1 AND month_key = ?2 AND last_request_id = ?5
  `).bind(subject, utcMonthKey(now), utcDayKey(now), now, analysisId).run();
}

async function currentQuota(
  db: D1Database,
  subject: string,
  monthlyLimit: number,
  dailyLimit: number,
  now: number,
): Promise<QuotaState> {
  const row = await db.prepare(`
    SELECT month_count, day_count, day_key, last_request_id
    FROM usage_counters WHERE subject = ?1 AND month_key = ?2
  `).bind(subject, utcMonthKey(now)).first<UsageRow>();
  const dailyUsed = row?.day_key === utcDayKey(now) ? row.day_count : 0;
  return quotaState(monthlyLimit, row?.month_count ?? 0, dailyLimit, dailyUsed, now);
}

function quotaState(
  monthlyLimit: number,
  monthlyUsed: number,
  dailyLimit: number,
  dailyUsed: number,
  now: number,
): QuotaState {
  return {
    monthlyLimit,
    monthlyRemaining: Math.max(0, monthlyLimit - monthlyUsed),
    dailyLimit,
    dailyRemaining: Math.max(0, dailyLimit - dailyUsed),
    monthResetsAtMillis: nextUtcMonth(now),
    dayResetsAtMillis: nextUtcDay(now),
  };
}

function applyQuotaHeaders(headers: Headers, quota: QuotaState): void {
  headers.set("x-sunny-monthly-limit", String(quota.monthlyLimit));
  headers.set("x-sunny-monthly-remaining", String(quota.monthlyRemaining));
  headers.set("x-sunny-daily-limit", String(quota.dailyLimit));
  headers.set("x-sunny-daily-remaining", String(quota.dailyRemaining));
  headers.set("x-sunny-month-reset", String(quota.monthResetsAtMillis));
  headers.set("x-sunny-day-reset", String(quota.dayResetsAtMillis));
}

async function signedModelDownloads(
  origin: string,
  subject: string,
  env: Env,
  authorizationExpiresAtMillis: number,
): Promise<object> {
  const now = Date.now();
  const expiresAtMillis = Math.min(
    authorizationExpiresAtMillis,
    now + positiveInt(env.MODEL_URL_TTL_SECONDS, 43_200) * 1000,
  );
  const exp = Math.floor(expiresAtMillis / 1000);
  const assets: Record<string, string> = {};
  for (const path of MODEL_PATHS) {
    const signature = await signDownload(subject, path, exp, env.MODEL_TOKEN_SECRET);
    const encodedPath = path.split("/").map(encodeURIComponent).join("/");
    const url = new URL(`${origin}/v1/models/${encodedPath}`);
    url.searchParams.set("sub", subject);
    url.searchParams.set("exp", String(exp));
    url.searchParams.set("sig", signature);
    assets[path] = url.toString();
  }
  return { expiresAtMillis, assets };
}

async function serveSignedModel(request: Request, env: Env, url: URL): Promise<Response> {
  if (request.method !== "GET") throw new PublicError(405, "method_not_allowed");
  requireCoreBindings(env);
  const path = url.pathname.slice("/v1/models/".length).split("/")
    .map((part) => decodeURIComponent(part)).join("/");
  if (!MODEL_PATHS.has(path)) throw new PublicError(404, "model_asset_not_found");
  const subject = url.searchParams.get("sub") ?? "";
  const signature = url.searchParams.get("sig") ?? "";
  const exp = Number(url.searchParams.get("exp") ?? "0");
  const nowSeconds = Math.floor(Date.now() / 1000);
  const maxTtl = positiveInt(env.MODEL_URL_TTL_SECONDS, 43_200);
  if (!subject || !Number.isSafeInteger(exp) || exp <= nowSeconds || exp > nowSeconds + maxTtl + 60) {
    throw new PublicError(401, "download_url_expired");
  }
  if (!await verifyDownload(subject, path, exp, signature, env.MODEL_TOKEN_SECRET)) {
    throw new PublicError(401, "invalid_download_signature");
  }

  if (!env.MODEL_BUCKET) throw new Error("private model bucket is not configured");
  const object = await env.MODEL_BUCKET.get(path, {
    onlyIf: request.headers,
    range: request.headers,
  });
  if (object === null) throw new PublicError(404, "model_asset_not_found");
  const headers = secureHeaders();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("accept-ranges", "bytes");
  headers.set("cache-control", "private, no-store");
  headers.set("content-disposition", `attachment; filename="${path.substring(path.lastIndexOf("/") + 1)}"`);
  if (!("body" in object)) return new Response(undefined, { status: 412, headers });
  const range = object.range as { offset?: number; length?: number } | undefined;
  let status = 200;
  if (request.headers.has("range") && range?.offset !== undefined && range.length !== undefined) {
    status = 206;
    headers.set("content-range", `bytes ${range.offset}-${range.offset + range.length - 1}/${object.size}`);
    headers.set("content-length", String(range.length));
  } else {
    headers.set("content-length", String(object.size));
  }
  return new Response(object.body, { status, headers });
}

async function requireAccessClaims(request: Request, secret: string): Promise<AccessClaims> {
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) throw new PublicError(401, "authorization_required");
  return verifyAccessToken(authorization.slice(7), secret);
}

async function signAccessToken(payload: AccessClaims, secret: string): Promise<string> {
  const unsigned = `${base64UrlJson({ alg: "HS256", typ: "JWT" })}.${base64UrlJson(payload)}`;
  const key = await hmacKey(secret, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

async function verifyAccessToken(token: string, secret: string): Promise<AccessClaims> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new PublicError(401, "invalid_authorization");
  const key = await hmacKey(secret, ["verify"]);
  const valid = await crypto.subtle.verify(
    "HMAC", key, base64UrlDecode(parts[2]).buffer as ArrayBuffer,
    encoder.encode(`${parts[0]}.${parts[1]}`),
  );
  if (!valid) throw new PublicError(401, "invalid_authorization");
  let claims: unknown;
  try {
    claims = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
  } catch {
    throw new PublicError(401, "invalid_authorization");
  }
  if (!isObject(claims) || typeof claims.sub !== "string" ||
      (claims.plan !== "FREE" && claims.plan !== "PRO") ||
      typeof claims.monthlyLimit !== "number" || typeof claims.dailyLimit !== "number" ||
      typeof claims.exp !== "number" || claims.exp <= Date.now() / 1000) {
    throw new PublicError(401, "authorization_expired");
  }
  return {
    sub: claims.sub,
    plan: claims.plan,
    monthlyLimit: claims.monthlyLimit,
    dailyLimit: claims.dailyLimit,
    exp: claims.exp,
    nonce: typeof claims.nonce === "string" ? claims.nonce : "",
  };
}

async function signDownload(subject: string, path: string, exp: number, secret: string): Promise<string> {
  const key = await hmacKey(secret, ["sign"]);
  const signature = await crypto.subtle.sign(
    "HMAC", key, encoder.encode(`${subject}\n${path}\n${exp}`),
  );
  return base64Url(new Uint8Array(signature));
}

async function verifyDownload(
  subject: string,
  path: string,
  exp: number,
  signature: string,
  secret: string,
): Promise<boolean> {
  let decoded: Uint8Array;
  try {
    decoded = base64UrlDecode(signature);
  } catch {
    return false;
  }
  const key = await hmacKey(secret, ["verify"]);
  return crypto.subtle.verify(
    "HMAC", key, decoded.buffer as ArrayBuffer, encoder.encode(`${subject}\n${path}\n${exp}`),
  );
}

async function opaqueSubject(value: string, secret: string): Promise<string> {
  const key = await hmacKey(secret, ["sign"]);
  const digest = await crypto.subtle.sign("HMAC", key, encoder.encode(value));
  return base64Url(new Uint8Array(digest));
}

async function googleAccessToken(env: Env): Promise<string> {
  if (googleTokenCache && googleTokenCache.expiresAtMillis > Date.now() + 60_000) {
    return googleTokenCache.value;
  }
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signRs256(
    { alg: "RS256", typ: "JWT" },
    { iss: env.GOOGLE_SERVICE_ACCOUNT_EMAIL, scope: GOOGLE_SCOPE,
      aud: GOOGLE_TOKEN_URL, iat: now, exp: now + 3600 },
    env.GOOGLE_PRIVATE_KEY,
  );
  const result = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!result.ok) throw new PublicError(502, "play_verification_unavailable");
  const token = await result.json<{ access_token?: unknown }>();
  if (typeof token.access_token !== "string") {
    throw new PublicError(502, "play_verification_unavailable");
  }
  googleTokenCache = { value: token.access_token, expiresAtMillis: Date.now() + 55 * 60_000 };
  return token.access_token;
}

async function signRs256(header: object, payload: object, privatePem: string): Promise<string> {
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(payload)}`;
  const keyBytes = pemBytes(privatePem.replace(/\\n/g, "\n"), "PRIVATE KEY");
  const key = await crypto.subtle.importKey(
    "pkcs8", keyBytes, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

async function readSmallJson(request: Request): Promise<Record<string, unknown>> {
  const length = Number(request.headers.get("content-length") ?? "0");
  if (length > 16_384) throw new PublicError(413, "request_too_large");
  let body: unknown;
  try {
    body = await request.json<unknown>();
  } catch {
    throw new PublicError(400, "invalid_json");
  }
  if (!isObject(body)) throw new PublicError(400, "invalid_request");
  return body;
}

function requireInstallationId(value: unknown): string {
  if (typeof value !== "string" || !/^[A-Za-z0-9._-]{20,128}$/.test(value)) {
    throw new PublicError(400, "invalid_installation_id");
  }
  return value;
}

function validRequestId(value: string | null): string | null {
  return value !== null && /^[A-Za-z0-9._-]{16,128}$/.test(value) ? value : null;
}

function planLimits(env: Env, plan: Plan): { monthly: number; daily: number } {
  return plan === "PRO"
    ? { monthly: positiveInt(env.PRO_MONTHLY_LIMIT, 100), daily: positiveInt(env.PRO_DAILY_LIMIT, 25) }
    : { monthly: positiveInt(env.FREE_MONTHLY_LIMIT, 5), daily: positiveInt(env.FREE_DAILY_LIMIT, 2) };
}

function positiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function utcMonthKey(now: number): string {
  return new Date(now).toISOString().slice(0, 7);
}

function utcDayKey(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

function nextUtcDay(now: number): number {
  const date = new Date(now);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() + 1);
}

function nextUtcMonth(now: number): number {
  const date = new Date(now);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 1);
}

async function hmacKey(secret: string, usages: Array<"sign" | "verify">): Promise<CryptoKey> {
  if (encoder.encode(secret).byteLength < 32) throw new Error("MODEL_TOKEN_SECRET is too short");
  return crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, usages,
  );
}

function requireCoreBindings(env: Env): void {
  if (!env.MODEL_TOKEN_SECRET || !env.DB) {
    throw new Error("freemium worker bindings are not configured");
  }
}

function requirePlaySecrets(env: Env): void {
  if (!env.GOOGLE_SERVICE_ACCOUNT_EMAIL || !env.GOOGLE_PRIVATE_KEY || !env.PRO_PRODUCT_ID) {
    throw new Error("Google Play verification secrets are not configured");
  }
}

function pemBytes(pem: string, label: string): ArrayBuffer {
  const raw = pem.replace(`-----BEGIN ${label}-----`, "")
    .replace(`-----END ${label}-----`, "").replace(/\s/g, "");
  const decoded = atob(raw);
  return Uint8Array.from(decoded, (c) => c.charCodeAt(0)).buffer;
}

function base64UrlJson(value: object): string {
  return base64Url(encoder.encode(JSON.stringify(value)));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64UrlDecode(value: string): Uint8Array {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const decoded = atob(base64);
  return Uint8Array.from(decoded, (c) => c.charCodeAt(0));
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function secureHeaders(): Headers {
  return new Headers({
    "x-content-type-options": "nosniff",
    "strict-transport-security": "max-age=31536000; includeSubDomains",
    "referrer-policy": "no-referrer",
  });
}

function json(value: object, status = 200): Response {
  const headers = secureHeaders();
  headers.set("content-type", "application/json");
  headers.set("cache-control", "no-store");
  return new Response(JSON.stringify(value), { status, headers });
}

function response(body: string, status: number, contentType: string): Response {
  const headers = secureHeaders();
  headers.set("content-type", contentType);
  headers.set("cache-control", "no-store");
  return new Response(body, { status, headers });
}

class PublicError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
  }
}
