# Sunny for iOS

Native SwiftUI port of the Sunny Android application. It targets iOS 17 and
uses Apple frameworks rather than wrapping the Android UI.

## Implemented

- SwiftUI onboarding and three-tab navigation
- camera and PhotosPicker capture
- cloud inference using the existing OpenAI-compatible Sunny endpoint
- the identical six-field prompt, controlled vocabulary, contradiction handling,
  templated summary, retry policy and diagnosis guardrails
- encrypted Device Vault using AES-256-GCM, a device-bound Keychain key and
  `NSFileProtectionComplete`
- optional four-digit app PIN with native PBKDF2-HMAC-SHA256 and escalating
  lockout
- tracked areas, follow-up photos, history, editing and explicit Previous → Current comparison
- StoreKit 2 monthly/annual subscription handling and transaction verification
- 393 MiB Sunny Offline background download over Wi-Fi or cellular, throughput/ETA,
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
| `SUNNY_MODEL_BASE_URL` | HTTPS root containing `sunny-pad-smolvlm-500m-v1-gguf/` |
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

## On-device Sunny Offline runtime

The Swift code, model catalog and downloader are complete, but the Android JNI
library cannot be linked into an iOS process. Build the shared llama.cpp/SmolVLM
core for `ios-arm64` and `ios-arm64_x86_64-simulator`, combine it as
`libsunny_moe.xcframework`, link it to the Sunny target, and add
`SUNNY_MOE_RUNTIME` to `SWIFT_ACTIVE_COMPILATION_CONDITIONS`.

The XCFramework must export this C ABI:

```c
void *sunny_moe_create(const char *text_model_path,
                       const char *projector_path,
                       const char *grammar_path);
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
| `sunny-pad-smolvlm-500m-Q4_K_M.gguf` | 303,250,432 | `fb64c371d4044c7556966bf5dbf12d8aa5fb3b628a8be9ba0841189dbffe4d64` |
| `sunny-pad-smolvlm-500m-mmproj-Q8_0.gguf` | 108,782,144 | `ac585ec2ee776eab23c4502f1a71d9d90a4057752057c05ed2487d47dc31798f` |
| `derm.gbnf` | 304 | `bb7668aafa0c3b87cb5b10ecf9bd01037c6ad3ed8fdf01bc53e7b267538c2f09` |
| `THIRD_PARTY_NOTICES.txt` | 2,088 | `ded7a876f2e6501c7a263e3fafaef98684165ae325eac5c81fcb8b5aeb352866` |
| `Apache-2.0.txt` | 11,357 | `84829002701217076a39a84808ec52e45088ddbf9f6623896e5550becd8e09be` |
| `manifest.json` | 3,230 | `972c9d0544bddebed21506616f4bbce56ef18c78e4b7681fa870e297f5111712` |

## App Store release blockers

The PAD-UFES-20/SmolVLM licensing evidence is documented and accompanies the
pack. Do not claim diagnosis, cancer detection or clinical validation. A public launch
also requires phone-photo validation, skin-tone/device stratification, production
TLS services, retention/deletion controls, App Store server-side entitlement
verification, final privacy disclosures and a jurisdiction-specific regulatory
review. See `../RELEASE_READINESS.md`.

## Required finishing assets

Before archiving for App Store Connect, add a 1024×1024 app icon to
`Sunny/Resources/Assets.xcassets`, create screenshots for supported iPhone/iPad
sizes, replace beta endpoints, and configure the signing team and subscription
products.
