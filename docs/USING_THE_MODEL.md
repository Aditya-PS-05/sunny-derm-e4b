# Using the Sunny PAD SmolVLM 500M model

> **Current architecture (August 2026):** Sunny AI Cloud and the downloadable
> phone runtime use the same PAD-UFES-20-trained SmolVLM 500M family. The
> checksum-pinned pack is `sunny-pad-smolvlm-500m-v1-gguf` and totals
> 412,049,555 bytes (about 393 MiB).

Sunny describes visible skin features for longitudinal tracking. It is not a
diagnostic model and must never be presented as one.

## Prompt contract

Send exactly one image first and this text second in one user message:

```text
You are a dermatology description assistant. Look at this skin lesion photo and describe what you see. Do NOT diagnose or name a disease. Report only observable features in this exact format:
Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>
Colour: <colours present>
Symmetry: <symmetric / asymmetric>
Borders: <smooth / irregular / well- or poorly-defined>
Texture: <smooth / rough / raised / scaly>
Summary: <one plain-language sentence describing the lesion's appearance>
Safety: This is a visual description only, not a diagnosis — see a clinician for any concern.
```

Expected output:

```text
Lesion Type: pigmented lesion
Colour: light and dark brown
Symmetry: roughly symmetric
Borders: somewhat irregular borders
Texture: rough or structurally varied surface
Summary: A light and dark brown pigmented lesion with a roughly symmetric shape and somewhat irregular borders.
Safety: This is a visual description only, not a diagnosis — see a clinician for any concern.
```

## Runtime contract

| Setting | Value |
|---|---|
| Temperature | `0` (greedy) |
| Maximum new tokens | `256` |
| Grammar | `derm.gbnf`, root rule `root` |
| Message order | image first, prompt second |
| Images per request | exactly one |

The grammar bounds every field, requires all six fields in order, and forces
the exact non-diagnosis safety line. Keeping that line separate makes the
grammar unambiguous even when the model writes a long Summary. The app still
parses all fields and applies its
diagnosis/verdict banned-word guardrail before displaying an answer.

The Android runtime uses a fixed 256 px global image view with SmolVLM's bucketed
position embeddings. The stock
desktop processor expands a photo to a 2048 px tiled canvas and thirteen vision
passes, which is not suitable for a phone CPU.

## Artifacts

| Runtime | Files |
|---|---|
| Android pack | `sunny-pad-smolvlm-500m-Q8_0.gguf`, `sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf`, `derm.gbnf`, notices, Apache license, manifest |
| iOS pack | `sunny-pad-smolvlm-500m-Q4_K_M.gguf`, `sunny-pad-smolvlm-500m-mmproj-Q8_0.gguf`, `derm.gbnf`, notices, Apache license, manifest |
| GPU source paths | `~/models/smolvlm-derm-pad-Q4_K_M.gguf`, `~/models/mmproj-smolvlm-derm-pad-Q8_0.gguf` |
| Fine-tune | `~/models/smolvlm-derm-pad-lora`, merged checkpoint `~/models/smolvlm-derm-pad-merged` |

The complete checksums, training provenance, license references, and runtime
commits are in
`exports/model_tiers/sunny-pad-smolvlm-500m-v1-gguf/manifest.json`.

## llama.cpp server

```bash
llama-server \
  -m smolvlm-derm-pad-Q4_K_M.gguf \
  --mmproj mmproj-smolvlm-derm-pad-Q8_0.gguf \
  --grammar-file derm.gbnf \
  -ngl 99 -c 4096 --jinja --reasoning-format none
```

Use the OpenAI-compatible `/v1/chat/completions` endpoint with the prompt and
message ordering above. Production applies the grammar at the server, so phone
clients do not send or control the grammar.

## App guardrails

1. Require all six fields and the exact disclaimer.
2. Suppress outputs containing a disease name, diagnosis, risk score, or
   benign/malignant verdict.
3. Keep the persistent “not a diagnostic tool” UI disclosure independent of
   model output.
4. Reject unusable, blurred, or unrelated images before inference.
5. Never silently upload a photo; cloud analysis requires explicit consent.
6. Do not build multi-image or conversational diagnosis flows on this model.

## Validation status

The grammar benchmark covered all 277 lesion-grouped PAD validation images and
reported 100% six-field format compliance and 100% safety compliance. A clean
live request through the public Sunny broker completed with the grammar and a
normal stop reason. These are model/service checks, not clinical validation.
Independent clinician-labelled phone-photo, skin-tone, device, thermal, memory,
and human-factors validation remain release requirements.
