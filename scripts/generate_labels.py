#!/usr/bin/env python3
"""
generate_labels.py — hybrid teacher labeler.

For each image:
  1) MEASURE morphology with lesion_features.py (colour, symmetry, borders,
     texture) — the grounding layer.
  2) DRAFT prose with a vision teacher (host.llm), constrained to the app's
     schema and to descriptive-only, non-diagnostic language.
  3) VERIFY + CORRECT: the measured phrases OVERRIDE the model's claims for
     Colour / Symmetry / Borders / Texture. The model's free text is kept only
     for 'Lesion Type' wording and the 'Summary', and the Summary is re-checked
     for banned diagnostic language.
  4) FILTER: drop samples whose draft failed the schema or safety checks.

Output schema (matches the Sunny app UI):
    Lesion Type · Colour · Symmetry · Borders · Texture · Summary

Emits train.jsonl / val.jsonl in a chat-with-image format ready for QLoRA:
    {"image": "images/<id>.jpg",
     "messages": [
        {"role":"user","content":[{"type":"image"},{"type":"text","text": PROMPT}]},
        {"role":"assistant","content":[{"type":"text","text": <structured desc>}]}
     ],
     "meta": {...ground-truth dx, measured features...}}

The vision teacher is pluggable: --teacher host  (host.llm, run in the repl tool)
                                  --teacher none  (features-only, deterministic)
This script's default path writes the *measured* fields and a templated Summary
so it runs with no LLM; the repl-driven host.llm drafting is layered on top by
label_with_host_llm() (called from a repl cell, see run_labeling.md).
"""
import argparse, json, os, random, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lesion_features as lf

# ---- the instruction the *student* model will be trained to answer ---------
USER_PROMPT = (
    "You are a dermatology description assistant. Look at this skin lesion photo "
    "and describe what you see. Do NOT diagnose or name a disease. Report only "
    "observable features in this exact format:\n"
    "Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>\n"
    "Colour: <colours present>\n"
    "Symmetry: <symmetric / asymmetric>\n"
    "Borders: <smooth / irregular / well- or poorly-defined>\n"
    "Texture: <smooth / rough / raised / scaly>\n"
    "Summary: <one plain-language sentence describing the lesion's appearance "
    "and reminding the user this is not a diagnosis>"
)

# ---- descriptive lesion-type wording per dx (NOT a diagnosis label) --------
# We map the ground-truth dx to a *morphological* type phrase the describer can
# legitimately say from appearance. This avoids teaching the model to output the
# diagnosis name while still giving a correct visual category.
TYPE_HINT = {
    "melanocytic_Nevi": "pigmented macule or papule",
    "melanoma": "pigmented lesion",
    "benign_keratosis-like_lesions": "keratotic plaque",
    "basal_cell_carcinoma": "raised translucent papule",
    "actinic_keratoses": "rough scaly patch",
    "vascular_lesions": "red or purple vascular papule",
    "dermatofibroma": "firm pigmented papule",
}

# ---- safety: banned diagnostic / alarming language in any output -----------
# Diagnostic disease names + assertive-diagnosis phrasing. NOTE: "diagnosis"
# itself is NOT banned, because the REQUIRED disclaimer says "not a diagnosis".
BANNED = re.compile(
    r"\b(cancer|carcinoma|melanoma|malignan\w*|benign|biopsy|"
    r"tumou?r|metasta\w*|precancer\w*|you have|likely to be|probably (?:is|a))\b",
    re.I,
)
# Approved disclaimer phrases are whitelisted out before the scan so the
# mandatory "not a diagnosis / see a clinician" text never trips the filter.
_DISCLAIMER_OK = re.compile(
    r"not a diagnosis|this is not a diagnosis|see a clinician|consult a clinician|"
    r"not a diagnostic tool", re.I)


def _scan_banned(text):
    """True if text contains genuinely banned diagnostic language (after
    removing the approved disclaimer phrases)."""
    scrubbed = _DISCLAIMER_OK.sub(" ", text)
    return bool(BANNED.search(scrubbed))


def build_description(dx, feat, teacher_summary=None):
    """Assemble the structured target. Measured fields are authoritative."""
    df = lf.describe_fields(feat)
    ltype = TYPE_HINT.get(dx, "skin lesion")
    if teacher_summary and not _scan_banned(teacher_summary):
        summary = teacher_summary.strip()
    else:
        # colour phrase -> a natural fragment: "uniform light brown" or
        # "light brown and red" (from "multiple colours (a, b, c)")
        col = df["colour"]
        if col.startswith("uniform "):
            col_frag = col.replace(" colour", "")           # "uniform light brown"
        elif col.startswith("multiple colours ("):
            names = col[col.find("(") + 1:col.rfind(")")].split(", ")
            col_frag = (" and ".join(names) if len(names) <= 2
                        else ", ".join(names[:-1]) + " and " + names[-1])
        else:
            col_frag = "variably coloured"
        tex_frag = df["texture"].split(", ")[-1]             # e.g. "even surface"
        summary = (
            f"A {col_frag} {ltype} that appears {df['symmetry']} with "
            f"{df['borders']} and a {tex_frag}. This is a visual description "
            f"only, not a diagnosis — see a clinician for any concern."
        )
    # final safety pass: strip if summary still contains banned language
    if _scan_banned(summary):
        summary = ("A visible skin lesion described by appearance only. This is not "
                   "a diagnosis — please consult a clinician for any concern.")
    return {
        "Lesion Type": ltype,
        "Colour": df["colour"],
        "Symmetry": df["symmetry"],
        "Borders": df["borders"],
        "Texture": df["texture"],
        "Summary": summary,
    }


