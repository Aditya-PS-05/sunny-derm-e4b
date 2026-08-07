# Archived Android experiment — Gemma 4 E4B

> **Superseded (August 2026).** This is retained only to reproduce the legacy
> export experiment. The Sunny Android app must not bundle or download this
> approximately 5.86–6 GB Pro model, LiteRT conversion, or ONNX tiers. Android
> now downloads the 393 MiB PAD-trained SmolVLM 500M pack through the current
> llama.cpp JNI bridge, and Sunny AI Cloud runs the same model family. The contract is
> `docs/model_tier_delivery.md`.

Historical notes for the former on-device Pro prototype follow. They are not
instructions for the current app.

---

## 1. Runtime choice

| Runtime | Status (2026) | Use when |
|---|---|---|
| **LiteRT-LM (Kotlin API)** | **Recommended.** Actively developed; supported Android path. | Default. Gemma 3n/4 E-series ship in `.litertlm`; vision supported. |
| MediaPipe LLM Inference API | Maintenance-only; Google recommends migrating off it. | Only if you already have a MediaPipe integration. |
| llama.cpp (GGUF) | Community. | You want a single cross-platform C++ engine and control the build. |

This comparison was made for the archived prototype; none of these runtimes is
selected by the current Android application.

## 2. Why we ship a MERGED model, not a runtime LoRA

The LLM Inference API's on-device LoRA is **attention-layers-only and GPU-only**.
Our QLoRA adapter also targets the MLP projections (`gate/up/down_proj`), so
shipping it as a runtime LoRA would silently drop those deltas. Instead
`export_android.py merge` folds the full adapter (attention **and** MLP) into
the base weights, and we quantize the merged model. This captures the complete
fine-tune at the cost of shipping full (quantized) weights rather than a small
adapter — the right trade for correctness.

## 3. Export sequence (on the GPU host after training)

```bash
# 1. Merge adapter -> fp16 base (full fine-tune, attn + MLP)
python scripts/export_android.py merge \
    --base google/gemma-4-E4B-it \
    --adapter ~/derm/out/e4b-derm-lora \
    --out ~/derm/out/e4b-derm-merged

# 2a. LiteRT-LM path (recommended)
pip install ai-edge-torch-nightly ai-edge-litert
#   Use the ai-edge-torch Gemma generative recipe to re-author the merged
#   checkpoint, convert() + quantize (int4/int8) to .tflite, then bundle with
#   the tokenizer into a .litertlm Task Bundle.
python scripts/export_android.py litert \
    --merged ~/derm/out/e4b-derm-merged --out ~/derm/out/e4b-derm.litertlm

# 2b. GGUF path (llama.cpp) — BUILT & VERIFIED for this model
#   Language model -> Q4_K_M (5.0 GB):
python llama.cpp/convert_hf_to_gguf.py ~/derm/out/e4b-derm-merged \
    --outfile ~/derm/out/e4b-derm-f16.gguf --outtype f16
llama.cpp/build/bin/llama-quantize ~/derm/out/e4b-derm-f16.gguf \
    ~/derm/out/e4b-derm-Q4_K_M.gguf Q4_K_M
#   Vision projector (mmproj, 990 MB) — REQUIRED for image input:
python llama.cpp/convert_hf_to_gguf.py ~/derm/out/e4b-derm-merged \
    --mmproj --outfile ~/derm/out/mmproj-e4b-derm-f16.gguf
```

> **Verified:** `Gemma4ForConditionalGeneration` is registered in llama.cpp's
> converter. The language model quantized cleanly to Q4_K_M (14,236 → 5,042 MiB,
> 5.67 bpw) and the vision projector exported (990 MB, 1411 tensors). The
> quantized model loads and generates in `llama-cli`. **Both files ship
> together** — the Q4_K_M language model + the `mmproj-*` vision projector —
> and you load them with the multimodal runner (`llama-mtmd` / the `mtmd` API).
> Total on-device footprint ≈ 6 GB.

