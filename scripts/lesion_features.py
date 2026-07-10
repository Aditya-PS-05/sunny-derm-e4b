#!/usr/bin/env python3
"""
lesion_features.py — measured, descriptive image features for a skin-lesion photo.

These are the GROUNDING layer of the hybrid labeler: a vision model drafts the
prose, and these measured features verify/correct its claims about colour,
symmetry, and border before a description is accepted. Everything here is
*descriptive morphology* (what the pixels show) — NOT a diagnosis and NOT an
ABCD malignancy score. We deliberately avoid outputting any benign/malignant call.

Public API:
    extract_features(path) -> dict   # measured fields + human phrases
    describe_fields(feat)  -> dict   # schema-shaped descriptive strings

Robust to dermatoscopic images (circular vignette, immersion bubbles, hair):
the segmentation ignores dark corners and thin dark hair-like structures.
"""
import numpy as np


# ---- colour vocabulary (dermatology-descriptive, not diagnostic) ----------
# Reference colours a describer commonly uses for pigmented/erythematous lesions.
COLOR_REFS = {
    "light brown":  (170, 120, 90),
    "dark brown":   (90, 60, 45),
    "black":        (40, 30, 30),
    "tan":          (200, 165, 130),
    "pink":         (220, 150, 150),
    "red":          (180, 60, 60),
    "purple":       (120, 70, 110),
    "blue-grey":    (110, 120, 140),
    "white":        (225, 220, 215),
    "skin-toned":   (215, 180, 160),
}


def _load_rgb(path, max_side=512):
    import cv2
    bgr = cv2.imread(path, cv2.IMREAD_COLOR)
    if bgr is None:
        from PIL import Image
        bgr = cv2.cvtColor(np.array(Image.open(path).convert("RGB")), cv2.COLOR_RGB2BGR)
    h, w = bgr.shape[:2]
    if max(h, w) > max_side:
        s = max_side / max(h, w)
        bgr = cv2.resize(bgr, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)


def _vignette_mask(rgb):
    """True where the image is *inside* the usable field (excludes dark dermatoscope corners)."""
    import cv2
    g = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    # dark ring/corners: very low luminance near the border
    valid = g > 25
    # keep the largest bright connected region as the field of view
    n, lab, stats, _ = cv2.connectedComponentsWithStats(valid.astype(np.uint8), 8)
    if n <= 1:
        return np.ones(g.shape, bool)
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    return lab == biggest


def _segment_lesion(rgb, fov):
    """Segment the lesion vs surrounding skin inside the field of view.
    Lesion pixels are those whose colour departs from the median skin colour."""
    import cv2
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB).astype(np.float32)
    skin_med = np.median(lab[fov], axis=0)
    dist = np.linalg.norm(lab - skin_med, axis=2)
    d = dist.copy(); d[~fov] = 0
    # Otsu on the in-FOV distance
    dn = np.clip(d / (d[fov].max() + 1e-6) * 255, 0, 255).astype(np.uint8)
    thr, _ = cv2.threshold(dn[fov].reshape(-1, 1), 0, 255,
                           cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = (dn >= thr) & fov
    # morphological cleanup + remove hair-like thin structures (open) then close holes
    mask = mask.astype(np.uint8)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, k)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE,
                            cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    # keep the largest central component
    n, labimg, stats, cent = cv2.connectedComponentsWithStats(mask, 8)
    if n <= 1:
        return mask.astype(bool)
    H, W = mask.shape
    # score components by area and centrality
    best, best_score = 1, -1
    for i in range(1, n):
        area = stats[i, cv2.CC_STAT_AREA]
        cy, cx = cent[i][1], cent[i][0]
        centrality = 1.0 - (abs(cx - W / 2) / (W / 2) + abs(cy - H / 2) / (H / 2)) / 2
        score = area * (0.5 + 0.5 * centrality)
        if score > best_score:
            best, best_score = i, score
    return labimg == best


def _nearest_colors(rgb, mask, topk=3):
    px = rgb[mask].astype(np.float32)
    if len(px) < 20:
        return []
    names = list(COLOR_REFS)
    refs = np.array([COLOR_REFS[n] for n in names], np.float32)
    d = np.linalg.norm(px[:, None, :] - refs[None, :, :], axis=2)
    assign = d.argmin(1)
    counts = np.bincount(assign, minlength=len(names)).astype(float)
    frac = counts / counts.sum()
    order = np.argsort(frac)[::-1]
    out = [(names[i], float(frac[i])) for i in order if frac[i] >= 0.08][:topk]
    return out


