# Model weights — where they live and how to get them

The trained weights were produced on the GPU host (`ssh:gpu`) at `~/derm/out/`.
The large binaries are **not** copied into this repo folder because the
Claude Science sandbox has no fast file-transfer path to the host (no
`scratch_root` configured; `call_command` stdout is capped at ~64 KB, which makes
streaming multi-GB files impractical). This file records exactly what exists and
the two ways to retrieve it.

## Artifacts on the host (`ec2-user@ssh:gpu:~/derm/out/`)

| File | Size | sha256 (first16) | What it is |
|---|---|---|---|
| `e4b-derm-lora/adapter_model.safetensors` | 139,602,808 B (134 MB) | `4c645ebba206b4d5` | **QLoRA adapter** — the reproducible core. Merge onto the base to reproduce everything else. |
| `e4b-derm-lora/adapter_config.json` | 14,662 B | — | LoRA config (r=16, targets). *(copied into this folder)* |
| `e4b-derm-Q4_K_M.gguf` | 5,302,272,736 B (5.0 GB) | `e41e8bf3d8184980` | Quantized **language model** for llama.cpp/mtmd on Android. |
| `mmproj-e4b-derm-f16.gguf` | 990,372,192 B (990 MB) | `23474645acf3e10f` | **Vision projector** — required with the Q4_K_M file for image input. |
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
Two options to move the 5 GB + 990 MB GGUFs off `ssh:gpu`:
1. **Push to Hugging Face Hub** (host has open internet):
   `huggingface-cli upload <your-repo> ~/derm/out/e4b-derm-Q4_K_M.gguf` (needs a
   write token), then download anywhere.
2. **Configure a `scratch_root`** for `ssh:gpu` in the Compute panel, then the
   agent's `download()` / `submit_job` file-harvest works normally — no chunking.

## Note
`google/gemma-4-E4B-it` is **ungated** — the base downloads with no token or
license acceptance. Only route B option 1 (uploading to *your* Hub repo) needs a
token, and that's a write token for your own account.
