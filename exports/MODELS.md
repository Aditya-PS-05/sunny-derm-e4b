# Model weights — where they live and how to get them

The trained weights were produced on the GPU host (`sunny-gpu`). The Android
mobile pack is under
`~/sunny-training/out/sunny-moe-2.2b-v4-gguf/`. The older expert-pack/GGUF/ONNX
artifacts below are retained for reproducibility but are no longer Android
download products; Gemma/E4B Pro is server-only.
The large binaries are **not** copied into this repo folder because the
Claude Science sandbox has no fast file-transfer path to the host (no
`scratch_root` configured; `call_command` stdout is capped at ~64 KB, which makes
streaming multi-GB files impractical). This file records exactly what exists and
the two ways to retrieve it.

## Artifacts on the host

| File | Size | sha256 | What it is |
|---|---|---|---|
| `sunny-moe-2.2b-v4-gguf/` | 3,082,369,677 B including manifest | `exports/model_tiers/sunny-moe-2.2b-v4-gguf/manifest.json` | **Current Android pack**: Q4_K_M mixed dense/MoE text GGUF + corrected FP16 vision GGUF. |
| `sunny-moe-2.2b-v3-q4-corrected/` | 2,876,046,492 B | Per-file hashes in `exports/sunny_moe/v3-q4-corrected-manifest.json` | Intermediate grouped-safetensors training/export pack; not loaded by Android. |
| `e4b-derm-lora/adapter_model.safetensors` | 139,602,808 B (134 MB) | `4c645ebba206b4d5` | **QLoRA adapter** — the reproducible core. Merge onto the base to reproduce everything else. |
| `e4b-derm-lora/adapter_config.json` | 14,662 B | — | LoRA config (r=16, targets). *(copied into this folder)* |
| `e4b-derm-Q4_K_M.gguf` | 5,302,272,736 B (5.0 GB) | `e41e8bf3d8184980023bb2af2d0b565463f359a9b6c46a8e95b77da61af472ce` | Legacy Pro export; server/reproducibility only, not downloaded by Android. |
| `mmproj-e4b-derm-f16.gguf` | 990,372,192 B (990 MB) | `23474645acf3e10f7789cfb5dddacbf00a0d693b4f958b37ecc9b217071d7f46` | **Vision projector** — required with the Q4_K_M file for image input. |
| `mmproj-e4b-derm-Q8_0.gguf` | 559,874,816 B (560 MB) | `ca84f3c750bd559c1c43f714f4c677edceb4ae8d850fad71cd10cfdab1fe0ef6` | Legacy Pro projector; server/reproducibility only. |
| `sunny-lite-mobilenetv3.onnx` | 6,192,794 B | `7358037d02cefc53adcba0bb29772c60e9ef9f7f82af14274d0d2e70c98e836e` | Retired Android Lite experiment. |
| `sunny-medium-convnexttiny.onnx` | 111,458,297 B | `fe3e95e15954efddc971518188277af5f9be4fea2ae240a7fa90c76bb772f626` | Retired Android Medium experiment. |
| `e4b-derm-merged/` | ~15 GB | — | Full fp16 merged HF checkpoint (adapter folded in). Re-quantize from here. |

`adapter_model.safetensors` is fetched into this folder (`model_on_host/`) when
the transfer completes — it's small enough to stream. The GGUFs and merged
checkpoint stay on the host.

## Retrieval route A — reproduce from the adapter (recommended)
The adapter (134 MB) + the ungated base is all you need; everything else is
derived. From any machine with the base model:
```bash
python scripts/export_android.py merge \
    --base google/gemma-4-E4B-it \
    --adapter model_on_host/  --out ./e4b-derm-merged
# then quantize (see docs/android_integration.md §3):
python llama.cpp/convert_hf_to_gguf.py ./e4b-derm-merged --outfile f16.gguf --outtype f16
llama.cpp/build/bin/llama-quantize f16.gguf e4b-derm-Q4_K_M.gguf Q4_K_M
python llama.cpp/convert_hf_to_gguf.py ./e4b-derm-merged --mmproj --outfile mmproj.gguf
```

## Retrieval route B — pull the GGUFs directly off the host
Two options to move the Pro GGUF pair off `ssh:gpu`:
1. **Push to Hugging Face Hub** (host has open internet):
   `huggingface-cli upload <your-repo> ~/derm/out/e4b-derm-Q4_K_M.gguf` (needs a
   write token), then download anywhere.
2. **Configure a `scratch_root`** for `ssh:gpu` in the Compute panel, then the
   agent's `download()` / `submit_job` file-harvest works normally — no chunking.

## Note
`google/gemma-4-E4B-it` is **ungated** — the base downloads with no token or
license acceptance. Only route B option 1 (uploading to *your* Hub repo) needs a
token, and that's a write token for your own account.
