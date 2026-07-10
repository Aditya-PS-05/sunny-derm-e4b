# Model performance — fine-tuned Gemma 4 E4B dermatology describer

Quantitative evaluation on the **held-out validation set (134 images, 7 classes)**,
comparing the base model against base+adapter on identical inputs, greedy decoding.
Reference targets are the grounded labels (OpenCV morphometry + vision prose).

## Headline: base vs fine-tuned

| Metric | Base Gemma 4 E4B | Fine-tuned | What it measures |
|---|---|---|---|
| Format compliance | 1.00 | 1.00 | all six schema fields present |
| Safety compliance | 1.00 | 1.00 | no banned diagnostic language |
| **Disclaimer present** | **0.07** | **1.00** | Summary ends with "not a diagnosis" |
| **Colour agreement** | **0.05** | **0.65** | Jaccard of colour-name sets vs reference |
| **Symmetry exact-match** | **0.00** | **0.59** | 3-way (roughly symmetric / mildly / notably asymmetric) matches reference |
| **Borders exact-match** | **0.00** | **0.60** | border phrase matches reference |
| **Texture exact-match** | **0.00** | **0.63** | texture phrase matches reference |

**Reading this:** the base model already writes fluent lesion descriptions (both
score 1.0 on format/safety because the prompt is explicit and neither model uses
disease words). What fine-tuning *adds* is **conformance to our specific
controlled vocabulary and the mandatory disclaimer**:
- The disclaimer went from ~7% to **100%** — the base model rarely appends it; the
  fine-tune learned it as an invariant.
- The structured fields (Symmetry/Borders/Texture) are drawn from a fixed phrase
  set the base model has no reason to reproduce verbatim — hence 0.00 exact-match
  for base, ~0.6 for the fine-tune. 0.6 exact-match on a 3-way (symmetry) / 3–4-way
  (borders, texture) controlled vocabulary is well above chance and reflects the
  model genuinely reading the image, not memorising a constant.
- Colour agreement (a softer set-overlap metric) rose 13× (0.05 → 0.65).

## By condition (fine-tuned, colour agreement)

| Condition | n | Colour agreement | Symmetry | Borders | Texture |
|---|---|---|---|---|---|
| melanoma | 22 | 0.80 | 0.50 | 0.64 | 0.45 |
| melanocytic nevus | 23 | 0.75 | 0.70 | 0.65 | 0.61 |
| dermatofibroma | 15 | 0.69 | 0.60 | 0.33 | 0.80 |
| benign keratosis | 19 | 0.68 | 0.68 | 0.58 | 0.53 |
| vascular lesion | 14 | 0.64 | 0.57 | 0.64 | 0.71 |
| basal cell carcinoma | 20 | 0.62 | 0.60 | 0.70 | 0.65 |
| actinic keratosis | 21 | 0.38 | 0.48 | 0.57 | 0.76 |

Colour agreement is highest on the visually distinctive classes (melanoma 0.80,
nevi 0.75) and lowest on actinic keratoses (0.38 — subtle erythematous/scaly
patches where colour naming is inherently ambiguous), though actinic keratoses
score well on texture (0.76, second only to dermatofibroma's 0.80). All classes hold 100%
format/safety/disclaimer.

## Interpretation & honesty notes
- These metrics measure **agreement with the grounded reference labels**, not
  clinical accuracy. The reference itself is measured morphology + model prose,
  not clinician ground truth. This evaluates *whether the student learned the
  teacher's controlled description scheme*, which it clearly did.
- The exact-match ceiling is bounded by reference noise (segmentation-derived
  phrases have their own error); ~0.6 is a strong result under that ceiling.
- **Not a clinical validation.** Real-world accuracy on phone photos requires a
  held-out set of clinician-labelled phone images — see the domain-gap caveat.

## Method
`scripts/eval_model.py` — loads base (4-bit), scores 134 val images, attaches the
adapter, re-scores the same images, aggregates overall + per-class. Full numbers
in `exports/eval_results.json`.
