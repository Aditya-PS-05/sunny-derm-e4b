#!/usr/bin/env python3
"""Materialize the corrected Sunny-MoE pack as a GGUF-convertible HF source.

The deployed pack stores grouped-Q4 bases plus FP16 correction LoRA. llama.cpp
can execute the SmolVLM2 vision path and mixed dense/MoE text graph, but its
converter expects ordinary tensors. This tool reconstructs those tensors,
merges the correction LoRA once, and names the sparse half like Mixtral so the
standard GGUF converter stacks each layer's four experts.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--max-shard-size", default="2GB")
    args = parser.parse_args()

    import torch
    from torch import nn
    from transformers import AutoModelForImageTextToText, AutoProcessor
    from sunny_moe.layers import SunnyLoRALinear, SunnySparseMLP
    from sunny_moe.q4_loader import load_q4_pack
    from sunny_moe.surgery import SunnyMoeSpec, upcycle_model

    source = Path(args.pack).resolve()
    target = Path(args.out).resolve()
    if target.exists():
        raise FileExistsError(f"refusing to overwrite {target}")
    target.mkdir(parents=True)

    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    spec = SunnyMoeSpec(**manifest["spec"])
    model = AutoModelForImageTextToText.from_pretrained(
        spec.seed_model,
        dtype=torch.float16,
        low_cpu_mem_usage=True,
        attn_implementation="eager",
    )
    upcycle_model(model, spec)
    loaded = load_q4_pack(model, str(source))

    def resolve(root: nn.Module, path: str) -> nn.Module:
        current = root
        for component in path.split(".") if path else []:
            current = current[int(component)] if component.isdigit() else getattr(current, component)
        return current

    def replace(root: nn.Module, path: str, module: nn.Module) -> None:
        parent_path, _, leaf = path.rpartition(".")
        parent = resolve(root, parent_path)
        if leaf.isdigit():
            parent[int(leaf)] = module
        else:
            setattr(parent, leaf, module)

    class WeightOnly(nn.Module):
        def __init__(self, weight: torch.Tensor):
            super().__init__()
            self.weight = nn.Parameter(weight.detach().cpu().contiguous(), requires_grad=False)

    class ExportExpert(nn.Module):
        def __init__(self, expert):
            super().__init__()
            merged = expert.merged_projection_weights()
            self.w1 = WeightOnly(merged["gate_proj.weight"])
            self.w2 = WeightOnly(merged["down_proj.weight"])
            self.w3 = WeightOnly(merged["up_proj.weight"])

    class ExportSparseMoe(nn.Module):
        def __init__(self, sparse: SunnySparseMLP):
            super().__init__()
            self.gate = WeightOnly(sparse.router.weight.float())
            # Runtime-controlled selection bias. It is zero during the routing
            # probe and then pins the image/prompt-selected expert through decode.
            self.e_score_correction = nn.Parameter(
                torch.zeros(spec.num_experts, dtype=torch.float32),
                requires_grad=False,
            )
            self.experts = nn.ModuleList(ExportExpert(expert) for expert in sparse.experts)

    # Merge every shared correction, including the vision-tower Q/K/V deltas.
    for path, module in list(model.named_modules()):
        if not path or not isinstance(module, SunnyLoRALinear):
            continue
        base = module.base
        linear = nn.Linear(
            module.in_features,
            module.out_features,
            bias=base.bias is not None,
            device="cpu",
            dtype=base.weight.dtype,
        )
        linear.weight.data.copy_(module.merged_weight().detach().cpu())
        if base.bias is not None:
            linear.bias.data.copy_(base.bias.detach().cpu())
        replace(model, path, linear)

    sparse_layers = 0
    for path, module in list(model.named_modules()):
        if not path or not isinstance(module, SunnySparseMLP):
            continue
        parent_path, _, leaf = path.rpartition(".")
        if leaf != "mlp":
            raise RuntimeError(f"unexpected sparse module path: {path}")
        parent = resolve(model, parent_path)
        delattr(parent, leaf)
        parent.add_module("block_sparse_moe", ExportSparseMoe(module))
        sparse_layers += 1

    if sparse_layers != 12:
        raise RuntimeError(f"expected 12 sparse layers, found {sparse_layers}")

    text_config = model.config.text_config
    text_config.num_local_experts = spec.num_experts
    text_config.num_experts_per_tok = spec.top_k
    text_config.score_function = "softmax"
    text_config.sunny_sparse_start_layer = spec.sparse_start_layer
    text_config.sunny_routing_scope = spec.routing_scope

    state = model.state_dict()
    forbidden = [
        name for name in state
        if ".delta." in name or ".base." in name or ".mlp.experts." in name
    ]
    if forbidden:
        raise RuntimeError(f"unmerged adapter/expert tensors remain: {forbidden[:5]}")
    router_keys = [name for name in state if name.endswith("block_sparse_moe.gate.weight")]
    expert_keys = [name for name in state if ".block_sparse_moe.experts." in name]
    if len(router_keys) != 12 or len(expert_keys) != 12 * spec.num_experts * 3:
        raise RuntimeError(
            f"bad sparse export: routers={len(router_keys)} experts={len(expert_keys)}"
        )

    model.save_pretrained(
        target,
        state_dict=state,
        safe_serialization=True,
        max_shard_size=args.max_shard_size,
    )
    AutoProcessor.from_pretrained(source).save_pretrained(target)
    # Transformers 5 serializes its generic Rust backend as TokenizersBackend;
    # llama.cpp's pinned Transformers 4 converter can load the same tokenizer.json
    # through the stable PreTrainedTokenizerFast name.
    tokenizer_config_path = target / "tokenizer_config.json"
    tokenizer_config = json.loads(tokenizer_config_path.read_text(encoding="utf-8"))
    if tokenizer_config.get("tokenizer_class") == "TokenizersBackend":
        tokenizer_config["tokenizer_class"] = "PreTrainedTokenizerFast"
        tokenizer_config_path.write_text(
            json.dumps(tokenizer_config, indent=2) + "\n", encoding="utf-8"
        )
    report = {
        "format": "sunny-moe-gguf-source-v1",
        "source_pack": str(source),
        "loaded_tensors": loaded["loaded_tensors"],
        "state_tensors": len(state),
        "sparse_layers": sparse_layers,
        "experts_per_layer": spec.num_experts,
        "corrections": "merged-fp16-before-gguf-quantization",
    }
    (target / "sunny_export.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
