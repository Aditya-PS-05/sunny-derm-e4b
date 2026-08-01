# Sunny — Android app

Native Android (Kotlin + Jetpack Compose) implementation of the **Sunny** skin
tracker, wrapping the fine-tuned Gemma 4 E4B on-device describer from this repo.
It follows the reference design (orange-on-cream, floating tab bar + capture
FAB) and enforces the product's hard safety/privacy contract.

> **Not a diagnostic tool.** Describes and tracks appearance only; every result
> and timeline screen shows the not-a-diagnosis disclaimer and routes concern to
> a clinician. See `../requirements.md` (S-01…S-07, P-01…P-05).

> **Research build only.** Public release is blocked until training-data rights
> and clinician-labelled phone-photo/skin-tone validation are complete. See
> `../RELEASE_READINESS.md`.

## Build & run

Prereqs: JDK 17, Android SDK (platform 35, build-tools 35). `local.properties`
points at the SDK (`sdk.dir=…`).

```bash
cd android
./gradlew assembleDebug              # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # with a device/emulator attached
```

Verified: `./gradlew assembleDebug` produces a ~20 MB debug APK.

Release and beta signing should come from the CI/OS secret store through
`SUNNY_SIGNING_STORE_FILE`, `SUNNY_SIGNING_STORE_PASSWORD`,
`SUNNY_SIGNING_KEY_ALIAS`, and `SUNNY_SIGNING_KEY_PASSWORD`. An ignored
`keystore.properties` is supported only as a local fallback and must be mode
`0600`; never commit either it or the keystore.

### Private GPU-server beta

The beta app exchanges its random installation ID with the HTTPS access broker
for short-lived cloud authorization and signed model URLs. No reusable gateway
credential is compiled into the APK or entered by the tester. This repository's
Debug/Beta default points to the private `sunny-gpu` broker, so a local build is:

```bash
./gradlew assembleBeta
```

Override `betaEntitlementApiUrl` (or `SUNNY_BETA_ENTITLEMENT_API_URL`) when the
GPU hostname changes. Public Release deliberately ignores that beta value and
requires a separately configured production `entitlementApiUrl`.

Cloud authorization is restored automatically at startup. Settings exposes only
the user-facing analysis-source choice; there is no credential-management UI.
Switching source resets the cached inference session so the next scan uses the
selected server or on-device engine.

For an external beta, deploy the TLS/auth/rate-limit gateway in
`../ops/beta-gateway/` and use its HTTPS domain and separate
inference/contribution tokens.

## The models: Sunny-MoE local, Pro server-only

The app talks through `inference/SunnyModel`. `SunnyMoeModel` is the sole
downloadable on-device engine. Consented, entitled Pro requests use
`RemoteSunnyModel`; the legacy 5.86 GB GGUF Pro pair is never downloaded. No
mock implementation is packaged, preventing plausible fake health output.

The prompt (`inference/Prompt`), greedy decoding, six-field parse
(`SchemaParser`), banned-word post-filter (`Guardrails`) and single re-run
(`SunnyDescriber`) enforce the boundary. Prompt is byte-identical to
`../docs/USING_THE_MODEL.md` §2.

### Model products

- **Sunny AI Cloud:** included server inference; no model download, with explicit
  photo-processing consent and runtime authorization. Free receives 5 analyses
  per UTC month (maximum 2/day); Pro receives 100/month (maximum 25/day).
- **Sunny MoE Pro:** 3.082 GB downloadable GGUF pack; Q4 language/experts and an
  FP16 corrected perception path that runs offline after installation.

Advanced comparison and new report generation route Free users to the Pro setup
screen. Existing scans and already-generated reports remain readable after Pro
expires.

The APK pins the byte count and full SHA-256 of the two GGUFs and manifest.

### Going live

