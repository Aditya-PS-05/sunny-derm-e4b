#!/usr/bin/env python3
"""Install calibrated routers and optional correction LoRA into an expert pack."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument(
        "--include-corrections",
        action="store_true",
        help="store non-router checkpoint tensors as an FP16 correction adapter",
    )
    args = parser.parse_args()

    from safetensors import safe_open
    from safetensors.torch import load_file, save_file
    from sunny_moe.checkpoint import WEIGHTS_FILE

    source = Path(args.pack)
    checkpoint = Path(args.checkpoint)
    target = Path(args.out)
    if target.exists():
        raise FileExistsError(f"refusing to overwrite {target}")
    shutil.copytree(source, target)

    manifest_path = target / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    router_name = manifest["router_file"]["file"]
    router_path = target / router_name
    with safe_open(str(source / router_name), framework="pt", device="cpu") as handle:
        expected = set(handle.keys())
    checkpoint_state = load_file(str(checkpoint / WEIGHTS_FILE), device="cpu")
    routers = {
        name: tensor.detach().cpu().float().contiguous()
        for name, tensor in checkpoint_state.items()
        if name.endswith(".router.weight")
    }
    if set(routers) != expected:
        raise RuntimeError(
            f"router mismatch: missing={sorted(expected - set(routers))[:5]} "
            f"unexpected={sorted(set(routers) - expected)[:5]}"
        )
    previous_bytes = int(manifest["router_file"]["bytes"])
    save_file(routers, str(router_path), metadata={
        "tier": "router",
        "format": "sunny-moe-calibrated-v1",
    })
    current_bytes = router_path.stat().st_size
    manifest["router_file"] = {
        "file": router_name,
        "bytes": current_bytes,
        "sha256": sha256(router_path),
    }
    manifest["total_bytes"] = int(manifest["total_bytes"]) - previous_bytes + current_bytes
    correction_bytes = 0
    if args.include_corrections:
        corrections = {
            name: tensor.detach().cpu().half().contiguous()
            for name, tensor in checkpoint_state.items()
            if not name.endswith(".router.weight")
        }
        if not corrections:
            raise RuntimeError("--include-corrections requested but checkpoint has no adapters")
        correction_name = "correction-adapters.safetensors"
        correction_path = target / correction_name
        save_file(corrections, str(correction_path), metadata={
            "tier": "correction-adapter",
            "format": "sunny-moe-fp16-lora-v1",
        })
        correction_bytes = correction_path.stat().st_size
        manifest["correction_adapter_file"] = {
            "file": correction_name,
            "bytes": correction_bytes,
            "sha256": sha256(correction_path),
            "dtype": "float16",
            "tensors": len(corrections),
        }
        manifest["total_bytes"] += correction_bytes
    manifest["router_calibration"] = {
        "checkpoint": checkpoint.name,
        "method": (
            "task-loss-plus-load-balancing-with-fp16-correction-lora"
            if args.include_corrections
            else "task-loss-plus-load-balancing-on-dequantized-deployment-weights"
        ),
    }
    source_fp_bytes = int(manifest.get("source_fp_bytes", 0))
    if source_fp_bytes:
        manifest["compression_ratio"] = source_fp_bytes / manifest["total_bytes"]
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(
        f"[done] calibrated pack -> {target}; {manifest['total_bytes'] / 1e9:.2f} GB"
        + (f" including {correction_bytes / 1e6:.1f} MB corrections" if correction_bytes else "")
    )


if __name__ == "__main__":
    main()
