#!/usr/bin/env python3
"""Create a fixed-resolution SmolVLM projector without changing llama.cpp."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import torch
from safetensors import safe_open
from safetensors.torch import save_file


POSITION_KEY = "model.vision_model.embeddings.position_embedding.weight"
WEIGHT_PREFIXES = ("model.vision_model.", "model.connector.")


def read_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value: dict) -> None:
    with path.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def copy_metadata(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    for path in source.iterdir():
        if path.name.endswith(".safetensors") or path.name.endswith(".safetensors.index.json"):
            continue
        target = destination / path.name
        if path.is_dir():
            shutil.copytree(path, target, dirs_exist_ok=True)
        else:
            shutil.copy2(path, target)


def bucket_indices(source_side: int, target_side: int) -> torch.Tensor:
    coordinates = torch.arange(target_side, dtype=torch.int64)
    buckets = torch.div(coordinates * source_side, target_side, rounding_mode="floor")
    return (buckets[:, None] * source_side + buckets[None, :]).reshape(-1)


def load_projector_tensors(source: Path, image_size: int, patch_size: int) -> dict[str, torch.Tensor]:
    files = sorted(source.glob("*.safetensors"))
    if not files:
        raise FileNotFoundError(f"No safetensors files found in {source}")

    tensors: dict[str, torch.Tensor] = {}
    for path in files:
        with safe_open(path, framework="pt", device="cpu") as handle:
            for key in handle.keys():
                if key.startswith(WEIGHT_PREFIXES):
                    tensors[key] = handle.get_tensor(key)

    positions = tensors.get(POSITION_KEY)
    if positions is None:
        raise KeyError(f"Missing {POSITION_KEY}")
    source_side = int(positions.shape[0] ** 0.5)
    if source_side * source_side != positions.shape[0]:
        raise ValueError("Position embedding count is not a square")
    target_side = image_size // patch_size
    indices = bucket_indices(source_side, target_side)
    tensors[POSITION_KEY] = positions.index_select(0, indices).contiguous()
    return tensors


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--image-size", type=int, default=256)
    args = parser.parse_args()

    config = read_json(args.source / "config.json")
    vision = config["vision_config"]
    patch_size = int(vision["patch_size"])
    scale_factor = int(config["scale_factor"])
    if args.image_size <= 0 or args.image_size % (patch_size * scale_factor) != 0:
        raise ValueError("image size must be divisible by patch_size * scale_factor")

    copy_metadata(args.source, args.destination)
    vision["image_size"] = args.image_size
    vision["max_image_size"] = {"longest_edge": args.image_size}
    write_json(args.destination / "config.json", config)

    processor_path = args.destination / "processor_config.json"
    if processor_path.is_file():
        processor = read_json(processor_path)
        processor["image_processor"]["max_image_size"] = {"longest_edge": args.image_size}
        patches = args.image_size // patch_size
        processor["image_seq_len"] = patches * patches // (scale_factor * scale_factor)
        write_json(processor_path, processor)

    tensors = load_projector_tensors(args.source, args.image_size, patch_size)
    save_file(tensors, args.destination / "model.safetensors")
    print(
        f"Wrote {len(tensors)} projector tensors for {args.image_size}px "
        f"to {args.destination}"
    )


if __name__ == "__main__":
    main()
