# Sunny — Android app

Native Android (Kotlin + Jetpack Compose) implementation of the **Sunny** skin
tracker, wrapping the PAD-trained SmolVLM 500M describer from this repo.
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

## The models: PAD-trained Sunny Offline and Sunny AI Cloud

The app talks through `inference/SunnyModel`. `SunnyMoeModel` keeps its internal
name for upgrade compatibility and is the bundled on-device engine.
Consented cloud requests use `RemoteSunnyModel`. Both routes use the current
PAD-UFES-20-trained SmolVLM 500M family. No
mock implementation is packaged, preventing plausible fake health output.

The prompt (`inference/Prompt`), greedy decoding, six-field parse
(`SchemaParser`), controlled presentation vocabulary (`AnalysisVocabulary`),
banned-word post-filter (`Guardrails`) and single re-run (`SunnyDescriber`)
enforce the boundary. Conflicting observable terms resolve to `unclear`, and the
plain-language summary is generated from the normalized fields. Prompt is byte-identical to
`../docs/USING_THE_MODEL.md` §2.

### Model products

- **Sunny AI Cloud:** included server inference; no model download, with explicit
  photo-processing consent and runtime authorization. Free receives 5 analyses
  per UTC month (maximum 2/day); Pro receives 100/month (maximum 25/day).
- **Sunny Offline Pro:** 393 MiB install-time Play Asset Delivery pack with a
  Q8_0 SmolVLM 500M model, fixed-256 px F16 vision projector, and bounded GBNF grammar.

Advanced comparison and new report generation route Free users to the Pro setup
screen. Existing scans and already-generated reports remain readable after Pro
expires.

The APK pins the byte count and full SHA-256 of both GGUFs, the grammar, model manifest,
the PAD-UFES-20 attribution notice, and the complete Apache 2.0 license.

### Going live

**Stable native bridge (`libsunny_moe.so`).** `inference/SunnyMoeBridge`
retains the existing JNI surface while loading SmolVLM 500M. CMake builds it
from the pinned llama.cpp/mtmd source tree. It applies the exact image-first
SmolVLM template and grammar-constrained multimodal decoding. The Android mtmd
build uses one 256 px global vision view; the stock desktop processor expands a
photo into thirteen vision passes and is too slow for mobile CPUs.
Run `./scripts/vendor_sunny_moe_runtime.sh`
after a fresh clone before building.

**Model delivery.** Google Play installs `sunny_model_pack` with the app. On
first launch Sunny verifies and copies it into private runtime storage because
llama.cpp needs ordinary filesystem paths. Debug builds additionally accept the
app external directory and `/data/local/tmp/sunny/` for developer pushes.

- **Local (recommended here):** push the repo's weights onto a device once —
  ```bash
  ./scripts/push_weights_to_device.sh /path/to/sunny-pad-smolvlm-500m-mobile256-v2-gguf
  ```
  Then **Settings › AI Model** shows "Model installed" and the real model runs.
- **Google Play:** run `./scripts/stage_bundled_model.sh`, then build/upload an
  AAB with `bundleBeta` or `bundleRelease`. No foreground model download is used.

`ModelProvider.reset()` closes the previous native/remote session and
dynamically resolves Sunny Offline or Sunny AI Cloud.

## Structure

```
inference/   SmolVLM JNI + AI Cloud, grammar, parser, guardrails and describer
inference/download/  install-time model verifier/materializer + legacy migration code
data/        Room (scans + observations), repository, image + settings stores
report/      encrypted PDF generation + stream-decrypting share provider
ui/theme     Sunny palette / type / theme
ui/nav       NavHost, floating bottom bar + capture FAB
ui/components body template, analysis card, chips, disclaimer, scaffold
ui/screens   Overview · Saved · ScanDetail · Settings · Capture · Camera ·
             Review · GenerateReport · Reports · ReportDetail · Lock
```

## Languages

Sunny follows the device language by default and also provides a persistent
selector in **Settings → Language & region**. English source copy is translated
on-device with Google ML Kit. Sunny downloads the selected language model before
committing the switch, caches translations in memory, and uses the same path for
normalized analysis descriptions. The English analysis remains canonical in the
encrypted database so comparison and safety rules do not vary by locale.

ML Kit translations are a convenience layer, not human-reviewed medical copy.
The complete product and safety copy still requires native-speaker review before
a public store release.

## Privacy & safety enforced in-app (not just the model)

- Sunny Offline is the default placement and never silently falls back to Cloud.
  It requires verified Pro access outside local debug builds. AI Cloud requires
  runtime authorization and explicit timestamped per-device processing consent; contribution remains
  a separate timestamped opt-in (P-01…P-03).
- Persistent disclaimer on every analysis, timeline and report (S-02).
- Banned-word filter suppresses + re-runs on any disease/verdict term (S-03).
- Conservative exposure, contrast, and size checks recommend a retake before
  inference without interpreting skin or preventing an explicit override (F-08).
- Timeline comparison shows controlled Previous → Current description values only.
  It never scores pixels, declares stability, or recommends a care interval (F-14, S-05).
- Optional Face ID / device-credential lock gates the app locally.

## Known limitation carried from the model

PAD-UFES-20 provides smartphone clinical images, but independent patient-level,
skin-tone, device, lighting, and real-world performance are not clinically
established. The limitation is acknowledged during onboarding and repeated
beside results. See `../RELEASE_READINESS.md`.

## Verification

`.github/workflows/android.yml` runs JVM tests, lint, APK assembly, and the API 34
instrumentation suite on pushes and pull requests.

`debug` is the private beta mode and can use the configured server endpoints.
`release` is the public mode: shared beta inference/contribution credentials are
never compiled in. AI Cloud uses short-lived server authorization and user
consent; Pro verification unlocks use of the bundled Sunny Offline model.

Release builds compile `libsunny_moe.so` for arm64 and require the vendored
runtime source, HTTPS entitlement/model gateway, and the legal/validation
attestations in `RELEASE_READINESS.md`.
