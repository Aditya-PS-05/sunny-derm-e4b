"""Correctness loader for Colibri-style grouped-Q4 Sunny-MoE packs."""

from __future__ import annotations

import json
from pathlib import Path

import torch
from torch import nn


def expert_pack_name_to_model_name(name: str) -> str:
    """Map merged deploy expert names back to the training module's base weight."""
    marker = ".experts."
    if marker not in name:
        return name
    prefix, suffix = name.split(marker, 1)
    expert, remainder = suffix.split(".", 1)
    if remainder.startswith(("gate_proj.", "up_proj.", "down_proj.")):
        return f"{prefix}{marker}{expert}.base.{remainder}"
    return name


def dequantize_grouped_q4_torch(
    packed: torch.Tensor,
    scales: torch.Tensor,
    *,
    output_features: int,
    input_features: int,
    group_size: int,
    dtype: torch.dtype,
) -> torch.Tensor:
    groups = (input_features + group_size - 1) // group_size
    values = torch.empty((output_features, input_features), dtype=torch.float32)
    low_columns = (input_features + 1) // 2
    high_columns = input_features // 2
    values[:, 0::2] = (packed[:, :low_columns] & 0x0F).to(torch.int16).float() - 8
    if high_columns:
        values[:, 1::2] = (
            ((packed[:, :high_columns] >> 4) & 0x0F).to(torch.int16).float() - 8
        )
    repeated_scales = scales.reshape(output_features, groups).repeat_interleave(
        group_size, dim=1
    )[:, :input_features]
    return (values * repeated_scales).to(dtype=dtype)


def dequantize_grouped_q8_torch(
    quantized: torch.Tensor,
    scales: torch.Tensor,
    *,
    output_features: int,
    input_features: int,
    group_size: int,
    dtype: torch.dtype,
) -> torch.Tensor:
    groups = (input_features + group_size - 1) // group_size
    repeated_scales = scales.reshape(output_features, groups).repeat_interleave(
        group_size, dim=1
    )[:, :input_features]
    return (quantized.float() * repeated_scales).to(dtype=dtype)


@torch.no_grad()
def load_q4_pack(model: nn.Module, pack_dir: str) -> dict:
    """Load all dense, router, and merged expert weights into an upcycled model.

    This is deliberately a correctness oracle, not the phone runtime: weights
    are dequantized into the model's resident dtype. The C/ARM runtime will
    multiply packed Q4 directly and retain only selected experts.
    """
    from safetensors import safe_open

    source = Path(pack_dir)
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    supported_formats = {
        "sunny-moe-expert-pack-q4-v1",
        "sunny-moe-expert-pack-mixed-q4-q8-v1",
        "sunny-moe-expert-pack-mixed-q4-fp16-v1",
    }
    if manifest.get("format") not in supported_formats:
        raise ValueError("expected a supported Sunny-MoE quantized manifest")
    target_state = model.state_dict()
    loaded: set[str] = set()

    quant_files = manifest["quantization"]["files"]
    file_names = [entry["file"] for entry in manifest["dense_shards"]]
    file_names.extend(entry["file"] for entry in manifest["expert_files"])
    file_names.append(manifest["router_file"]["file"])
    correction_entry = manifest.get("correction_adapter_file")
    if correction_entry:
        file_names.append(correction_entry["file"])

    for file_name in file_names:
        metadata = quant_files.get(file_name, {}).get("tensors", {})
        with safe_open(str(source / file_name), framework="pt", device="cpu") as handle:
            keys = set(handle.keys())
            for packed_name in sorted(keys):
                if packed_name.endswith(".qs"):
                    continue
                model_name = expert_pack_name_to_model_name(packed_name)
                if model_name not in target_state and packed_name.endswith((".weight", ".bias")):
                    stem, suffix = packed_name.rsplit(".", 1)
                    shared_candidate = f"{stem}.base.{suffix}"
                    if shared_candidate in target_state:
                        model_name = shared_candidate
                if model_name not in target_state:
                    raise KeyError(f"pack tensor has no model target: {packed_name} -> {model_name}")
                info = metadata.get(packed_name, {})
                tensor = handle.get_tensor(packed_name)
                if info.get("storage") == "q4_grouped":
                    scale_name = info["scale_tensor"]
                    if scale_name not in keys:
                        raise KeyError(f"missing Q4 scale tensor {scale_name}")
                    shape = info["shape"]
                    tensor = dequantize_grouped_q4_torch(
                        tensor,
                        handle.get_tensor(scale_name),
                        output_features=int(shape[0]),
                        input_features=int(shape[1]),
                        group_size=int(info["group_size"]),
                        dtype=target_state[model_name].dtype,
                    )
                elif info.get("storage") == "q8_grouped":
                    scale_name = info["scale_tensor"]
                    if scale_name not in keys:
                        raise KeyError(f"missing Q8 scale tensor {scale_name}")
                    shape = info["shape"]
                    tensor = dequantize_grouped_q8_torch(
                        tensor,
                        handle.get_tensor(scale_name),
                        output_features=int(shape[0]),
                        input_features=int(shape[1]),
                        group_size=int(info["group_size"]),
                        dtype=target_state[model_name].dtype,
                    )
                else:
                    tensor = tensor.to(dtype=target_state[model_name].dtype)
                if tuple(tensor.shape) != tuple(target_state[model_name].shape):
                    raise ValueError(
                        f"shape mismatch for {model_name}: pack={tuple(tensor.shape)} "
                        f"model={tuple(target_state[model_name].shape)}"
                    )
                target_state[model_name].copy_(tensor)
                loaded.add(model_name)

    expected = {
        name for name in target_state
        if "_delta." not in name and ".delta." not in name
        and not name.endswith(".num_batches_tracked")
    }
    missing = sorted(expected - loaded)
    if missing:
        raise RuntimeError(f"Q4 pack did not cover model tensors: {missing[:10]}")
    return {
        "loaded_tensors": len(loaded),
        "manifest": manifest,
    }