def format_target(desc):
    return (f"Lesion Type: {desc['Lesion Type']}\n"
            f"Colour: {desc['Colour']}\n"
            f"Symmetry: {desc['Symmetry']}\n"
            f"Borders: {desc['Borders']}\n"
            f"Texture: {desc['Texture']}\n"
            f"Summary: {desc['Summary']}")


def qa_checks(feat, desc):
    """Return list of failure reasons ([] == passed)."""
    fails = []
    if feat["area_frac"] < 0.005:
        fails.append("no_lesion_segmented")
    if feat["area_frac"] > 0.85:
        fails.append("segmentation_covers_whole_image")
    if _scan_banned(format_target(desc)):
        fails.append("banned_language")
    if len(desc["Summary"].split()) < 6:
        fails.append("summary_too_short")
    return fails


def build_record(img_rel, dx, feat, teacher_summary=None):
    desc = build_description(dx, feat, teacher_summary)
    fails = qa_checks(feat, desc)
    rec = {
        "image": img_rel,
        "messages": [
            {"role": "user",
             "content": [{"type": "image"}, {"type": "text", "text": USER_PROMPT}]},
            {"role": "assistant",
             "content": [{"type": "text", "text": format_target(desc)}]},
        ],
        "meta": {"dx": dx, "features": feat, "qa_fails": fails},
    }
    return rec, fails


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-root", required=True,
                    help="dir that image_path in the manifest is relative to")
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--summaries", default=None,
                    help="optional JSON {image_id: teacher_summary} from host.llm")
    ap.add_argument("--val-frac", type=float, default=0.12)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    import pandas as pd
    random.seed(args.seed)
    os.makedirs(args.out_dir, exist_ok=True)
    m = pd.read_csv(args.manifest)
    # Some mirrors repeat the same ISIC image across source shards/splits. Keep
    # one physical image so duplicates cannot overweight training or validation.
    m = m.drop_duplicates(subset=["image_id"], keep="first").reset_index(drop=True)
    summaries = {}
    if args.summaries and os.path.exists(args.summaries):
        summaries = json.load(open(args.summaries))

    kept, dropped = [], []
    for _, r in m.iterrows():
        p = os.path.join(args.images_root, r["image_path"])
        if not os.path.exists(p):
            dropped.append((r["image_id"], "missing_file")); continue
        try:
            feat = lf.extract_features(p)
        except Exception as e:
            dropped.append((r["image_id"], f"feat_err:{e}")); continue
        rec, fails = build_record(r["image_path"], r["dx"], feat,
                                  summaries.get(str(r["image_id"])))
        if fails:
            dropped.append((r["image_id"], ",".join(fails))); continue
        # All images of one HAM10000 lesion must remain in the same split.
        # Otherwise near-duplicate views can make validation look artificially good.
        rec["meta"]["lesion_id"] = str(r["lesion_id"])
        kept.append(rec)

    # Group by lesion within each class, then select whole groups for validation.
    # Per-class grouping keeps the small held-out set representative of all seven
    # visual categories while guaranteeing zero lesion identity overlap.
    by_class = {}
    for rec in kept:
        by_class.setdefault(rec["meta"]["dx"], {}).setdefault(
            rec["meta"]["lesion_id"], [],
        ).append(rec)
    train, val = [], []
    for dx in sorted(by_class):
        groups = list(by_class[dx].values())
        random.shuffle(groups)
        target = max(1, round(sum(len(group) for group in groups) * args.val_frac))
        val_count = 0
        for index, group in enumerate(groups):
            groups_left = len(groups) - index - 1
            if val_count < target and groups_left >= 1:
                val.extend(group)
                val_count += len(group)
            else:
                train.extend(group)
    random.shuffle(train)
    random.shuffle(val)
    for name, split in [("train", train), ("val", val)]:
        with open(os.path.join(args.out_dir, f"{name}.jsonl"), "w") as f:
            for rec in split:
                f.write(json.dumps(rec) + "\n")
    with open(os.path.join(args.out_dir, "dropped.json"), "w") as f:
        json.dump(dropped, f, indent=2)
    print(f"[labels] kept={len(kept)} (train={len(train)} val={len(val)}) "
          f"dropped={len(dropped)}")
    if dropped[:10]:
        print("[labels] sample drops:", dropped[:10])


if __name__ == "__main__":
    main()
