# Using the fine-tuned model — a guide for Claude Code

This is the operating manual for the dermatology-describer model when building
the mobile app. It tells you exactly how the model was trained to be called, so
your app prompts it the way it saw during fine-tuning. **Matching the training
contract is the single biggest lever on output quality** — a mismatched prompt
degrades the model far more than any decoding tweak.

---

## 1. What this model is (and is not)

- **Base:** `google/gemma-4-E4B-it` (ungated), a multimodal (vision+text) ~8B
  effective-4B model designed for on-device use.
- **Fine-tune:** QLoRA (r=16), language tower only, on 1,207 dermatoscopic images
  with grounded structured descriptions.
- **Job:** given ONE skin-lesion image, emit a fixed six-field description in a
  controlled vocabulary, always ending with a not-a-diagnosis disclaimer.
- **It is NOT a diagnostic model.** It never names a disease and must never be
  presented as diagnosing. It describes appearance for tracking over time.
- **Domain caveat:** trained on *dermatoscopic* images (through-the-lens, with
  vignette/immersion artefacts). On raw phone photos it will still produce valid
  output but accuracy drops — plan a phone-photo fine-tune or a clip-on
  dermatoscope for production. See `docs/performance.md`.

---

## 2. The prompt contract (copy this verbatim)

The model was trained with this exact user prompt accompanying the image. Use it
character-for-character; do not paraphrase, shorten, or "improve" it.

```
You are a dermatology description assistant. Look at this skin lesion photo and describe what you see. Do NOT diagnose or name a disease. Report only observable features in this exact format:
Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>
Colour: <colours present>
Symmetry: <symmetric / asymmetric>
Borders: <smooth / irregular / well- or poorly-defined>
Texture: <smooth / rough / raised / scaly>
Summary: <one plain-language sentence describing the lesion's appearance and reminding the user this is not a diagnosis>
```

**Message shape:** one user turn containing the image FIRST, then the text
prompt. Exactly one image per request — the model was never trained on multiple
images or on text-only lesion questions, and will behave out-of-distribution if
you send either.

**Output it produces** (six lines; parse by `Field:` prefix):
```
Lesion Type: pigmented lesion
Colour: multiple colours (light brown, dark brown, red)
Symmetry: roughly symmetric
Borders: somewhat irregular borders
Texture: rough or structurally varied surface
Summary: A light brown, dark brown and red pigmented lesion that appears roughly symmetric with somewhat irregular borders and a rough or structurally varied surface. This is a visual description only, not a diagnosis — see a clinician for any concern.
```

The controlled vocabularies the model learned (your parser can rely on these):
- **Symmetry:** `roughly symmetric` | `mildly asymmetric` | `notably asymmetric`
- **Borders:** `smooth, well-defined borders` | `somewhat irregular borders` | `ragged, poorly-defined borders`
- **Texture:** `smooth, even surface` | `slightly uneven surface` | `rough or structurally varied surface`

---

## 3. Decoding settings

For a **stable, reproducible** app experience use **greedy decoding** (this is
how the model was evaluated):

| Setting | Value | Why |
|---|---|---|
| `do_sample` | `false` (greedy) | deterministic output; the schema is not a creative task |
| `temperature` | 0.0–0.2 | low; the base default of 1.0 will make it wander off-format |
| `max_new_tokens` | 180 | the full six-field output fits comfortably; caps runaway generation |
| `top_p` / `top_k` | n/a when greedy | only relevant if you deliberately sample |