def _asymmetry(mask):
    import cv2
    ys, xs = np.where(mask)
    if len(xs) < 30:
        return None
    m = cv2.moments(mask.astype(np.uint8))
    if m["m00"] == 0:
        return None
    cx, cy = m["m10"] / m["m00"], m["m01"] / m["m00"]
    # principal axis angle
    mu20, mu02, mu11 = m["mu20"], m["mu02"], m["mu11"]
    theta = 0.5 * np.arctan2(2 * mu11, (mu20 - mu02))
    H, W = mask.shape
    M = cv2.getRotationMatrix2D((cx, cy), np.degrees(theta), 1.0)
    rot = cv2.warpAffine(mask.astype(np.uint8), M, (W, H))
    ys2, xs2 = np.where(rot)
    if len(xs2) < 30:
        return None
    x0, x1, y0, y1 = xs2.min(), xs2.max(), ys2.min(), ys2.max()
    crop = rot[y0:y1 + 1, x0:x1 + 1]
    # symmetry error along each axis (fraction of mismatched pixels)
    fh = np.flip(crop, 1)
    fv = np.flip(crop, 0)
    a_h = (crop != fh).sum() / (crop.sum() + 1e-6)
    a_v = (crop != fv).sum() / (crop.sum() + 1e-6)
    return float(min(a_h, a_v))  # best-axis asymmetry, 0=symmetric


def _border_irregularity(mask):
    """Compactness-based border irregularity: 1 - (4*pi*A)/P^2. 0=circle, →1 ragged."""
    import cv2
    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL,
                               cv2.CHAIN_APPROX_NONE)
    if not cnts:
        return None
    c = max(cnts, key=cv2.contourArea)
    A = cv2.contourArea(c)
    P = cv2.arcLength(c, True)
    if P == 0 or A == 0:
        return None
    circ = 4 * np.pi * A / (P * P)
    return float(np.clip(1 - circ, 0, 1))


def _texture(rgb, mask):
    """Simple texture proxies: luminance std and edge density within the lesion."""
    import cv2
    g = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    vals = g[mask].astype(np.float32)
    if len(vals) < 30:
        return None, None
    lum_std = float(vals.std())
    edges = cv2.Canny(g, 50, 150)
    edge_density = float((edges[mask] > 0).mean())
    return lum_std, edge_density


def extract_features(path):
    rgb = _load_rgb(path)
    fov = _vignette_mask(rgb)
    mask = _segment_lesion(rgb, fov)
    area_frac = float(mask.sum() / max(fov.sum(), 1))
    colors = _nearest_colors(rgb, mask)
    asym = _asymmetry(mask)
    border = _border_irregularity(mask)
    lum_std, edge_density = _texture(rgb, mask)
    return {
        "area_frac": round(area_frac, 4),
        "n_colors": len(colors),
        "colors": colors,                      # [(name, frac), ...]
        "asymmetry": None if asym is None else round(asym, 4),
        "border_irregularity": None if border is None else round(border, 4),
        "lum_std": None if lum_std is None else round(lum_std, 2),
        "edge_density": None if edge_density is None else round(edge_density, 4),
    }


# ---- turn measured features into descriptive phrases ----------------------
# Thresholds calibrated to the empirical tertiles of the HAM10000 pilot
# (dermatoscopic segmentation inflates absolute morphometry, so phrases are
# relative-within-dataset, set at the 33rd/66th percentiles). See
# data/feature_thresholds.json for the fitted values and refit_thresholds().
THRESH = {
    "asymmetry": (0.31, 0.52),
    "border_irregularity": (0.55, 0.77),
    "lum_std": (13.3, 19.6),
    "edge_density": (0.009, 0.052),
}


def _phrase_symmetry(asym):
    if asym is None:
        return "symmetry not assessable"
    lo, hi = THRESH["asymmetry"]
    if asym < lo:
        return "roughly symmetric"
    if asym < hi:
        return "mildly asymmetric"
    return "notably asymmetric"


def _phrase_border(b):
    if b is None:
        return "borders not assessable"
    lo, hi = THRESH["border_irregularity"]
    if b < lo:
        return "smooth, well-defined borders"
    if b < hi:
        return "somewhat irregular borders"
    return "ragged, poorly-defined borders"


def _phrase_colors(colors):
    if not colors:
        return "colour not assessable"
    names = [c[0] for c in colors]
    if len(names) == 1:
        return f"uniform {names[0]} colour"
    return f"multiple colours ({', '.join(names)})"


def _phrase_texture(lum_std, edge_density):
    if lum_std is None:
        return "texture not assessable"
    lo, hi = THRESH["lum_std"]
    e_hi = THRESH["edge_density"][1]
    if lum_std < lo and (edge_density or 0) < e_hi:
        return "smooth, even surface"
    if lum_std < hi:
        return "slightly uneven surface"
    return "rough or structurally varied surface"


def describe_fields(feat):
    """Schema-shaped descriptive phrases derived from measured features."""
    return {
        "colour": _phrase_colors(feat["colors"]),
        "symmetry": _phrase_symmetry(feat["asymmetry"]),
        "borders": _phrase_border(feat["border_irregularity"]),
        "texture": _phrase_texture(feat["lum_std"], feat["edge_density"]),
    }


if __name__ == "__main__":
    import sys, json
    for p in sys.argv[1:]:
        f = extract_features(p)
        print(p)
        print("  measured:", json.dumps(f))
        print("  described:", json.dumps(describe_fields(f)))