**Sunny-MoE native bridge (`libsunny_moe.so`).** `inference/SunnyMoeBridge`
declares the JNI surface for the sparse SmolVLM runtime. CMake builds it from a
pinned, patched llama.cpp/mtmd source tree. It loads the mixed dense/MoE text
GGUF, the corrected FP16 vision GGUF, pools one expert route per sequence, and
pins those routes through decoding. Run `./scripts/vendor_sunny_moe_runtime.sh`
after a fresh clone before building.

**Model delivery.** `ModelProvider` resolves downloads from private internal
storage. Debug builds additionally accept the app external directory and
`/data/local/tmp/sunny/` for developer pushes.

- **Local (recommended here):** push the repo's weights onto a device once —
  ```bash
  ./scripts/push_weights_to_device.sh /path/to/sunny-moe-2.2b-v4-gguf
  ```
  Then **Settings › AI Model** shows "Model installed" and the real model runs.
- **Download:** Pro users receive short-lived per-file URLs from the entitlement
  worker's private model bucket and can download over Wi-Fi or mobile data.
  `inference/download/*` resumes partial files, verifies APK-pinned checksums,
  and atomically finalizes each file in `filesDir/models/`.

`ModelProvider.reset()` closes the previous native/remote session and
dynamically resolves Sunny-MoE or Sunny AI Cloud.

## Structure

```
inference/   Sunny-MoE JNI + AI Cloud, parser, guardrails and describer
inference/download/  resumable checksum-verified weight downloader + status manager
data/        Room (scans + observations), repository, image + settings stores
report/      encrypted PDF generation + stream-decrypting share provider
ui/theme     Sunny palette / type / theme
ui/nav       NavHost, floating bottom bar + capture FAB
ui/components body template, analysis card, chips, disclaimer, scaffold
ui/screens   Overview · Saved · ScanDetail · Settings · Capture · Camera ·
             Review · GenerateReport · Reports · ReportDetail · Lock
```

## Languages

Sunny follows the device language by default and also provides an immediate,
persistent selector in **Settings → Language & region**. English, Hindi,
Spanish, Italian, French, German, Brazilian Portuguese, Japanese, Korean,
Simplified Chinese and Traditional Chinese are included. Compose text goes through the shared
localization layer in `ui/i18n/`; Android 13+ can also discover the declared
locales through `res/xml/locales_config.xml`.

The extended locale catalogs are generated with
`scripts/generate_locale_catalogs.py`. Treat generated translations as a first
pass: the non-diagnostic safety disclaimer has a reviewed override for every
locale, and the complete product copy should receive native-speaker review
before a public store release.

## Privacy & safety enforced in-app (not just the model)

- Sunny-MoE inference stays on-device and requires verified Pro access outside
  local debug builds. AI Cloud requires runtime authorization and explicit
  per-device processing consent; contribution remains
  a separate timestamped opt-in (P-01…P-03).
- Persistent disclaimer on every analysis, timeline and report (S-02).
- Banned-word filter suppresses + re-runs on any disease/verdict term (S-03).
- Conservative exposure, contrast, and size checks recommend a retake before
  inference without interpreting skin or preventing an explicit override (F-08).
- Timeline comparison shows literal description-field differences only. It never
  scores pixels, declares stability, or recommends a care interval (F-14, S-05).
- Optional Face ID / device-credential lock gates the app locally.

## Known limitation carried from the model

Trained on **dermatoscopic** images; phone-photo and skin-tone performance is not
clinically established. The limitation is acknowledged during onboarding and
repeated beside results. See `../docs/performance.md`.

## Verification

`.github/workflows/android.yml` runs JVM tests, lint, APK assembly, and the API 34
instrumentation suite on pushes and pull requests.

`debug` is the private beta mode and can use the configured server endpoints.
`release` is the public mode: shared beta inference/contribution credentials are
never compiled in. AI Cloud uses short-lived server authorization and user
consent; Pro verification unlocks the Sunny-MoE download.

Release builds compile `libsunny_moe.so` for arm64 and require the vendored
runtime source, HTTPS entitlement/model gateway, and the legal/validation
attestations in `RELEASE_READINESS.md`.
