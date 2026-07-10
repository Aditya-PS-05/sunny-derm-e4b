#!/usr/bin/env python3
"""
fetch_images.py — pull a class-balanced dermatology image sample from the
Hugging Face mirror of HAM10000 (marmal88/skin_cancer), weighted toward the
Tier-1 (high-commercial-value) conditions.

Runs ON THE GPU HOST (open internet). Downloads parquet shards directly over
HTTPS (no `datasets` streaming — the fsspec/Xet path is flaky behind proxies),
decodes embedded image bytes to JPEG files, and writes a manifest.csv.

Output:
  <out>/images/<dx>/<image_id>.jpg
  <out>/manifest.csv   columns: image_path, image_id, lesion_id, dx, dx_type,
                                age, sex, localization, split, tier

Usage:
  python fetch_images.py --out ./data --per-class 200 --seed 42
  python fetch_images.py --out ./data --per-class 400 --tier1-only
"""
import argparse, csv, io, os, sys, urllib.request, ssl, random
from collections import defaultdict

REPO = "marmal88/skin_cancer"
BASE = f"https://huggingface.co/datasets/{REPO}/resolve/main"

# Parquet shards in the repo (from the tree listing).
SHARDS = {
    "train": [
        "data/train-00000-of-00005-7eed077f2f8e6d15.parquet",
        "data/train-00001-of-00005-50ba64fd20294ba8.parquet",
        "data/train-00002-of-00005-36c02a25cbdd5481.parquet",
        "data/train-00003-of-00005-27da80cf1cb2598d.parquet",
        "data/train-00004-of-00005-264fb0c337457a9b.parquet",
    ],
    "validation": [
        "data/validation-00000-of-00002-9cc6b2a1db12d1a6.parquet",
        "data/validation-00001-of-00002-900252bc4d7798ec.parquet",
    ],
    "test": ["data/test-00000-of-00001-61e7cf54bf274ae2.parquet"],
}

# Commercial-value tiers (see README). Tier-1 = skin-cancer / sun-damage anxiety.
TIER = {
    "melanoma": 1,
    "basal_cell_carcinoma": 1,
    "actinic_keratoses": 1,
    "melanocytic_Nevi": 1,          # moles — the core "track a mole" use-case
    "benign_keratosis-like_lesions": 2,
    "dermatofibroma": 3,
    "vascular_lesions": 3,
}
# Human-readable class names for downstream prompts / manifest.
CLASS_HUMAN = {
    "melanocytic_Nevi": "melanocytic nevus (mole)",
    "melanoma": "melanoma",
    "benign_keratosis-like_lesions": "benign keratosis",
    "basal_cell_carcinoma": "basal cell carcinoma",
    "actinic_keratoses": "actinic keratosis / Bowen's",
    "vascular_lesions": "vascular lesion",
    "dermatofibroma": "dermatofibroma",
}

CTX = ssl.create_default_context()


def download(path, dst, timeout=600):
    """Ranged download that follows the 302 to the HF CDN (host has open net)."""
    url = f"{BASE}/{path}"
    req = urllib.request.Request(url, headers={"User-Agent": "python-urllib"})
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r, open(dst, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    return os.path.getsize(dst)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="./data")
    ap.add_argument("--per-class", type=int, default=200,
                    help="target images per dx class (capped by availability)")
    ap.add_argument("--tier1-only", action="store_true",
                    help="keep only Tier-1 classes")
    ap.add_argument("--splits", default="train,validation,test")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--jpeg-quality", type=int, default=92)
    ap.add_argument("--max-side", type=int, default=768,
                    help="downscale longest side to this many px (0=keep native)")
    args = ap.parse_args()

    import pandas as pd
    from PIL import Image

    random.seed(args.seed)
    os.makedirs(args.out, exist_ok=True)
    pq_dir = os.path.join(args.out, "raw_parquet")
    img_root = os.path.join(args.out, "images")
    os.makedirs(pq_dir, exist_ok=True)
    os.makedirs(img_root, exist_ok=True)

    want_splits = [s.strip() for s in args.splits.split(",") if s.strip()]

    # 1) Load all requested shards into one frame (metadata + image bytes).
    frames = []
    for split in want_splits:
        for path in SHARDS.get(split, []):
            local = os.path.join(pq_dir, os.path.basename(path))
            if not os.path.exists(local):
                sz = download(path, local)
                print(f"[dl] {path}  ({sz/1e6:.1f} MB)", flush=True)
            df = pd.read_parquet(local)
            df["split"] = split
            frames.append(df)
    full = pd.concat(frames, ignore_index=True)
    print(f"[load] total rows across splits: {len(full)}", flush=True)
    print("[load] raw dx distribution:", dict(full["dx"].value_counts()), flush=True)

    if args.tier1_only:
        full = full[full["dx"].map(lambda d: TIER.get(d, 9)) == 1].copy()

    # 2) Class-balanced sampling weighted toward Tier-1.
    #    Prefer histo-confirmed rows first (most reliable labels), then fill.
    picked_rows = []
    for dx, grp in full.groupby("dx"):
        n_target = args.per_class
        grp = grp.copy()
        # order: histo > consensus > confocal > follow_up (reliability)
        rank = {"histo": 0, "consensus": 1, "confocal": 2, "follow_up": 3}
        grp["_r"] = grp["dx_type"].map(lambda t: rank.get(t, 4))
        grp = grp.sort_values("_r").reset_index(drop=True)
        take = grp.head(min(n_target, len(grp)))
        picked_rows.append(take)
        print(f"[sample] {dx}: took {len(take)}/{len(grp)} "
              f"(tier {TIER.get(dx,'?')})", flush=True)
    sample = pd.concat(picked_rows, ignore_index=True)
    sample = sample.sample(frac=1.0, random_state=args.seed).reset_index(drop=True)

    # 3) Decode image bytes -> JPEG files + manifest.
    manifest_path = os.path.join(args.out, "manifest.csv")
    n_ok, n_fail = 0, 0
    with open(manifest_path, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["image_path", "image_id", "lesion_id", "dx", "dx_human",
                    "dx_type", "age", "sex", "localization", "split", "tier"])
        for _, row in sample.iterrows():
            dx = row["dx"]
            cls_dir = os.path.join(img_root, dx)
            os.makedirs(cls_dir, exist_ok=True)
            img_id = row["image_id"]
            out_jpg = os.path.join(cls_dir, f"{img_id}.jpg")
            try:
                b = row["image"]["bytes"]
                im = Image.open(io.BytesIO(b)).convert("RGB")
                if args.max_side and max(im.size) > args.max_side:
                    im.thumbnail((args.max_side, args.max_side), Image.LANCZOS)
                im.save(out_jpg, "JPEG", quality=args.jpeg_quality)
                n_ok += 1
                w.writerow([
                    os.path.relpath(out_jpg, args.out), img_id, row["lesion_id"],
                    dx, CLASS_HUMAN.get(dx, dx), row["dx_type"], row.get("age"),
                    row.get("sex"), row.get("localization"), row["split"],
                    TIER.get(dx, 9),
                ])
            except Exception as e:
                n_fail += 1
                print(f"[warn] decode failed {img_id}: {e}", flush=True)
    print(f"[done] wrote {n_ok} images ({n_fail} failed) -> {manifest_path}",
          flush=True)


if __name__ == "__main__":
    main()
