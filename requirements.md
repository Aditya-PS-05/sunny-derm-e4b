# Requirements

The testable contract for the app. Companion to `product.md` (why/who) and
`design.md` (how). IDs are stable so they can be referenced in issues/tests.
Priority: **M** = MVP / must-have, **S** = should-have, **C** = could-have.

## 1. Functional requirements

### Capture & inference
| ID | Pri | Requirement |
|---|---|---|
| F-01 | M | The app SHALL capture a lesion photo using the device camera with on-screen framing/lighting guidance. |
| F-02 | M | Production/release builds SHALL run description inference **entirely on-device**. An explicitly identified debug beta MAY use remote inference only with truthful disclosure, explicit acknowledgement, and a release gate that prevents it shipping. |
| F-03 | M | The app SHALL send the model exactly one image plus the verbatim prompt from `docs/USING_THE_MODEL.md` §2, image first. |
| F-04 | M | The production on-device path SHALL use greedy decoding with `max_new_tokens = 180`. A beta server MAY use a larger internal reasoning budget but SHALL return only the guarded six-field schema. |
| F-05 | M | The app SHALL parse the model output into the six fields (Lesion Type, Colour, Symmetry, Borders, Texture, Summary). |
| F-05A | M | Before presentation, the app SHALL map free-form observable fields to a controlled non-diagnostic vocabulary, resolve contradictory terms to an explicit `unclear` value, and generate the Summary from those controlled fields. Raw model output SHALL remain available only for audit and opted-in improvement data. |
| F-06 | M | If any of the six fields fails to parse, the app SHALL NOT show a partial result as final and SHALL show a "couldn't read this image" state. Production decoding is deterministic and grammar-constrained, so repeating the identical request is prohibited. |
| F-07 | S | The app SHALL run a pre-inference check to reject clearly non-lesion images (e.g. no skin detected) before calling the model. |
| F-08 | M | Before inference, the app SHALL flag obviously undersized, underexposed, overexposed, or low-contrast photos and offer retake guidance; the user MAY explicitly continue because the check is photographic, not medical. |
| F-09 | S | While the camera is open, the app SHALL provide non-medical exposure/detail feedback and tap-to-focus without retaining preview frames. |

### Tracking
| ID | Pri | Requirement |
|---|---|---|
| F-10 | M | The app SHALL let the user create named lesions, each with a body-area tag. |
| F-11 | M | The app SHALL store each capture as a dated Observation in that lesion's timeline (photo + six fields + model version). |
| F-12 | M | The app SHALL display a per-lesion chronological timeline of photos and descriptions. |
| F-13 | M | On re-check, the app SHALL show each changed controlled field as an explicit Previous → Current value versus the previous Observation. |
| F-14 | M | When a field changes materially, the app SHALL prompt the user to consider seeing a clinician — as routing, never as a verdict. |
| F-15 | S | The app SHALL offer optional re-check reminders at a user-set cadence. |
| F-16 | S | The app SHALL export an on-device clinician-oriented visual tracking PDF containing notes, model provenance, aligned first/latest comparison, and all selected uncropped originals. |
| F-17 | S | A re-check camera SHALL offer the previous encrypted photo as an optional alignment overlay and append the reviewed result to the same timeline. |
| F-18 | S | Saved scans SHALL be searchable by name/location and sortable by update date or name. |
| F-19 | S | Re-check capture SHALL provide technical centering, distance and rotation guidance derived from the previous photo, without retaining preview frames or expressing a medical conclusion. |
| F-19A | S | Compare SHALL constrain selection to an older/newer pair, expose fade/wipe/blink/side views, and fall back to raw photos when automatic registration confidence is low. |
| F-19B | S | The app SHALL offer one encrypted, resumable photo-check session over saved areas with pending, completed and skipped states, while stating that it is not a complete examination. |
| F-19C | C | The user MAY record an approximate size from a manually positioned known-width reference; the stored record SHALL retain the reference and target spans used for the ratio. |

### Education & routing
| ID | Pri | Requirement |
|---|---|---|
| F-20 | M | The app SHALL provide static, reviewed "what to watch for" guidance (ABCDE) and a persistently reachable "see a clinician" path. |

