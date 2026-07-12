# Design — UX & Technical Architecture

Companion to `product.md` (why/who) and `requirements.md` (the testable contract).
This document covers how the app looks, flows, and is built.

## 1. Design principles
1. **Calm, not alarming.** A health-adjacent app about skin cancer must never
   spike anxiety. Neutral colours, plain language, no red "warning" styling on
   descriptions.
2. **Honest by default.** The not-a-diagnosis framing is not fine print — it's
   present on the result screen and reinforced at every decision point.
3. **Private by construction.** Nothing leaves the device. The UI should make this
   visible (an offline/lock indicator), because privacy is the core value prop.
4. **Change is the hero.** The product's value is longitudinal comparison, so the
   timeline and diff view are the most important screens, not the single capture.

## 2. Primary user flows

### 2a. First-time capture
```
Onboarding (privacy + "not a diagnosis" consent)
  → Add lesion → name/locate it (body area)
  → Camera with framing guides (distance, lighting, fill-frame)
  → Capture → on-device inference (progress spinner, ~seconds)
  → Result: six-field description + disclaimer banner
  → Save to this lesion's timeline
```

### 2b. Re-check (the retention loop)
```
Home (list of tracked lesions, each with last-checked date + thumbnail)
  → Select lesion → Timeline (dated photos + descriptions)
  → "Re-check now" → same capture flow
  → Result screen ALSO shows a field-level diff vs the previous entry
     e.g.  Borders:  somewhat irregular  →  ragged, poorly-defined   ⚠ changed
  → If any field changed materially → gentle prompt: "This has changed since
     <date>. Consider showing a clinician." (routing, never a verdict)
```

### 2c. Export (premium)
```
Lesion → Export → PDF: photos over time + descriptions + dates
  → "Share with your dermatologist"  (generated on-device)
```

## 3. Screen inventory
| Screen | Purpose | Key elements |
|---|---|---|
| Onboarding | Consent + privacy + limits | "Not a diagnosis" acknowledgement, offline promise |
| Home | List of tracked lesions | Thumbnail, name, body area, last-checked, change badge |
| Capture | Take the photo | Live framing guides, lighting hint, capture button |
| Result | Show the description | Six fields, disclaimer banner, "see a clinician" link, Save |
| Timeline | One lesion over time | Chronological photos + descriptions, change markers |
| Diff | Compare two entries | Side-by-side photos, field-by-field change table |
| Educate | What to watch for | ABCDE rule, when to seek care (static, reviewed content) |
| Settings | Reminders, export, about | Re-check cadence, model info, privacy statement |

## 4. Result screen — the description card
Renders the six parsed fields in fixed order, each as a labelled row:

```
┌─────────────────────────────────────────────┐
│  Lesion Type   pigmented lesion              │
│  Colour        light brown, dark brown, red  │
│  Symmetry      roughly symmetric             │
│  Borders       somewhat irregular borders    │
│  Texture       rough / structurally varied   │
│─────────────────────────────────────────────│
│  Summary  A light brown … roughly symmetric  │
│           … not a diagnosis; see a clinician │
├─────────────────────────────────────────────┤
│  ⓘ This is a visual description only, not a  │
│     diagnosis. See a clinician for concerns. │  ← persistent banner
└─────────────────────────────────────────────┘
```
Field values come straight from the model's controlled vocabulary (see
`docs/USING_THE_MODEL.md` §2) — the UI does not paraphrase them.

## 5. Data model (on-device, e.g. SQLite + file store)
```
Lesion        { id, name, body_area, created_at, reminder_cadence }
Observation   { id, lesion_id, captured_at, image_path,
                lesion_type, colour, symmetry, borders, texture, summary,
                model_version, raw_output }
```
- Images stored in app-private storage; never uploaded.
- `raw_output` retained for debugging/audit; `model_version` stamps which
  checkpoint produced each description (so upgrades are traceable).
- Comparison = literal field-level differences between consecutive `Observation`
  rows. The app does not turn those differences into an ordinal score, stability
  label, risk level, urgency verdict, or recommended care interval.

## 6. Technical architecture (on-device inference)

```
Camera → JPEG → [pre-check: is-this-a-lesion heuristic]
      → Image + fixed prompt (USING_THE_MODEL.md §2)
      → Gemma 4 E4B (fine-tuned, quantized) via runtime below
      → 6-field text → parser → [banned-word post-filter] → Observation row
```

**Runtime options** (see `docs/android_integration.md` for the full comparison):
| Runtime | Files | Notes |
|---|---|---|
| **llama.cpp / mtmd** (recommended, portable) | `e4b-derm-Q4_K_M.gguf` 5.0 GB + `mmproj-e4b-derm-f16.gguf` 990 MB | JNI wrapper; both files required for vision. ≈6 GB on device. |
| **LiteRT-LM** (Google on-device stack) | `.litertlm` from merged checkpoint | Native vision; tighter Android integration. |

**Model call contract (do not deviate):** image first then the verbatim prompt,
one image per request, **greedy decoding**, `max_new_tokens = 180`. The prompt
contract is the biggest quality lever — a paraphrased prompt degrades output more
than any decoding change. Full detail in `docs/USING_THE_MODEL.md`.

**Performance envelope:** ~6 GB model → target phones with ≥8 GB RAM. Keep the
model **warm** (load once, reuse the session) so only the first inference pays the
load cost. For low-end devices, ship a more aggressively quantized fallback
(Q4_0 / Q3 language model, int8 mmproj) or gate the feature behind a spec check.

## 7. Safety design (enforced in the app, not just the model)
The model scored 100% on format/safety/disclaimer in evaluation, but a health app
must not rely on the model alone. The UI/logic layer enforces:
1. **Persistent disclaimer** on every result and timeline screen.
2. **Fail closed without the real model**; no synthetic/demo analysis can surface.
3. **Banned-word post-filter** (cancer, melanoma, carcinoma, benign, biopsy,
   malignant, tumour…) — suppress + re-run if the model ever emits one.
4. **No verdicts** — the app never converts a description into a risk level.
5. **Routing to care** on any description difference and always reachable; no
   claim that an absent difference means stability or safety.

## 8. Visual design notes
- **Palette:** neutral/clinical (soft blues, greys, off-white); reserve any accent
  colour for the "changed since last check" marker only, and even then keep it
  informative (amber "review"), never alarming (red "danger").
- **Typography:** high legibility; the six field labels in a consistent
  small-caps/label style, values in body weight.
- **Accessibility:** large tap targets for the capture flow; the description card
  must be screen-reader friendly (each field a labelled row).
- **Empty states:** first-run home should teach the tracking loop ("add a spot,
  then re-check it in a few weeks to see change").
