# Sunny-style Dermatology Description Model — Gemma 4 E4B, on-device Android

Fine-tune Google's **Gemma 4 E4B** (multimodal, mobile-native) to produce structured
skin-lesion *descriptions* (not diagnoses) from a phone photo, then export it to run
on-device on Android. Modeled on the "Sunny" app (Daniel Bourke), which fine-tuned
MedGemma; here we use E4B because its vision path converts cleanly to Android runtimes.

> **Not a diagnostic tool.** The model describes what a lesion looks like and supports
> longitudinal tracking. It must never assert benign/malignant or a diagnosis, and the
> app must route any medical concern to a qualified clinician.

## Output schema (matches the app UI)
`Lesion Type · Colour · Symmetry · Borders · Texture · Summary`

## Pipeline (run order)
1. `scripts/fetch_images.py`  — pull class-balanced dermatology images (runs **on the GPU host**, which has open internet).
2. `scripts/generate_labels.py` — teacher VLM drafts the structured schema per image; rule-based QA filter; emits `train.jsonl` / `val.jsonl`.
3. `scripts/train_qlora.py` — QLoRA fine-tune of Gemma 4 E4B (transformers/peft/trl), dispatched to `ssh:gpu` (A10G, 23 GB).
4. Merge adapter → base; quantize to GGUF Q4_K_M (llama.cpp) and/or LiteRT-LM `.litertlm`.
5. `docs/android_integration.md` — load the quantized model on-device (LiteRT-LM / llama.cpp), camera→inference→structured-field rendering.

## Data source & access
- **Primary:** `marmal88/skin_cancer` on the Hugging Face Hub — a HAM10000-derived set
  with per-image diagnosis labels (7 classes). Parquet shards with embedded images.
- **Why not ISIC direct:** `api.isic-archive.com` and `us.aws.cdn.hf.co` are **blocked**
  in the Claude Science sandbox (proxy 403). The GPU host (`ssh:gpu`) has **open**
  internet including the HF CDN, so all bulk image fetching happens there; only a small
  pilot sample is pulled back to the sandbox for teacher-labeling.

## Target classes (weighted toward high-commercial-value conditions)
Tier-1 (skin-cancer anxiety / sun damage — top purchase drivers):
melanocytic nevi, melanoma, basal-cell carcinoma, actinic keratoses / Bowen's.
Also present in HAM10000: benign keratosis, dermatofibroma, vascular lesions.

## Compute
`ssh:gpu` — AWS EC2, 1× A10G (23 GB), system python3.9 only (provision a user-space env),
~73 GB free on `~`, open internet. No scheduler (direct exec).
Provisioned env `derm` (miniconda): torch 2.12+cu130, transformers 5.13, peft, trl,
bitsandbytes, opencv. All Gemma model classes present; bf16 supported.

---

## End-to-end run guide

All heavy steps run on the A10G in the `derm` conda env
(`source ~/miniconda3/etc/profile.d/conda.sh && conda activate derm`).

```bash
# 1. Fetch class-balanced images (host has open internet)
python scripts/fetch_images.py --out ~/derm/data --per-class 200 --seed 42

# 2. Generate grounded, safety-filtered labels (hybrid: features + vision prose)
python scripts/generate_labels.py \
    --images-root ~/derm/data --manifest ~/derm/data/manifest.csv \
    --out-dir ~/derm/labels_full --summaries ~/derm/labels/teacher_summaries.json

# 3. QLoRA fine-tune Gemma 4 E4B  (base is UNGATED — no token/license needed)
python scripts/train_qlora.py \
    --model google/gemma-4-E4B-it \
    --data-dir ~/derm/labels_full --images-root ~/derm/data \
    --out ~/derm/out/e4b-derm-lora --epochs 3 --batch-size 1 --grad-accum 8

# 4. Merge adapter + export for Android
python scripts/export_android.py merge \
    --adapter ~/derm/out/e4b-derm-lora --out ~/derm/out/e4b-derm-merged
python scripts/export_android.py litert \
    --merged ~/derm/out/e4b-derm-merged --out ~/derm/out/e4b-derm.litertlm
```

Then integrate on Android per `docs/android_integration.md` (LiteRT-LM, vision
enabled, warm session, six-field schema parsing, safety guardrails).

## Building the app — start here
- **`docs/USING_THE_MODEL.md`** — the operating manual for calling the model
  correctly: the exact prompt contract, decoding settings, per-runtime load
  instructions, integration snippets, output parser, and the guardrails the app
  must enforce. **Read this before writing any inference code.**
- **`exports/eval_results.json` + `docs/performance.md`** — measured quality
  (base vs fine-tuned on 134 held-out images).
- **`exports/MODELS.md`** — where the weights live and how to fetch them.
- **`pull_weights.sh`** — run on *your* machine to rsync the GGUFs + adapter from
  the GPU host into `exports/model_on_host/` (`./pull_weights.sh <ssh-alias>`).

## Status — COMPLETE end-to-end ✅
- ✅ Data: full HAM10000 (13,354 imgs) fetched on host; balanced 1,343-img sample.
- ✅ Labels: hybrid teacher (OpenCV morphometry + vision prose + safety filter) →
  1,341 grounded labels (1,207 train / 134 val), 2 dropped.
- ✅ Training: 3-epoch QLoRA on A10G, 61 min. train_loss 3.22→0.10,
  eval_loss 0.144→0.126→0.123. Adapter 134 MB (34.9M params, 0.44%).
- ✅ Validation: held-out val images → correct 6-field schema + disclaimer.
- ✅ Export: merged (15 GB) → **Q4_K_M GGUF 5.0 GB** + **vision mmproj 990 MB**
  (≈6 GB on-device). Quantized model loads & generates.
- ✅ Android: LiteRT-LM + llama.cpp/mtmd integration documented.

**`google/gemma-4-E4B-it` is ungated** — no HF token or license acceptance needed.
(The gated model is the older `gemma-3n-E4B-it`; we do not use it.)

Model artifacts live on the GPU host at `~/derm/out/` (too large to round-trip
through the sandbox; push to HF Hub or pull via a configured scratch_root).

## Security note
An HF token was pasted in chat during early setup, before we determined the model
was ungated and no token was needed. It was never required and should be **rotated**
anyway (regenerate at huggingface.co/settings/tokens). The scripts read any token
from the environment and never embed it.

## Key caveat — domain gap
HAM10000 is **dermatoscopic** imagery (through-the-scope). A phone app captures
**macro photos**. The pipeline is correct and complete on this data, but a
production model needs a second fine-tuning pass on phone-camera photos (or a
clip-on dermatoscope accessory). See `docs/android_integration.md` §6.