## 2. Safety & compliance requirements (hard constraints)
| ID | Pri | Requirement |
|---|---|---|
| S-01 | M | The app SHALL NEVER present output as a diagnosis, risk score, or benign/malignant classification. |
| S-02 | M | Every result and timeline screen SHALL display a visible "this is a visual description only, not a diagnosis" disclaimer. |
| S-03 | M | The app SHALL post-filter model output against a banned-word list (cancer, melanoma, carcinoma, benign, malignant, biopsy, tumour, precancerous, …) and suppress+re-run if any appears. |
| S-04 | M | The app SHALL obtain explicit user acknowledgement of the not-a-diagnosis limitation during onboarding and, in server beta mode, remote photo processing. |
| S-05 | M | The app SHALL NOT convert any description into a risk level or urgency verdict. |
| S-06 | M | The app SHALL NOT build multi-image or conversational analysis on this model (out of its trained distribution). |
| S-07 | S | Marketing and store copy SHALL avoid diagnostic claims to stay within non-medical-device positioning; seek regulatory review before any claim change. |
| S-08 | M | Reference-based size values SHALL always be labelled approximate and SHALL NOT be presented as clinical measurements, growth verdicts, risk or urgency. |

## 3. Privacy & data requirements
| ID | Pri | Requirement |
|---|---|---|
| P-01 | M | Persistent photos and descriptions SHALL be stored only in app-private on-device storage. A debug server beta MAY transmit a disclosed in-memory copy for inference or separately opted-in contribution. |
| P-02 | M | Production/release builds SHALL function fully offline for the core describe/track loop. Debug server beta networking SHALL be explicit, mode-gated, and blocked from release. |
| P-03 | M | The app SHALL NOT include analytics that transmit image content or descriptions off-device. |
| P-04 | S | The app SHALL let the user delete a lesion (and all its Observations) permanently. |
| P-05 | C | Any future cloud sync SHALL be opt-in and end-to-end encrypted. |
| P-06 | S | Active photo-check progress and reference-based measurement metadata SHALL be encrypted at rest and cleared by Delete all local data. |

## 4. Non-functional requirements

### Performance
| ID | Pri | Requirement |
|---|---|---|
| N-01 | M | On a target device (≥8 GB RAM), a single description SHALL complete within a few seconds once the model is warm. |
| N-02 | M | The app SHALL load the model once and keep the session warm across captures within a session. |
| N-03 | S | The app SHALL declare a minimum device spec and degrade gracefully (or offer a smaller-quant model) below it. |

### Model / deployment
| ID | Pri | Requirement |
|---|---|---|
| N-10 | M | The app SHALL ship the fine-tuned Gemma 4 E4B as Q4_K_M GGUF (5.0 GB) + vision mmproj (990 MB), or the equivalent LiteRT-LM package. |
| N-11 | M | The app SHALL stamp each Observation with the model version that produced it. |
| N-12 | S | The app SHALL be structured so the model file can be updated (e.g. a phone-photo-tuned successor) without a data migration. |

### Quality bar (verifiable against the current model — see `docs/performance.md`)
| ID | Pri | Requirement |
|---|---|---|
| N-20 | M | Field-parse success on valid lesion photos SHALL be 100% (six fields present). |
| N-21 | M | Disease-name leakage in shown output SHALL be zero (enforced by S-03). |
| N-22 | S | Description consistency: identical input SHALL yield identical output (guaranteed by greedy decoding, F-04). |

## 5. Known limitation carried into requirements
The current model was fine-tuned on **dermatoscopic** images; production accuracy
on raw **phone photos** is lower (see `docs/performance.md`). Therefore:
| ID | Pri | Requirement |
|---|---|---|
| L-01 | M | Capture guidance SHALL push users toward well-lit, filled-frame, close-up photos to narrow the domain gap. |
| L-02 | S | The roadmap SHALL include a second fine-tuning pass on phone-camera images (and/or support for a clip-on dermatoscope) before any accuracy claim is made. |

## 6. Traceability
- Product rationale for these requirements: `product.md`.
- UX/architecture realizing them: `design.md`.
- Exact model-call contract for F-03/F-04/F-05: `docs/USING_THE_MODEL.md`.
- Measured quality backing N-20…N-22: `docs/performance.md`, `exports/eval_results.json`.
