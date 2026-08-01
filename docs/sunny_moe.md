# Sunny-MoE prototype

## Decision

Sunny-MoE upcycles `HuggingFaceTB/SmolVLM2-2.2B-Instruct` rather than training
vision and language from random initialization. The seed is Apache-2.0 and has
a 1.7B SmolLM2 language backbone plus a compact SigLIP vision tower.

The production candidate replaces the last 12 of 24 gated decoder FFNs with
four experts and activates one expert per layer. Attention, embeddings, the
vision tower, and the first 12 FFNs remain shared. Expert routes are selected
once during image/prompt prefill and pinned for the short six-field response.
Top-1 uses a straight-through gate: its forward value remains exactly one for
function-preserving upcycling, while its backward derivative lets task loss
train the router instead of relying only on load-balancing loss.
During teacher forcing, router pooling is restricted to image/prompt positions;
assistant target tokens are masked out so training matches inference-time route
selection.
Shared LoRA adapters cover all decoder attention projections and the first 12
dense FFNs. They are merged during export, adding no parameters to the deployed
pack; expert-specific LoRA remains on the twelve sparse FFNs.
Input photos are resized to one native vision tile (384px for the 2.2B seed,
512px for the 500M smoke model) instead of SmolVLM's multi-tile 2,048px default.
On the measured 600×450 smoke image this reduced visual tokens from 832 to 64.

| Property | Candidate |
|---|---:|
| Seed parameters | 2.247B |
| Total parameters after upcycling | 4.059B including routers |
| Active parameters | 2.247B |
| Sparse layers | 12 |
| Experts / active experts | 4 / 1 |
| Measured corrected deployment directory | 2.876 GB (2,876,046,492 bytes) |
| Measured weight payload | 2.872 GB |
| FP16 correction adapters | 77.6 MB |
| Theoretical cold expert reads/token | 0.42 GB |

The cold-I/O figure is intentionally pessimistic. Sequence-level route pinning
means the same twelve experts are reused after prefill, allowing a phone runtime
to retain the active path in its RAM cache.

## Why only the last half is sparse

Making all 24 FFNs four-way experts would produce approximately 5.87B total
parameters and likely miss the 3 GB download gate. Upcycling twelve layers adds
1.812B parameters and stays within the target while retaining meaningful expert
capacity.

## Pipeline

1. Recalculate the architecture and size gate:

   ```bash
   python scripts/plan_sunny_moe.py \
     --out exports/sunny_moe/architecture-plan.json
   ```

2. Run a two-step 500M feasibility smoke test on the GPU host:

   ```bash
   python scripts/train_sunny_moe.py --smoke \
     --data-dir ~/derm/labels --images-root ~/derm/data \
     --out ~/derm/out/sunny-moe-smoke
   ```

3. Train the 2.2B candidate after the smoke test passes:

   ```bash
   python scripts/train_sunny_moe.py \
     --data-dir ~/derm/labels --images-root ~/derm/data \
     --out ~/derm/out/sunny-moe-2.2b
   ```

4. Run the PyTorch vision correctness oracle:

   ```bash
   python scripts/run_sunny_moe_reference.py \
     --checkpoint ~/derm/out/sunny-moe-2.2b \
     --image /path/to/test.jpg \
     --out-json ~/derm/out/sunny-moe-reference.json
   ```

   Run the complete held-out quality/safety/router benchmark:

   ```bash
   python scripts/eval_sunny_moe.py \
     --checkpoint ~/derm/out/sunny-moe-2.2b \
     --val ~/derm/labels/val.jsonl --images-root ~/derm/data \
     --out ~/derm/out/sunny-moe-eval.json
   ```

   After Q4 export, rerun the same benchmark with `--q4-pack` in place of
   `--checkpoint`; the correctness loader dequantizes the deployed weights so
   output-level quality and safety changes can be measured directly.

