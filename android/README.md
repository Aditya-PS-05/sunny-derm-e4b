# Sunny — Android app

Native Android (Kotlin + Jetpack Compose) implementation of the **Sunny** skin
tracker, wrapping the fine-tuned Gemma 4 E4B on-device describer from this repo.
It follows the reference design (orange-on-cream, floating tab bar + capture
FAB) and enforces the product's hard safety/privacy contract.

> **Not a diagnostic tool.** Describes and tracks appearance only; every result
> and timeline screen shows the not-a-diagnosis disclaimer and routes concern to
> a clinician. See `../requirements.md` (S-01…S-07, P-01…P-05).

## Build & run

Prereqs: JDK 17, Android SDK (platform 35, build-tools 35). `local.properties`
points at the SDK (`sdk.dir=…`).

```bash
cd android
./gradlew assembleDebug              # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # with a device/emulator attached
```

Verified: `./gradlew assembleDebug` produces a ~20 MB debug APK.

## The model: mock by default, real via native bridge + download

The app talks to the model through `inference/SunnyModel` and picks an
implementation at runtime (`inference/ModelProvider`):

- **`MockSunnyModel`** (default) — emits the exact six-field schema using the
  training-time controlled vocabulary, deterministically per image. Lets the
  whole UI/parser/guardrail/storage/report pipeline run and demo on any device
  **without** the 6 GB weights. Settings shows "Sunny-Gemma4-E4B (demo)".
- **`LlamaCppSunnyModel`** (real, llama.cpp/mtmd — the verified path) — used
  automatically once BOTH the native lib and the weights are present.

The prompt (`inference/Prompt`), greedy decoding, six-field parse
(`SchemaParser`), banned-word post-filter (`Guardrails`) and single re-run
(`SunnyDescriber`) are all runtime-independent, so both models enforce the same
boundary. Prompt is byte-identical to `../docs/USING_THE_MODEL.md` §2.

### Going live — two steps, both built

**1. Native bridge (`libsunny_llama.so`).** `inference/LlamaBridge` is backed by
`app/src/main/cpp/sunny_llama.cpp` (JNI over llama.cpp + `mtmd`) and
`CMakeLists.txt`. The native build is gated so the default build never needs the
toolchain:

```bash
./scripts/vendor_llama.sh                 # clone pinned llama.cpp into cpp/
./gradlew assembleDebug -PwithLlama       # builds ggml+llama+mtmd+bridge (arm64)
```

Without `-PwithLlama` the `.so` is absent, `LlamaBridge.ensureLibrary()` returns
false, and `ModelProvider` stays on the mock.

**2. Weights.** The GGUFs already exist locally in this repo
(`../exports/model_on_host/`), so **no download is required**. `ModelProvider`
resolves them at runtime from the first of these on-device folders that has both
files: `filesDir/models/`, the app's external files dir, or `/data/local/tmp/sunny/`.

- **Local (recommended here):** push the repo's weights onto a device once —
  ```bash
  ./scripts/push_weights_to_device.sh      # adb push -> app external files dir
  ```
  Then **Settings › AI Model** shows "Model installed" and the real model runs.
- **Download (optional, for distribution):** `inference/download/*` also
  implements a resumable, checksum-verified download into `filesDir/models/`
  (Wi-Fi-gated, progress UI). Set `ModelSource.baseUrl` to a published URL to
  enable it. INTERNET is used for **only** this optional download; the core
  describe/track loop stays offline.

Either way `ModelProvider.reset()` upgrades the live session mock → real once the
files appear. To shrink the ~6 GB footprint, re-quantize on the GPU host (int8
mmproj + Q4_0/Q3 LM → ~4 GB) and drop the smaller files in the same folder — no
app code changes.

## Structure

```
inference/   SunnyModel interface, Mock + llama.cpp impls, Prompt, parser, guardrails, describer
inference/download/  resumable checksum-verified weight downloader + status manager
cpp/         sunny_llama.cpp (JNI/mtmd bridge) + CMakeLists (built with -PwithLlama)
data/        Room (scans + observations), repository, image + settings stores
report/      on-device PDF generation + store (share via FileProvider)
ui/theme     Sunny palette / type / theme
ui/nav       NavHost, floating bottom bar + capture FAB
ui/components body template, analysis card, chips, disclaimer, scaffold
ui/screens   Overview · Saved · ScanDetail · Settings · Capture · Camera ·
             Review · GenerateReport · Reports · ReportDetail · Lock
```

## Privacy & safety enforced in-app (not just the model)

- No `INTERNET` permission for the core loop; photos/scans stay in app-private
  storage; backup/transfer excluded (P-01…P-03).
- Persistent disclaimer on every analysis, timeline and report (S-02).
- Banned-word filter suppresses + re-runs on any disease/verdict term (S-03).
- Change detection is shown as routing ("consider seeing a clinician"), never a
  verdict or risk score (F-14, S-05).
- Optional Face ID / device-credential lock gates the app locally.

## Known limitation carried from the model

Trained on **dermatoscopic** images; on raw phone photos accuracy drops. The
capture flow pushes toward close-up, well-lit, filled-frame shots, and the
"couldn't read this image" state guides retries. See `../docs/performance.md`.
```