Special tokens (already in the GGUF/HF configs — you don't set these manually):
`bos=<bos>`, `eos=<eos>`, `pad=<pad>`. EOS ids: `[1, 106, 50]`.

> If you want slight natural-language variety in the Summary line, sample at
> `temperature 0.3, top_p 0.9` — but keep `max_new_tokens` at 180 and expect
> the occasional off-vocabulary phrase. For a tracking app, greedy is better.

---

## 4. Which file to load (see `exports/MODELS.md` for paths/sizes)

| Runtime | Files needed | Notes |
|---|---|---|
| **llama.cpp / llama-mtmd** (Android via JNI) | `e4b-derm-Q4_K_M.gguf` (5.0 GB) + `mmproj-e4b-derm-f16.gguf` (990 MB) | Both required for image input. ~6 GB on device. |
| **LiteRT-LM** (Google's on-device stack) | convert the merged checkpoint to `.litertlm` | Handles vision natively; see `docs/android_integration.md`. |
| **transformers** (server / prototype) | merged checkpoint OR base + adapter | Full precision; for testing, not phones. |

The **adapter** (`adapter_model.safetensors`, 134 MB) is the reproducible core —
merge it onto the ungated base to regenerate any of the above.

---

## 5. Minimal integration snippets

### 5a. llama.cpp (C/JNI on Android — the vision path)
Load model + mmproj together and run the multimodal chat:
```bash
# desktop test of the exact Android path:
llama-mtmd-cli \
  -m e4b-derm-Q4_K_M.gguf \
  --mmproj mmproj-e4b-derm-f16.gguf \
  --image lesion.jpg \
  -p "You are a dermatology description assistant. ...(full prompt from §2)..." \
  --temp 0.0 -n 180
```
On Android you call the same via the `mtmd` API in the llama.cpp JNI layer:
create context with both files, add the image to the chat, then the §2 prompt.
See `docs/android_integration.md` for the Kotlin session sketch.

### 5b. transformers (prototype / server)
```python
from transformers import AutoModelForImageTextToText, AutoProcessor
from PIL import Image
import torch

proc = AutoProcessor.from_pretrained("google/gemma-4-E4B-it")
model = AutoModelForImageTextToText.from_pretrained(
    "path/to/e4b-derm-merged", dtype=torch.bfloat16, device_map="auto")

PROMPT = "...(full prompt from §2)..."
img = Image.open("lesion.jpg").convert("RGB")
msgs = [{"role":"user","content":[
    {"type":"image","image":img},
    {"type":"text","text":PROMPT}]}]
inputs = proc.apply_chat_template(msgs, add_generation_prompt=True,
    tokenize=True, return_dict=True, return_tensors="pt").to(model.device)
out = model.generate(**inputs, max_new_tokens=180, do_sample=False)
print(proc.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True))
```

### 5c. Parsing the output (robust)
```python
import re
FIELDS = ["Lesion Type","Colour","Symmetry","Borders","Texture","Summary"]
def parse(text):
    return {f: (re.search(rf"{re.escape(f)}:\s*(.+)", text) or [None,None])[1]
            for f in FIELDS}
# Always check all six are non-None before showing to a user; if any is missing,
# re-run once (greedy is deterministic, so a missing field means a truncated
# generation — raise max_new_tokens) or show a "couldn't read this image" state.
```

---

## 6. Guardrails the app MUST enforce (do not rely on the model alone)

1. **Always display the disclaimer.** The model appends it (100% of the time in
   eval), but your UI should also show a persistent "not a diagnostic tool"
   banner — belt and suspenders for a health app.
2. **Reject non-lesion images.** The model will describe whatever it's given. Add
   a cheap pre-check (blur/skin-tone heuristic, or a tiny classifier) so users
   don't get a "description" of a random photo.
3. **Never surface disease names even if they somehow appear.** Post-filter the
   output for a banned-word list (cancer, melanoma, carcinoma, benign, biopsy,
   tumour, malignant…) and suppress+re-run if hit. The fine-tune scored 100% on
   this in eval, but a health app should hard-enforce it.
4. **One image in, one description out.** Don't build multi-image or
   conversational flows on top of this checkpoint — it's out of distribution.
5. **Tracking, not triage.** Frame the feature as "log how a spot looks over
   time," never "check if this is dangerous."

---

## 7. Quick quality checklist before shipping a prompt change
- [ ] Prompt is byte-identical to §2 (image first, then text).
- [ ] Greedy decoding, `max_new_tokens ≥ 180`.
- [ ] Parser handles all six fields + a missing-field fallback.
- [ ] Disclaimer shown in UI regardless of model output.
- [ ] Banned-word post-filter active.
- [ ] Tested on real phone photos (not just the dermatoscopic eval set) — expect
      lower accuracy and calibrate copy accordingly.

Full evaluation numbers: `docs/performance.md`. Export/merge/quantize details:
`docs/android_integration.md`. Weight locations: `exports/MODELS.md`.
