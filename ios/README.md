# Sunny for iOS

Native SwiftUI port of the Sunny Android application. It targets iOS 17 and
uses Apple frameworks rather than wrapping the Android UI.

## Implemented

- SwiftUI onboarding and three-tab navigation
- camera and PhotosPicker capture
- cloud inference using the existing OpenAI-compatible Sunny endpoint
- the identical six-field prompt, parser, retry policy and diagnosis guardrails
- encrypted Device Vault using AES-256-GCM, a device-bound Keychain key and
  `NSFileProtectionComplete`
- optional four-digit app PIN with native PBKDF2-HMAC-SHA256 and escalating
  lockout
- tracked areas, follow-up photos, history, editing and Pro comparison
- StoreKit 2 monthly/annual subscription handling and transaction verification
- 3.08 GB Sunny-MoE background download over Wi-Fi or cellular, throughput/ETA,
  storage preflight and streaming SHA-256 verification
- local notifications, PDF reports, privacy disclosures and explicit cloud/
  on-device selection
- schema/guardrail unit tests

## Generate and open the Xcode project

On a Mac with Xcode 16 or newer:

```bash
brew install xcodegen
cd ios
xcodegen generate
open Sunny.xcodeproj
```

Select the `Sunny` scheme, choose an iPhone running iOS 17 or newer, set your
development team and run. The checked-in `project.yml` is the source of truth;
do not manually maintain a generated `.xcodeproj`.

Run tests from Xcode or with:

```bash
xcodebuild \
  -project Sunny.xcodeproj \
  -scheme Sunny \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  test
```

## Configuration

The XcodeGen target defines these Info.plist-backed settings:

| Setting | Purpose |
|---|---|
| `SUNNY_INFERENCE_API_URL` | HTTPS root for `/v1/chat/completions` |
| `SUNNY_MODEL_BASE_URL` | HTTPS root containing `sunny-moe-2.2b-v4-gguf/` |
| `SUNNY_ENTITLEMENT_API_URL` | future App Store entitlement/download service |
| `SUNNY_PRO_MONTHLY_PRODUCT_ID` | StoreKit monthly product |
| `SUNNY_PRO_ANNUAL_PRODUCT_ID` | StoreKit annual product |

Debug and Beta currently point at the existing beta gateway. Release values are
deliberately blank so an App Store archive cannot silently ship a temporary beta
service. Replace them with production endpoints before release.

Create matching auto-renewable subscriptions in App Store Connect:

- `sunny.pro.monthly`
- `sunny.pro.annual`

Use a StoreKit configuration file locally or a sandbox tester to exercise the
purchase flow. StoreKit transactions are verified on-device. Before production,
the entitlement backend must also validate signed App Store transactions before
minting short-lived model-download URLs; do not rely solely on the local flag.

## On-device Sunny-MoE runtime

The Swift code, model catalog and downloader are complete, but the Android JNI
library cannot be linked into an iOS process. Build the shared llama.cpp/Sunny-MoE
core for `ios-arm64` and `ios-arm64_x86_64-simulator`, combine it as
`libsunny_moe.xcframework`, link it to the Sunny target, and add
`SUNNY_MOE_RUNTIME` to `SWIFT_ACTIVE_COMPILATION_CONDITIONS`.

The XCFramework must export this C ABI:

```c
void *sunny_moe_create(const char *text_model_path,
                       const char *projector_path);
char *sunny_moe_describe(void *handle,
                         const uint8_t *jpeg,
                         size_t jpeg_count,
                         const char *prompt);
void sunny_moe_free_string(char *value);
void sunny_moe_destroy(void *handle);
```

`NativeSunnyMoeEngine.swift` binds that ABI conditionally. Without the
XCFramework, the app remains honest: cloud inference works and the UI states
that the on-device runtime is unavailable instead of pretending local analysis
works.

The iOS catalog intentionally matches Android byte-for-byte:

| Asset | Bytes | SHA-256 |
|---|---:|---|
| `sunny-moe-text-Q4_K_M.gguf` | 2,210,067,936 | `7e7aa651986473c94988ac99978cd5c51a7bb7b8a1e4092cd41ed835c38fd28b` |
| `sunny-moe-mmproj-F16.gguf` | 872,300,704 | `c4149a795d2c4af070d94e2130e2e1026d96bb912bbac1595b6fd12d376b91f4` |
| `manifest.json` | 1,037 | `1a8ce0012e240b3e2e3c6b8a2eec376396986b4fbda858dabd8ccc8b686c13ad` |

## App Store release blockers

The iOS port does not change Sunny's research status. Do not submit or monetize
the current model weights until commercial training-data rights are cleared.
Do not claim diagnosis, cancer detection or clinical validation. A public launch
also requires phone-photo validation, skin-tone/device stratification, production
TLS services, retention/deletion controls, App Store server-side entitlement
verification, final privacy disclosures and a jurisdiction-specific regulatory
review. See `../RELEASE_READINESS.md`.

## Required finishing assets

Before archiving for App Store Connect, add a 1024×1024 app icon to
`Sunny/Resources/Assets.xcassets`, create screenshots for supported iPhone/iPad
sizes, replace beta endpoints, and configure the signing team and subscription
products.