5. Materialize independently addressable FP16 expert blocks, then quantize:

   ```bash
   python scripts/export_sunny_moe_pack.py \
     --checkpoint ~/derm/out/sunny-moe-2.2b \
     --out ~/derm/out/sunny-moe-fp16

   python scripts/quantize_sunny_moe_pack.py \
     --input ~/derm/out/sunny-moe-fp16 \
     --out ~/derm/out/sunny-moe-q4
   ```

   The default precision policy retains the route-sensitive vision tower and
   connector in FP16 and quantizes language/expert matrices to grouped Q4.
   Calibrate correction LoRA directly against those deployed weights, then add
   the FP16 corrections to a copied pack:

   ```bash
   python scripts/train_sunny_moe.py \
     --quantized-pack ~/derm/out/sunny-moe-q4 \
     --calibration-scope adapters \
     --data-dir ~/derm/labels --images-root ~/derm/data \
     --out ~/derm/out/sunny-moe-q4-corrections

   python scripts/apply_sunny_moe_router_checkpoint.py \
     --pack ~/derm/out/sunny-moe-q4 \
     --checkpoint ~/derm/out/sunny-moe-q4-corrections \
     --include-corrections \
     --out ~/derm/out/sunny-moe-q4-corrected
   ```

## Measured GPU result

All quality rows use the same 134-image validation split. `Task` averages
symmetry, borders, texture, colour, and lesion type. `Morph-4` excludes lesion
type so it can be compared with the existing Sunny Pro benchmark.

| Candidate | Size | Task | Morph-4 | Safety | Router entropy | P95 | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| FP16 v3 | 8.12 GB | 0.595 | 0.611 | 100% | 0.979 | 3.89 s | Quality reference |
| All Q4 | 2.16 GB | 0.336 | 0.362 | 97.76% | 0.225 | 3.60 s | Rejected |
| Q4 + FP16 perception | 2.80 GB | 0.384 | 0.387 | 100% | 0.313 | 3.39 s | Rejected |
| Router-only calibrated | 2.80 GB | 0.382 | 0.384 | 100% | 0.591 | 3.69 s | Rejected |
| Q4 + FP16 perception + correction LoRA | 2.876 GB | **0.638** | **0.643** | **100%** | **0.979** | 3.98 s | GPU gates passed |

The corrected pack also achieved 100% six-field format and disclaimer
compliance, 26.27 generated tokens/s, 3.59 s median latency, and a 0.366 worst
per-layer expert share. Its Morph-4 score is 2.39 percentage points above the
existing Pro benchmark's 0.619. Correction training took 11.2 minutes and
peaked at 11.31 GB GPU memory. Hashes for all 17 weight/router/correction files
were independently verified after packaging.

These are PyTorch/L40S correctness measurements, not phone performance. The
reference loader dequantizes Q4 into BF16 and applies unmerged correction LoRA;
it does not benchmark flash streaming or ARM kernels.

## Expert-pack contract

The corrected pack follows Colibri's storage convention:

- language/expert Q4 matrices keep their original tensor name as packed `U8`;
- `<tensor>.qs` contains F32 group scales;
- vision and connector matrices remain FP16 to stabilize expert routing;
- routed layers are separate `experts-layer-NNN.safetensors` files;
- `routers.safetensors` remains floating point;
- `correction-adapters.safetensors` contains 77.6 MB of FP16 low-rank deltas;
- `manifest.json` contains shapes, hashes, quantization error, and layer layout.

This made the intermediate pack suitable for Colibri-style `pread`/LRU
experiments, but did not make unmodified Colibri a SmolVLM runtime. The shipped
Android path instead converts the corrected weights to GGUF and uses the pinned
llama.cpp/mtmd arm64 runtime described in `model_tier_delivery.md`.

## Required gates before Android integration

- 500M smoke completes without OOM and all router/adapter tensors receive
  gradients.
- The upcycled model exactly matches the seed before training.
- Router load does not collapse to one expert across the evaluation set.
- Q4 schema compliance remains 100%; morphology scores must be measured rather
  than inferred from reconstruction error.
- Final mobile pack is approximately 3 GB (measured 3.082 GB plus manifest).
- Phone benchmark measures peak RSS, cold and warm scan latency, flash reads,
  battery draw, and sustained temperature.

The intermediate grouped-safetensors pack was converted into the 3.082 GB
mobile GGUF pair documented in `model_tier_delivery.md`. The arm64 Android
runtime now implements the vision path, Q4 kernels, sequence routing and JNI,
and passes host/native and Android build smoke tests. Physical-phone latency,
peak RSS, thermals and battery validation remain open release gates.