For **this** model the actual on-device footprint is ≈6 GB: the Q4_K_M language
model is 5.0 GB and the vision projector (mmproj) adds 990 MB (measured, see §3).
That is larger than a text-only Q4 E-series model (~3–4 GB) because it ships the
vision tower needed for image input. It runs at usable speed on a modern phone
(the Sunny reference reported "1x speed after model loaded in memory"); load the
model once and keep the session warm. To shrink it, quantize the language model
more aggressively (Q4_0 / Q3) or the mmproj to int8.

## 4. App flow

```
Camera / gallery ──► Bitmap ──► (optional) crop+downscale to ~768px
        │
        ▼
   MPImage / LiteRT image tensor  +  the fixed schema PROMPT
        │
        ▼
   LiteRT-LM session (vision modality enabled)  ──► streamed text
        │
        ▼
   parse "Lesion Type: … / Colour: … / …"  ──► 6 UI fields
        │
        ▼
   store {photo, fields, timestamp, body-area}  ──► longitudinal tracking
```

### 4.1 Kotlin sketch (LiteRT-LM, vision enabled)

```kotlin
// One-time: load the bundled model (warm session)
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath("/data/local/tmp/e4b-derm.litertlm")
    .setMaxTokens(256)
    .setPreferredBackend(Backend.GPU)   // CPU fallback if unavailable
    .build()
val llm = LlmInference.createFromOptions(context, options)

val session = LlmInferenceSession.createFromOptions(
    llm,
    LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTemperature(0.2f)           // low temp -> stable structured output
        .setTopK(40)
        .setGraphOptions(
            GraphOptions.builder().setEnableVisionModality(true).build())
        .build())

// Per photo:
val mpImage = BitmapImageBuilder(bitmap).build()
session.addQueryChunk(SCHEMA_PROMPT)      // the exact training-time prompt
session.addImage(mpImage)
val sb = StringBuilder()
session.generateResponseAsync { partial, done ->
    sb.append(partial)                    // stream tokens into the UI
    if (done) renderFields(parseSchema(sb.toString()))
}
```

### 4.2 The prompt (must match training)

Use the identical instruction the model was trained on (see
`scripts/generate_labels.py::USER_PROMPT`) so the output format is stable:

```
You are a dermatology description assistant. Look at this skin lesion photo and
describe what you see. Do NOT diagnose or name a disease. Report only observable
features in this exact format:
Lesion Type: …
Colour: …
Symmetry: …
Borders: …
Texture: …
Summary: …
```

### 4.3 Parsing the six fields

```kotlin
fun parseSchema(text: String): Map<String,String> =
    listOf("Lesion Type","Colour","Symmetry","Borders","Texture","Summary")
      .associateWith { key ->
          Regex("$key:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim() ?: ""
      }
```

## 5. Safety & product guardrails (non-negotiable)

The model is trained to **describe, not diagnose**, and the app must enforce the
same boundary the data does:

1. **Persistent disclaimer** on every result: "This is a visual description for
   tracking, not a medical diagnosis. See a clinician for any concern." (The
   training targets already end every Summary with this.)
2. **No risk scoring / no benign-malignant call** anywhere in the UI. The
   labeling pipeline bans that language (`generate_labels.py::BANNED`); don't
   reintroduce it in post-processing.
3. **Route concern to care**: a visible "Find a dermatologist" action.
4. **On-device only**: photos and descriptions never leave the phone unless the
   user explicitly exports — a core privacy selling point.
5. **Change-tracking, not alerting**: compare a lesion's fields/photos over time
   and show the user the visual history; do NOT auto-flag "this looks worse."

## 6. Historical domain gap

The training data (HAM10000) is **dermatoscopic** imagery (captured through a
dermatoscope: circular vignette, immersion fluid, polarized detail). Real app
capture is **macro phone photos**. Expect a distribution shift. Before shipping:
- Fine-tune a second pass on (or mix in) phone-camera lesion photos, or
- Constrain the app to a clip-on dermatoscope accessory (matches training), or
- Validate description quality on real phone photos and set expectations in-app.

This gap is closed in the current PAD-UFES-20 training candidate, which uses
smartphone clinical images. Independent patient-level, skin-tone, device, and
lighting validation remains outstanding.
