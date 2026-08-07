# Sunny visual skin-description model — SmolVLM 500M

Sunny fine-tunes **HuggingFaceTB/SmolVLM-500M-Instruct** on PAD-UFES-20 phone
images to produce structured visual skin descriptions for tracking over time.
The same model pair runs on the Sunny GPU service and in the Android
install-time model pack. The iOS catalog remains aligned byte-for-byte.

> **Not a diagnostic tool.** Sunny describes visible appearance. It must never
> assert a diagnosis, benign/malignant verdict, or risk score, and the app must
> direct medical concerns to a qualified clinician.

## Current release candidate

- Text model: `smolvlm-derm-pad-Q4_K_M.gguf` — 303,250,432 bytes
- Vision projector: `mmproj-smolvlm-derm-pad-Q8_0.gguf` — 108,782,144 bytes
- Output: `Lesion Type · Colour · Symmetry · Borders · Texture · Summary`
- Decoding: greedy, 160-token cap, bounded `derm.gbnf` grammar
- Phone pack: `sunny-pad-smolvlm-500m-v1-gguf` — 412,049,555 bytes
- Base model license: Apache License 2.0
- Dataset: PAD-UFES-20, CC BY 4.0

The full artifact hashes and modification notice are in
[`manifest.json`](exports/model_tiers/sunny-pad-smolvlm-500m-v1-gguf/manifest.json)
and [`THIRD_PARTY_NOTICES.txt`](licenses/THIRD_PARTY_NOTICES.txt).

## Data and training

- Source: [PAD-UFES-20](https://doi.org/10.17632/zr7vgbcyr2.1), also available
  as ISIC collection 406.
- Source images: 2,298 smartphone clinical images.
- Accepted: 2,292 after six feature-extraction failures.
- Split: 2,015 training and 277 lesion-grouped validation images.
- Fine-tune: three-epoch LoRA (`r=16`, `alpha=16`, batch 4, gradient
  accumulation 4, learning rate `2e-4`, seed 42).
- Export: merged checkpoint, Q4_K_M language model, Q8_0 vision projector.

Diagnostic source labels are used only for grouping/balancing. Sunny’s output
contains observable features, not a category prediction.

## Runtime

The GPU service runs llama.cpp/mtmd on `ssh sunny-gpu` and exposes an
OpenAI-compatible endpoint through the Sunny access broker. Android uses the
same llama.cpp/mtmd code behind the stable `libsunny_moe.so` JNI boundary. The
iOS downloader/catalog is aligned byte-for-byte; its native XCFramework remains
a separate release integration step.

Start with:

- [`docs/USING_THE_MODEL.md`](docs/USING_THE_MODEL.md) — prompt, grammar, and
  decoding contract.
- [`docs/model_tier_delivery.md`](docs/model_tier_delivery.md) — Android
  install-time delivery and runtime boundary.
- [`exports/MODELS.md`](exports/MODELS.md) — artifact locations and hashes.
- [`RELEASE_READINESS.md`](RELEASE_READINESS.md) — remaining validation and
  launch gates.

## Validation status

The grammar benchmark over all 277 validation images reported 100% six-field
format compliance and 100% safety compliance. A live request through the public
Sunny broker also completed with all six fields and the exact non-diagnosis
ending.

This is not clinical validation. Independent clinician-labelled phone-photo,
skin-tone/device, peak-memory, thermal, battery, accessibility, human-factors,
and regulatory validation remain required before medical claims or a broad
public release.

## Security and privacy

On-device analysis is the default and Sunny never falls back to Cloud silently.
Cloud analysis requires explicit, timestamped user consent because the selected
photo leaves the device. On Android, Google Play installs Sunny Offline with the app; Sunny
then verifies every object by app-pinned size and SHA-256 before copying it to
private runtime storage. No second network model download is required.
