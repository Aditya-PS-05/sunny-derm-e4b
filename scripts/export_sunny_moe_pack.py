#!/usr/bin/env python3
"""Materialize a trained Sunny-MoE adapter as a streamable FP16 expert pack.

The output is deliberately expert-addressable: dense/shared tensors are
sharded separately, while every routed layer gets one safetensors file. A
subsequent quantizer can convert these independent blocks to Q4 without first
recombining the model.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def save_tensor_file(state: dict, path: Path, metadata: dict[str, str]) -> dict:
    from safetensors.torch import save_file

    save_file(state, str(path), metadata=metadata)
    return {"file": path.name, "bytes": path.stat().st_size, "sha256": sha256(path)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--dense-shard-gb", type=float, default=1.5)
    parser.add_argument("--dtype", choices=("float16", "bfloat16"), default="float16")
    args = parser.parse_args()

    import torch
    from transformers import AutoModelForImageTextToText, AutoProcessor
    from sunny_moe.checkpoint import CONFIG_FILE, load_adapter_checkpoint
    from sunny_moe.layers import SunnyLoRALinear, SunnySparseMLP
    from sunny_moe.surgery import SunnyMoeSpec, upcycle_model

    checkpoint = Path(args.checkpoint)
    saved_config = json.loads((checkpoint / CONFIG_FILE).read_text(encoding="utf-8"))
    spec = SunnyMoeSpec(**saved_config["spec"])
    dtype = torch.float16 if args.dtype == "float16" else torch.bfloat16

    print(f"[model] loading {spec.seed_model}")
    model = AutoModelForImageTextToText.from_pretrained(
        spec.seed_model, dtype=torch.bfloat16, low_cpu_mem_usage=True
    )
    replaced = upcycle_model(model, spec)
    load_adapter_checkpoint(model, args.checkpoint)
    model.eval()

    target = Path(args.out)
    target.mkdir(parents=True, exist_ok=True)
    sparse_modules = {
        path: module
        for path, module in model.named_modules()
        if isinstance(module, SunnySparseMLP)
    }
    shared_lora_modules = {
        path: module
        for path, module in model.named_modules()
        if isinstance(module, SunnyLoRALinear)
    }
    if set(sparse_modules) != set(replaced):
        raise RuntimeError("upcycled module list changed while preparing export")

    manifest = {
        "format": "sunny-moe-expert-pack-v1",
        "architecture": "smolvlm2-sunny-moe",
        "seed_model": spec.seed_model,
        "dtype": args.dtype,
        "spec": spec.as_dict(),
        "dense_shards": [],
        "router_file": None,
        "expert_files": [],
    }

    # State outside sparse MLPs is the always-resident dense/shared tier.
    sparse_prefixes = tuple(f"{path}." for path in sparse_modules)
    shared_lora_prefixes = tuple(f"{path}." for path in shared_lora_modules)
    max_shard_bytes = int(args.dense_shard_gb * 1_000_000_000)
    shard: dict[str, torch.Tensor] = {}
    shard_bytes = 0
    shard_index = 0

    def flush_dense() -> None:
        nonlocal shard, shard_bytes, shard_index
        if not shard:
            return
        shard_index += 1
        path = target / f"dense-{shard_index:05d}.safetensors"
        manifest["dense_shards"].append(
            save_tensor_file(shard, path, {"tier": "dense", "format": "sunny-moe-v1"})
        )
        shard = {}
        shard_bytes = 0

    for name, tensor in model.state_dict().items():
        if name.startswith(sparse_prefixes) or (
            shared_lora_prefixes and name.startswith(shared_lora_prefixes)
        ):
            continue
        value = tensor.detach().to(device="cpu", dtype=dtype).contiguous()
        value_bytes = value.numel() * value.element_size()
        if shard and shard_bytes + value_bytes > max_shard_bytes:
            flush_dense()
        shard[name] = value
        shard_bytes += value_bytes
    flush_dense()

    # Merge shared LoRA deltas under the seed model's original tensor names.
    for path, module in shared_lora_modules.items():
        value = module.merged_weight().cpu().to(dtype=dtype).contiguous()
        value_bytes = value.numel() * value.element_size()
        if shard and shard_bytes + value_bytes > max_shard_bytes:
            flush_dense()
        shard[f"{path}.weight"] = value
        shard_bytes += value_bytes
        if module.bias is not None:
            bias = module.bias.detach().cpu().to(dtype=dtype).contiguous()
            shard[f"{path}.bias"] = bias
            shard_bytes += bias.numel() * bias.element_size()
    flush_dense()

    routers = {
        f"{path}.router.weight": module.router.weight.detach().cpu().float().contiguous()
        for path, module in sparse_modules.items()
    }
    router_path = target / "routers.safetensors"
    manifest["router_file"] = save_tensor_file(
        routers, router_path, {"tier": "router", "format": "sunny-moe-v1"}
    )

    for path, module in sparse_modules.items():
        layer_index = int(path.split(".layers.", 1)[1].split(".", 1)[0])
        expert_state: dict[str, torch.Tensor] = {}
        for expert_index, expert in enumerate(module.experts):
            for projection, tensor in expert.merged_projection_weights().items():
                key = f"{path}.experts.{expert_index}.{projection}"
                expert_state[key] = tensor.cpu().to(dtype=dtype).contiguous()
        expert_path = target / f"experts-layer-{layer_index:03d}.safetensors"
        entry = save_tensor_file(
            expert_state,
            expert_path,
            {"tier": "expert", "layer": str(layer_index), "format": "sunny-moe-v1"},
        )
        entry["layer"] = layer_index
        entry["experts"] = module.num_experts
        manifest["expert_files"].append(entry)
        del expert_state

    # Preserve Sunny's one-tile image resize saved by training; reloading the
    # seed processor here would silently restore the 2,048px multi-tile default.
    AutoProcessor.from_pretrained(args.checkpoint).save_pretrained(target)
    manifest["total_bytes"] = sum(
        entry["bytes"] for entry in manifest["dense_shards"]
    ) + manifest["router_file"]["bytes"] + sum(
        entry["bytes"] for entry in manifest["expert_files"]
    )
    (target / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(f"[done] {target}; {manifest['total_bytes'] / 1e9:.2f} GB before Q4")


if __name__ == "__main__":
    main()
