# Release readiness gates

Sunny is currently an engineering/research build. Public release, monetization,
and model-weight publication are blocked until both gates below have written
evidence and an accountable reviewer has approved them.

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
  -PdataRightsCleared=true \
  -PclinicalValidationComplete=true
```

Command-line flags are attestations, not substitutes for the evidence above.
