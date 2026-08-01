#!/usr/bin/env python3
"""Generate additional Sunny locale assets from the canonical English keys.

The checked-in JSON files are runtime inputs. Re-run this script only when the
English catalog changes, then review health, privacy and billing copy before
shipping. It intentionally preserves Sunny product and technical terms.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/kotlin/com/sunny/skin/ui/i18n/SunnyLocalization.kt"
ASSETS = ROOT / "app/src/main/assets/i18n"
RES = ROOT / "app/src/main/res"

TARGETS = {
    "it": "it",
    "fr": "fr",
    "de": "de",
    "pt-BR": "pt",
    "ja": "ja",
    "ko": "ko",
    "zh-Hans": "zh-CN",
    "zh-Hant": "zh-TW",
}

PROTECTED = {
    "Sunny AI Cloud": "SNYSUNNYCLOUDTOKEN",
    "Sunny MoE": "SNYSUNNYMOETOKEN",
    "Sunny": "SNYSUNNYTOKEN",
    "Wi-Fi": "SNYWIFITOKEN",
    "ABCDE": "SNYABCDETOKEN",
    "HTTPS": "SNYHTTPSTOKEN",
    "PDF": "SNYPDFTOKEN",
    "PIN": "SNYPINTOKEN",
    "Pro": "SNYPROTOKEN",
    "GB": "SNYGBTOKEN",
}

RESOURCE_KEYS = {
    "app_name": "Sunny",
    "nav_overview": "Overview",
    "nav_areas": "Areas",
    "nav_settings": "Settings",
    "action_add_photo": "Add photo",
    "capture_title": "Add photo",
    "capture_intro": "Use a clear, close-up photo of one area. Sunny will check lighting and detail before analysis.",
    "capture_take_photo": "Take a photo",
    "capture_take_photo_support": "Use live framing and lighting guidance",
    "capture_choose_library": "Choose from library",
    "capture_choose_library_support": "Use a photo already on this phone",
    "capture_library_error": "That photo could not be opened. Choose another image.",
    "capture_full_body": "Full-body photo check",
    "capture_full_body_support": "Capture head to toe with framing tips for each zone",
    "review_photo_title": "Review photo",
    "review_follow_up_title": "Review follow-up",
    "save_photo": "Save photo",
    "save_follow_up": "Save follow-up",
    "analysing_photo": "Analysing photo…",
    "resolve_analysis_to_save": "Resolve analysis to save",
}

SAFETY_DISCLAIMER = (
    "Sunny is a skin tracking tool only. It does not provide medical diagnoses or advice. "
    "Always consult a qualified healthcare professional for any skin concerns."
)

# Human-reviewed overrides for the highest-risk safety statement. Automated
# output is useful for breadth, but this copy must read naturally and preserve
# the non-diagnostic boundary in every shipped language.
MANUAL_OVERRIDES = {
    "it": {
        SAFETY_DISCLAIMER: "Sunny è uno strumento esclusivamente per monitorare la pelle. Non fornisce diagnosi o consigli medici. Per qualsiasi dubbio sulla pelle, consulta sempre un professionista sanitario qualificato.",
    },
    "fr": {
        SAFETY_DISCLAIMER: "Sunny est uniquement un outil de suivi de la peau. Il ne fournit ni diagnostic ni conseil médical. Consultez toujours un professionnel de santé qualifié pour toute préoccupation concernant votre peau.",
    },
    "de": {
        SAFETY_DISCLAIMER: "Sunny dient ausschließlich zur Dokumentation von Hautveränderungen. Die App stellt keine medizinischen Diagnosen und gibt keine medizinischen Ratschläge. Wenden Sie sich bei Hautproblemen immer an qualifiziertes medizinisches Fachpersonal.",
    },
    "pt-BR": {
        SAFETY_DISCLAIMER: "Sunny é apenas uma ferramenta para acompanhar alterações visíveis na pele. Não fornece diagnósticos nem orientações médicas. Consulte sempre um profissional de saúde qualificado se tiver qualquer preocupação com a pele.",
    },
    "ja": {
        SAFETY_DISCLAIMER: "Sunnyは、皮膚の見た目の変化を記録するためのツールです。医学的な診断や助言を行うものではありません。皮膚について心配なことがある場合は、必ず資格を持つ医療専門家に相談してください。",
    },
    "ko": {
        SAFETY_DISCLAIMER: "Sunny는 피부의 눈에 보이는 변화를 기록하기 위한 도구일 뿐입니다. 의학적 진단이나 조언을 제공하지 않습니다. 피부에 우려되는 점이 있다면 반드시 자격을 갖춘 의료 전문가와 상담하세요.",
    },
    "zh-Hans": {
        SAFETY_DISCLAIMER: "Sunny仅用于记录皮肤外观变化，不提供医疗诊断或建议。如对皮肤有任何疑虑，请务必咨询具备资质的医疗专业人员。",
    },
    "zh-Hant": {
        SAFETY_DISCLAIMER: "Sunny僅用於記錄皮膚外觀變化，不提供醫療診斷或建議。如對皮膚有任何疑慮，請務必諮詢具備資格的醫療專業人員。",
    },
}


def english_keys() -> list[str]:
    source = SOURCE.read_text(encoding="utf-8")
    block = source.split("private val hindi = mapOf(", 1)[1].split(
        "\n    )\n\n    private val spanish", 1
    )[0]
    keys = re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s+to\s+"', block, re.MULTILINE)
    if len(keys) < 250 or len(keys) != len(set(keys)):
        raise RuntimeError("Could not extract a unique canonical English catalog")
    return keys


def protect(text: str) -> str:
    for source, token in PROTECTED.items():
        text = text.replace(source, token)
    return text


def restore(text: str) -> str:
    for source, token in PROTECTED.items():
        text = re.sub(re.escape(token), source, text, flags=re.IGNORECASE)
    return text


def google_translate(lines: list[str], target: str) -> list[str]:
    protected = [protect(line) for line in lines]
    payload = urllib.parse.urlencode({
        "client": "gtx",
        "sl": "en",
        "tl": target,
        "dt": "t",
        "q": "\n".join(protected),
    }).encode()
    request = urllib.request.Request(
        "https://translate.googleapis.com/translate_a/single",
        data=payload,
        headers={"User-Agent": "Sunny-locale-builder/1.0"},
    )
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                result = json.load(response)
            translated = "".join(segment[0] for segment in result[0]).splitlines()
            if len(translated) != len(lines):
                raise RuntimeError(
                    f"Translation line mismatch: expected {len(lines)}, got {len(translated)}"
                )
            return [restore(value.strip()) for value in translated]
        except Exception:
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError("unreachable")


def translate_catalog(keys: list[str], target: str) -> dict[str, str]:
    result: dict[str, str] = {}
    chunk: list[str] = []
    size = 0
    for key in keys:
        if chunk and (len(chunk) >= 35 or size + len(key) > 5_500):
            values = google_translate(chunk, target)
            result.update(zip(chunk, values))
            chunk, size = [], 0
        chunk.append(key)
        size += len(key) + 1
    if chunk:
        result.update(zip(chunk, google_translate(chunk, target)))
    for brand in ("Sunny", "SUNNY", "PRO"):
        if brand in result:
            result[brand] = brand
    return result


def write_json(tag: str, catalog: dict[str, str]) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / f"{tag}.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def write_android_resources(tag: str, catalog: dict[str, str]) -> None:
    qualifier = {
        "pt-BR": "pt-rBR",
        "zh-Hans": "b+zh+Hans",
        "zh-Hant": "b+zh+Hant",
    }.get(tag, tag)
    values = RES / f"values-{qualifier}"
    values.mkdir(parents=True, exist_ok=True)
    lines = ["<resources>"]
    for resource, english in RESOURCE_KEYS.items():
        value = catalog.get(english, english)
        escaped = html.escape(value, quote=False).replace("'", "\\'")
        lines.append(f'    <string name="{resource}">{escaped}</string>')
    lines.append("</resources>")
    (values / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--locales", nargs="*", choices=TARGETS, default=list(TARGETS))
    parser.add_argument(
        "--reuse",
        action="store_true",
        help="Reuse existing JSON catalogs and only regenerate Android resources.",
    )
    args = parser.parse_args()
    keys = list(dict.fromkeys(english_keys() + list(RESOURCE_KEYS.values())))
    for tag in args.locales:
        existing = ASSETS / f"{tag}.json"
        if args.reuse and existing.is_file():
            print(f"Reusing {tag}", flush=True)
            catalog = json.loads(existing.read_text(encoding="utf-8"))
        else:
            print(f"Translating {len(keys)} strings -> {tag}", flush=True)
            catalog = translate_catalog(keys, TARGETS[tag])
            catalog.update(MANUAL_OVERRIDES.get(tag, {}))
        if set(catalog) != set(keys) or any(not value for value in catalog.values()):
            raise RuntimeError(f"Incomplete catalog for {tag}")
        write_json(tag, catalog)
        write_android_resources(tag, catalog)


if __name__ == "__main__":
    main()
