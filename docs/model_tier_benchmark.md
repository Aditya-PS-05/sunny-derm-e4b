# Sunny tier benchmark

> Historical experiment: Lite/Medium and offline GGUF Pro are no longer wired
> into Android. The current downloadable candidate is Sunny-MoE; see
> `sunny_moe.md`. The measurements below remain for reproducibility only.

Date: 2026-07-16

## Result

Sunny now has three real artifact families. Lite and Medium use compact visual
descriptor networks because the product output is a fixed five-field visual
schema plus a deterministic safety summary; a multi-gigabyte language model is
not necessary for those tiers. Pro remains the fine-tuned Gemma 4 E4B VLM.

| Tier | Runtime | Download | SHA-256 |
|---|---|---:|---|
| Lite | MobileNetV3 Small ONNX | 6,192,794 B (6.19 MB) | `7358037d02cefc53adcba0bb29772c60e9ef9f7f82af14274d0d2e70c98e836e` |
| Medium | ConvNeXt Tiny ONNX; ensembles with installed Lite | 111,458,297 B (111.46 MB) | `fe3e95e15954efddc971518188277af5f9be4fea2ae240a7fa90c76bb772f626` |
| Pro offline | E4B Q4_K_M + Q8_0 projector | 5,862,147,552 B (5.86 GB) | See `q8_projector_benchmark.md` |

## Leakage-free descriptor evaluation

The final split contains 154 unique HAM10000 images from 125 lesions. All views
of a lesion stay in one split; train/validation lesion overlap is zero. Duplicate
ISIC image IDs from the dataset mirror are removed. Lite used 1,101 unique
training images. Medium used 3,599 unique training images with a class-balanced
sampler. Both use ImageNet-pretrained backbones.

| Metric | Lite | Medium network | Medium runtime ensemble |
|---|---:|---:|---:|
| Lesion type exact | 65.58% | 66.88% | 70.13% |
| Symmetry exact | 65.58% | 66.23% | 65.58% |
| Borders exact | 65.58% | 64.29% | 68.18% |
| Texture exact | 73.38% | 68.18% | 70.78% |
| Colour Jaccard | 58.53% | 68.71% | 64.72% |
| Mean task score | 65.73% | 66.86% | 67.88% |

The Medium ensemble is +2.15 percentage points over Lite, but the paired
bootstrap 95% interval is -0.45 to +4.73 points. The interval includes zero, so
this experiment does **not** establish a statistically reliable overall quality
gain. Medium is a higher-capacity candidate with stronger point estimates, not
a clinically proven premium model.

CPU-only ONNX Runtime latency on the GPU host's server CPU was approximately
2.1 ms median for Lite and 104.1 ms for the Medium network. The ensemble adds
the Lite pass, so its model-only latency is approximately 106 ms there. These
are not phone measurements; representative Android latency, peak RAM, thermals,
and NNAPI performance still need device testing.

Pro was evaluated separately for Q8-projector equivalence on 70 images. That
test found no measurable regression versus the F16 projector, but it does not
support a direct Lite/Medium/Pro quality ranking. See `q8_projector_benchmark.md`.

## Download time

Ideal transfer times, excluding protocol and verification overhead:

| Connection | Lite | Medium | Pro offline |
|---|---:|---:|---:|
| 10 Mbps | 5.0 s | 1.49 min | 78.2 min |
| 25 Mbps | 2.0 s | 35.7 s | 31.3 min |
| 50 Mbps | 1.0 s | 17.8 s | 15.6 min |
| 100 Mbps | 0.5 s | 8.9 s | 7.8 min |

General-purpose compression does not make Pro a 2 GB download. Fast zstd
reduced the quantized Pro pair from 5.862 GB to 5.553 GB (5.27%). It also would
require temporary extraction storage and prevent direct mmap while compressed.
Fast gzip reduced Lite to 5.75 MB and Medium to 103.54 MB, about 7% each.

The non-debuggable arm64 beta APK is 39 MB with both ONNX and the Pro llama.cpp
JNI runtime (33 MB without llama.cpp), down from a 123 MB four-ABI universal
APK. Model packs remain separate on-demand downloads.

## Safety and limitations

- Lite and Medium predict only observable descriptor labels. Kotlin renders the
  sixth summary line and mandatory non-diagnosis reminder.
- The references are deterministic morphology labels produced by
  `generate_labels.py`, not independent clinician annotations.
- HAM10000 is dermatoscopic; Sunny receives phone photos with different lighting,
  distance, focus, skin-tone distribution, and artifacts.
- These models are not diagnostic devices and this is not clinical validation.
- Commercial rights for the dataset and pretrained weights must be cleared before
  a public release; the Gradle release gate remains fail-closed.

Raw aggregate results are in
`exports/model_tiers/descriptor-benchmark.json`. Training is reproduced with
`scripts/fetch_images.py`, `scripts/generate_labels.py`, and
`scripts/train_lite.py`.
