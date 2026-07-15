# Product Definition — On-Device Skin Tracker

## 1. One-line pitch
A private, offline Android app that lets people **photograph a skin spot and track
how its appearance changes over time**, using an on-device AI model that describes
what it sees in plain, consistent language — never a diagnosis.

## 2. The problem
People notice a mole or spot and worry. Their options today are poor:
- **Wait-and-see with no record.** Memory is unreliable; "has it changed?" is
  exactly the question that matters for skin lesions, and it's the hardest to
  answer from recollection.
- **Cloud "skin checker" apps.** Most upload photos of your body to a server,
  raising privacy concerns, and many overstep into implied diagnosis — a
  regulatory and trust minefield.
- **See a dermatologist.** Correct, but slow (weeks of wait), costly, and
  overkill for "is this worth watching?"

There is a gap for a tool that is **private, instant, and honest about its
limits** — a structured logbook, not an oracle.

## 3. What it is (and isn't)
| It IS | It is NOT |
|---|---|
| A longitudinal photo + description tracker | A diagnostic tool |
| Fully on-device (no photo ever leaves the phone) | A cloud service |
| A consistent, structured describer of appearance | A dermatologist replacement |
| A prompt to see a clinician when something changes | A source of "benign/malignant" verdicts |

**Positioning line (used verbatim in the UI):** *"Describe and track — not
diagnose."* This is both the ethical stance and the regulatory safe harbour: the
app reports observable features and change over time, and routes every medical
concern to a qualified clinician.

## 4. Target users & market
Primary: **privacy-conscious adults in the US and Western Europe** who have one or
more moles/spots they want to keep an eye on — the "skin-cancer-aware but not
alarmed" segment. Secondary: people managing a visible, chronic skin condition who
want to track flare-ups.

**What the shipped model was actually trained on.** The current fine-tune used a
single dataset — `marmal88/skin_cancer` (HAM10000-derived, 7 pigmented/lesion
classes). The training set was **class-balanced** by capping each class at ~200
images rather than reflecting HAM10000's natural distribution (which is ~85%
melanocytic nevi). The sampled counts were: melanoma 200, basal-cell carcinoma
200, actinic keratoses 200, melanocytic nevi 200, benign keratoses 200,
vascular lesions 189, dermatofibroma 154 (the last two had fewer than 200
available) — 1,343 images total, of which 1,341 produced valid labels
(1,207 train / 134 val). Balancing prevents the model from collapsing to the
majority nevi class; it does **not** privilege cancer classes over benign ones —
benign keratosis is sampled at the same 200.

So the shipped model describes **pigmented / neoplastic skin lesions**. It was
**not** trained on inflammatory or pigmentary conditions.

**Market roadmap (aspirational, NOT in the current model).** The broader
willingness-to-pay landscape for this audience also includes high-volume chronic
and long-tail conditions — acne, atopic dermatitis, psoriasis, rosacea,
seborrheic dermatitis, melasma, contact dermatitis. Covering these requires
**additional datasets and further fine-tuning passes**; they are future scope, not
capabilities of the shipped checkpoint.

## 5. Value proposition
1. **Privacy by construction** — inference runs locally; photos are stored only on
   the device. This is the headline differentiator versus cloud competitors.
2. **Offline & instant** — no connectivity needed; a description in ~seconds.
3. **Consistency** — the same six structured fields every time, so change is
   visible across a timeline rather than buried in fuzzy memory.
4. **Honesty** — never diagnoses; explicitly nudges toward professional care. This
   builds trust and keeps the product on the right side of medical-device rules.

## 6. Core features (MVP)
- **Capture** a lesion photo (with framing/lighting guidance).
- **Describe** — the production on-device model returns the six-field structured
  description. The temporary, explicitly disclosed beta can use the same model
  through a remote GPU server while phone-photo data is collected and evaluated.
- **Track** — each lesion is a timeline of dated photos + descriptions; the app
  highlights field-level changes (e.g. "Borders: smooth → somewhat irregular").
- **Recheck consistently** — the previous encrypted photo can guide framing;
  technical matching helps reproduce centering, distance and phone angle.
- **Compare** — inspect any older/newer pair with fade, wipe, blink or aligned
  side-by-side views, with a raw-photo fallback when registration is unreliable.
- **Photo-check sessions** — work through a resumable checklist of saved areas,
  marking each complete or skipped without implying a complete clinical exam.
- **Measure optionally** — record an explicitly approximate reference-to-area
  size ratio for personal tracking, never as a clinical measurement.
- **Remind** — optional re-check reminders (e.g. every 3 months).
- **Share** — generate a clinician-oriented visual tracking pack with aligned
  comparison, model provenance, user notes and uncropped originals.
- **Educate & route** — plain-language "what to watch for" (the ABCDE rule) and a
  persistent "see a clinician" path.

## 7. Deliberately out of scope (v1)
- Any diagnostic claim, risk score, or benign/malignant classification.
- Cloud sync / account system (privacy stance; may revisit as encrypted opt-in).
- Multi-image or conversational analysis (the model is one-image-in/one-out).
- Diagnostic or automated whole-body completeness claims.

## 8. Business model (future, blocked)
No monetization or public model distribution is permitted until the training-data
rights and clinical/regulatory gates in `RELEASE_READINESS.md` are cleared.
After clearance, an indicative model is: the free tier delivers the complete
tracking outcome for a limited number of saved areas, including reminders,
quality safeguards, export and deletion. A one-time unlock or low subscription
can expand capacity and convenience through unlimited areas, batch photo checks,
advanced comparisons, encrypted multi-device backup and family profiles.
Production processing stays on-device regardless of tier — privacy is not a
paywalled feature. The private model-improvement beta is a temporary exception:
it is build-mode selected, requires explicit disclosure, and is blocked from
release builds.

## 9. Key risks & mitigations
| Risk | Mitigation |
|---|---|
| Perceived as a diagnostic device (regulatory) | Hard "not a diagnosis" framing + guardrails (§requirements); no verdict language ever. |
| Model trained on dermatoscopic images, users shoot phone photos | Public release blocked pending clinician-labelled phone-photo and skin-tone validation. See `RELEASE_READINESS.md`. |
| Training-image commercial rights unresolved | No monetization or weight publication until the data chain has written legal clearance. |
| User over-relies on the app instead of seeing a doctor | Explicit routing to care on every screen; reminders framed as "check with a professional." |
| ~6 GB on-device model too large for low-end phones | Set a minimum-spec floor; offer a smaller-quant fallback (see design.md §tech). |

## 10. Success metrics
- **Retention:** % of users who log a *second* photo of the same lesion (the whole
  point is tracking, so repeat capture is the core engagement metric).
- **Trust:** disclaimer comprehension (surveyed), low rate of misinterpretation.
- **Quality:** on-device description latency, field-parse success rate (target
  100%), zero disease-name leakage.
- **Core loop:** percentage of saved areas that receive a technically comparable
  follow-up photo; median time from reminder to completed follow-up pair.
