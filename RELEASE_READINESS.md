# Release readiness gates

Sunny is currently an engineering/research build. Public release, monetization,
and model-weight publication are blocked until both gates below have written
evidence and an accountable reviewer has approved them.

The temporary GPU inference and contribution services are beta-only. Release
builds additionally fail unless device inference and disabled contribution are
selected through the environment.

Before inviting external beta participants, the temporary service must also use
TLS, authenticated and rate-limited endpoints, metadata-only gateway logs,
encrypted contribution storage, named operator access, a documented retention
period, and a working contribution-deletion process. A compatible gateway
template is provided in `ops/beta-gateway/`; storage and deletion remain backend
responsibilities.

## 1. Data and model rights

The current fine-tune used `marmal88/skin_cancer`, a HAM10000 mirror whose card
does not grant a license, describes academic use, and says the uploader owns no
rights to the images. Before setting `dataRightsCleared=true`:

- obtain written commercial rights covering training, derived weights, and
  distribution, or retrain on a dataset with compatible documented rights;
- record all source datasets, versions, licenses, attribution, and consent terms;
- have qualified counsel approve the intended business and distribution model.

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
  -PwithLlama \
  -PmodelBaseUrl=https://cdn.example/models/ \
  -PprivacyContact=privacy@example.com \
  -PdataRightsCleared=true \
  -PclinicalValidationComplete=true
```

Command-line flags are attestations, not substitutes for the evidence above.
The `release` build type is the public distribution mode: it hard-codes empty
inference/contribution endpoints and tokens, enables shrinking, and uses public
on-device/privacy copy. Debug builds retain the environment-controlled beta mode.
The build also refuses a release without the native llama runtime, an HTTPS model
source, and full model digests. The checked-in digests were calculated from the
canonical local GGUF artifacts; override them only when those artifacts change.
