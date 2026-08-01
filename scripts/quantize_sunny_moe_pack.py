#!/usr/bin/env python3
"""Convert an FP16 Sunny-MoE expert pack to a Colibri-style mixed pack.

FFN gate/up matrices and text-attention matrices become packed Q4.
By default, the vision and connector matrices remain FP16 because small
perturbations to image features can change every downstream expert route. All
language matrices use Q4. An experimental grouped-Q8 sensitive policy is also
available. Each quantized matrix keeps its original name and has an F32
`<name>.qs` scale tensor. Norms, biases, and routers remain floating. The
output manifest records original shapes so a mobile runtime can map blocks
without loading tensor payloads.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


def quantize_int4_grouped(weights, group_size: int = 128):
    import numpy as np

    if weights.ndim != 2:
        raise ValueError("grouped Q4 requires a 2-D matrix")
    output_features, input_features = weights.shape
    groups = (input_features + group_size - 1) // group_size
    padded_inputs = groups * group_size
    padded = np.zeros((output_features, padded_inputs), dtype=np.float32)
    padded[:, :input_features] = weights.astype(np.float32, copy=False)
    grouped = padded.reshape(output_features, groups, group_size)
    scales = np.maximum(np.abs(grouped).max(axis=2, keepdims=True) / 7.0, 1e-8)
    quantized = np.clip(np.rint(grouped / scales), -8, 7).astype(np.int8)
    quantized = quantized.reshape(output_features, padded_inputs)[:, :input_features]

    packed = np.zeros((output_features, (input_features + 1) // 2), dtype=np.uint8)
    low = (quantized[:, 0::2].astype(np.int16) + 8).astype(np.uint8)
    packed[:, :low.shape[1]] = low
    if input_features > 1:
        high = (quantized[:, 1::2].astype(np.int16) + 8).astype(np.uint8)
        packed[:, :high.shape[1]] |= high << 4
    return packed, scales[:, :, 0].astype(np.float32).reshape(-1)


def dequantize_int4_grouped(packed, scales, input_features: int, group_size: int = 128):
    import numpy as np

    output_features = packed.shape[0]
    groups = (input_features + group_size - 1) // group_size
    values = np.empty((output_features, input_features), dtype=np.float32)
    values[:, 0::2] = (packed & 0x0F).astype(np.int16)[:, :values[:, 0::2].shape[1]] - 8
    if input_features > 1:
        values[:, 1::2] = ((packed >> 4) & 0x0F).astype(np.int16)[
            :, :values[:, 1::2].shape[1]
        ] - 8
    scale_matrix = scales.reshape(output_features, groups)
    repeated = np.repeat(scale_matrix, group_size, axis=1)[:, :input_features]
    return values * repeated


def quantize_int8_grouped(weights, group_size: int = 128):
    import numpy as np

    if weights.ndim != 2:
        raise ValueError("grouped Q8 requires a 2-D matrix")
    output_features, input_features = weights.shape
    groups = (input_features + group_size - 1) // group_size
    padded_inputs = groups * group_size
    padded = np.zeros((output_features, padded_inputs), dtype=np.float32)
    padded[:, :input_features] = weights.astype(np.float32, copy=False)
    grouped = padded.reshape(output_features, groups, group_size)
    scales = np.maximum(np.abs(grouped).max(axis=2, keepdims=True) / 127.0, 1e-8)
    quantized = np.clip(np.rint(grouped / scales), -127, 127).astype(np.int8)
    quantized = quantized.reshape(output_features, padded_inputs)[:, :input_features]
    return quantized, scales[:, :, 0].astype(np.float32).reshape(-1)


def dequantize_int8_grouped(quantized, scales, input_features: int, group_size: int = 128):
    import numpy as np

    output_features = quantized.shape[0]
    groups = (input_features + group_size - 1) // group_size
    scale_matrix = scales.reshape(output_features, groups)
    repeated = np.repeat(scale_matrix, group_size, axis=1)[:, :input_features]
    return quantized.astype(np.float32) * repeated


def is_sensitive_q8_tensor(name: str) -> bool:
    """Keep the perception and token-routing path at eight bits."""
    return any(
        marker in name
        for marker in (
            "model.vision_model.",
            "model.connector.",
            ".down_proj.weight",
            "embed_tokens.weight",
            "lm_head.weight",
        )
    )


def is_perception_tensor(name: str) -> bool:
    return "model.vision_model." in name or "model.connector." in name


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def quantize_file(
    source: Path,
    target: Path,
    *,
    group_size: int,
    q8_sensitive: bool = False,
    fp16_perception: bool = False,
) -> tuple[dict, dict]:
    import numpy as np
    from safetensors import safe_open
    from safetensors.numpy import save_file

    output = {}
    tensor_metadata = {}
    total_elements = 0
    q4_elements = 0
    q8_elements = 0
    fp16_elements = 0
    weighted_relative_error = 0.0
    worst_relative_error = 0.0
    with safe_open(str(source), framework="np") as handle:
        for name in handle.keys():
            tensor = handle.get_tensor(name)
            is_matrix = tensor.ndim == 2 and np.issubdtype(tensor.dtype, np.floating)
            # Router logits and tiny matrices are precision-sensitive and save
            # little space, so leave them floating.
            should_quantize = is_matrix and "router" not in name and tensor.size >= 4096
            if not should_quantize:
                output[name] = (
                    tensor.astype(np.float16)
                    if np.issubdtype(tensor.dtype, np.floating) and "router" not in name
                    else tensor
                )
                tensor_metadata[name] = {
                    "shape": list(tensor.shape),
                    "storage": str(output[name].dtype),
                }
                continue

            original = tensor.astype(np.float32)
            if fp16_perception and is_perception_tensor(name):
                output[name] = tensor.astype(np.float16)
                fp16_elements += original.size
                tensor_metadata[name] = {
                    "shape": list(original.shape),
                    "storage": "float16",
                    "precision_reason": "route-sensitive-perception-path",
                }
                continue
            use_q8 = q8_sensitive and is_sensitive_q8_tensor(name)
            if use_q8:
                quantized, scales = quantize_int8_grouped(original, group_size)
                restored = dequantize_int8_grouped(
                    quantized, scales, original.shape[1], group_size
                )
                storage = "q8_grouped"
                q8_elements += original.size
            else:
                quantized, scales = quantize_int4_grouped(original, group_size)
                restored = dequantize_int4_grouped(
                    quantized, scales, original.shape[1], group_size
                )
                storage = "q4_grouped"
                q4_elements += original.size
            output[name] = quantized
            output[f"{name}.qs"] = scales
            denominator = float(np.abs(original).mean()) + 1e-12
            relative_error = float(np.abs(restored - original).mean()) / denominator
            total_elements += original.size
            weighted_relative_error += relative_error * original.size
            worst_relative_error = max(worst_relative_error, relative_error)
            tensor_metadata[name] = {
                "shape": list(original.shape),
                "storage": storage,
                "scale_tensor": f"{name}.qs",
                "group_size": group_size,
                "mean_relative_error": relative_error,
            }

    save_file(output, str(target), metadata={"format": "sunny-moe-q4-v1"})
    metrics = {
        "quantized_elements": total_elements,
        "q4_elements": q4_elements,
        "q8_elements": q8_elements,
        "fp16_elements": fp16_elements,
        "weighted_mean_relative_error": (
            weighted_relative_error / total_elements if total_elements else 0.0
        ),
        "worst_tensor_relative_error": worst_relative_error,
    }
    return tensor_metadata, metrics


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--group-size", type=int, default=128)
    parser.add_argument(
        "--sensitive-precision",
        choices=("fp16-perception", "q8", "none"),
        default="fp16-perception",
        help="precision policy for route-sensitive tensors",
    )
    args = parser.parse_args()
    if args.group_size <= 0 or args.group_size % 2:
        parser.error("--group-size must be a positive even integer")

    source = Path(args.input)
    target = Path(args.out)
    target.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))

    files = [entry["file"] for entry in manifest["dense_shards"]]
    files.extend(entry["file"] for entry in manifest["expert_files"])
    # Routers stay F32 and can be copied directly.
    router_name = manifest["router_file"]["file"]
    shutil.copy2(source / router_name, target / router_name)

    quantized_files = {}
    aggregate_elements = 0
    aggregate_error = 0.0
    worst_error = 0.0
    for index, file_name in enumerate(files, start=1):
        print(f"[quantize] {index}/{len(files)} {file_name}")
        tensor_metadata, metrics = quantize_file(
            source / file_name,
            target / file_name,
            group_size=args.group_size,
            q8_sensitive=args.sensitive_precision == "q8",
            fp16_perception=args.sensitive_precision == "fp16-perception",
        )
        output_path = target / file_name
        quantized_files[file_name] = {
            "bytes": output_path.stat().st_size,
            "sha256": sha256(output_path),
            "tensors": tensor_metadata,
            "metrics": metrics,
        }
        elements = metrics["quantized_elements"]
        aggregate_elements += elements
        aggregate_error += metrics["weighted_mean_relative_error"] * elements
        worst_error = max(worst_error, metrics["worst_tensor_relative_error"])

    for path in source.iterdir():
        if path.is_file() and path.name not in set(files) | {router_name, "manifest.json"}:
            shutil.copy2(path, target / path.name)

    router_path = target / router_name
    q4_manifest = dict(manifest)
    formats = {
        "fp16-perception": "sunny-moe-expert-pack-mixed-q4-fp16-v1",
        "q8": "sunny-moe-expert-pack-mixed-q4-q8-v1",
        "none": "sunny-moe-expert-pack-q4-v1",
    }
    dtypes = {
        "fp16-perception": "grouped-q4-fp16-perception",
        "q8": "grouped-q4-q8-mixed",
        "none": "grouped-q4-mixed",
    }
    q4_manifest["format"] = formats[args.sensitive_precision]
    q4_manifest["dtype"] = dtypes[args.sensitive_precision]
    for entry in q4_manifest["dense_shards"]:
        converted = quantized_files[entry["file"]]
        entry["bytes"] = converted["bytes"]
        entry["sha256"] = converted["sha256"]
    for entry in q4_manifest["expert_files"]:
        converted = quantized_files[entry["file"]]
        entry["bytes"] = converted["bytes"]
        entry["sha256"] = converted["sha256"]
    q4_manifest["quantization"] = {
        "bits": {
            "fp16-perception": "4/16",
            "q8": "4/8",
            "none": 4,
        }[args.sensitive_precision],
        "group_size": args.group_size,
        "sensitive_precision": args.sensitive_precision,
        "q4_elements": sum(
            entry["metrics"]["q4_elements"] for entry in quantized_files.values()
        ),
        "q8_elements": sum(
            entry["metrics"]["q8_elements"] for entry in quantized_files.values()
        ),
        "fp16_elements": sum(
            entry["metrics"]["fp16_elements"] for entry in quantized_files.values()
        ),
        "files": quantized_files,
        "weighted_mean_relative_error": (
            aggregate_error / aggregate_elements if aggregate_elements else 0.0
        ),
        "worst_tensor_relative_error": worst_error,
    }
    q4_manifest["router_file"] = {
        "file": router_name,
        "bytes": router_path.stat().st_size,
        "sha256": sha256(router_path),
    }
    q4_manifest["total_bytes"] = router_path.stat().st_size + sum(
        entry["bytes"] for entry in quantized_files.values()
    )
    q4_manifest["source_fp_bytes"] = manifest["total_bytes"]
    q4_manifest["compression_ratio"] = (
        manifest["total_bytes"] / q4_manifest["total_bytes"]
        if q4_manifest["total_bytes"] else 0.0
    )
    (target / "manifest.json").write_text(
        json.dumps(q4_manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(
        f"[done] {target}: {q4_manifest['total_bytes'] / 1e9:.2f} GB, "
        f"{q4_manifest['compression_ratio']:.2f}x smaller, "
        f"mean relative error={q4_manifest['quantization']['weighted_mean_relative_error']:.4f}"
    )


if __name__ == "__main__":
    main()
