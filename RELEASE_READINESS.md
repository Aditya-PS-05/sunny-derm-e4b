# Release readiness gates

Sunny is currently an engineering/research build. The former training-data
rights blocker is resolved for the PAD-trained model described below. Public
release remains blocked on clinical, real-device, operational, store, and
regulatory readiness.

The temporary GPU inference and contribution services are beta-only. Release
builds additionally fail unless device inference and disabled contribution are
selected through the environment.

Before inviting external beta participants, the temporary service must also use
TLS, authenticated and rate-limited endpoints, metadata-only gateway logs,
encrypted contribution storage, named operator access, a documented retention
period, and a working contribution-deletion process. A compatible gateway
template is provided in `ops/beta-gateway/`; storage and deletion remain backend
responsibilities.

Before enabling freemium in a public build, additionally verify that:

- `sunny_pro` has monthly and annual base plans and the reviewed country prices;
- Google Play purchase tokens are verified by the entitlement Worker;
- D1 quota migrations and the 62-day inactive-counter cleanup are active;
- the six checksum-pinned Sunny PAD SmolVLM 500M objects are present in the
  install-time Play Asset Delivery pack;
- the AAB build verifies every bundled size and hash before packaging;
- Free/Pro quota and offline-entitlement tests pass with Play license testers.

## 1. Data and model rights — resolved for `sunny-pad-smolvlm-500m-v1-gguf`

The shipping candidate was retrained exclusively on PAD-UFES-20 smartphone
images obtained from ISIC collection 406. The download record contains 2,298
images and reports `CC-BY` for every image; six unusable images were excluded,
leaving 2,015 training and 277 lesion-grouped validation records. The original
PAD-UFES-20 publication records ethics approval, patient consent, and a
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) license, which permits
commercial use and adaptation with attribution.

The base `HuggingFaceTB/SmolVLM-500M-Instruct` model is provided under Apache License 2.0.
Sunny's attribution, modification notice, complete Apache license, source DOI,
artifact hashes, split counts, and runtime provenance are recorded in:

- `licenses/THIRD_PARTY_NOTICES.txt`;
- `licenses/Apache-2.0.txt`;
- `exports/model_tiers/sunny-pad-smolvlm-500m-v1-gguf/manifest.json`.

These files ship in the install-time model pack. The
Gradle release gate verifies that this evidence is present. A future dataset,
base model, or model version requires a new rights review and manifest.

## 2. Clinical and real-world validation

The current 134-image evaluation measures agreement with generated references on
dermatoscopic images. It is not clinical evidence for phone-camera use. Before
setting `clinicalValidationComplete=true`:

- evaluate a preregistered, clinician-labelled, held-out phone-photo dataset;
- report performance and failure rates by skin tone, device, lighting, body area,
  lesion category, age, and sex where collection and consent permit;
- validate guardrail leakage, unreadable/non-skin rejection, latency, memory,
  thermal behavior, and crash rates on supported Android devices;
- complete human-factors testing for false reassurance and delayed-care risk;
- obtain a formal regulatory classification for every launch country.

For an approved release build only:

```bash
./gradlew bundleRelease \
  -PentitlementApiUrl=https://entitlements.example/ \
  -PprivacyContact=privacy@example.com \
  -PclinicalValidationComplete=true
```

Command-line flags are attestations, not substitutes for the evidence above.
The `release` build type is the public distribution mode: it hard-codes empty
inference/contribution endpoints and tokens, enables shrinking, and uses public
on-device/privacy copy. Debug builds retain the environment-controlled beta mode.
The build also refuses a release without the pinned vendored SmolVLM runtime
source, licensing evidence, HTTPS model and Play-entitlement services. CMake
retains the arm64 `libsunny_moe.so` filename for upgrade compatibility; full
PAD-model and notice-file digests are pinned in the signed catalog.
