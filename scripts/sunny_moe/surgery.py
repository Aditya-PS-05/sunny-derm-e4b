"""Model surgery that upcycles dense gated FFNs into Sunny sparse experts."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass

from torch import nn

from .layers import SunnyLoRALinear, SunnySparseMLP


_LAYER_MLP = re.compile(r"(?:^|\.)layers\.(\d+)\.mlp$")
_ATTENTION_PROJECTION = re.compile(
    r"(?:^|\.)layers\.(\d+)\.self_attn\.(?:q|k|v|o)_proj$"
)
_DENSE_MLP_PROJECTION = re.compile(
    r"(?:^|\.)layers\.(\d+)\.mlp\.(?:gate|up|down)_proj$"
)


@dataclass(frozen=True)
class SunnyMoeSpec:
    seed_model: str
    sparse_start_layer: int
    num_experts: int = 4
    top_k: int = 1
    lora_rank: int = 16
    lora_alpha: float = 16.0
    routing_scope: str = "sequence"
    router_seed: int = 42
    shared_lora_rank: int = 0
    shared_lora_alpha: float = 16.0

    def as_dict(self) -> dict:
        return asdict(self)


def _set_module(model: nn.Module, path: str, replacement: nn.Module) -> None:
    parent = model
    parts = path.split(".")
    for part in parts[:-1]:
        parent = getattr(parent, part)
    setattr(parent, parts[-1], replacement)


def find_dense_mlp(model: nn.Module) -> list[tuple[int, str, nn.Module]]:
    found: list[tuple[int, str, nn.Module]] = []
    for path, module in model.named_modules():
        match = _LAYER_MLP.search(path)
        if not match:
            continue
        if all(hasattr(module, name) for name in ("gate_proj", "up_proj", "down_proj", "act_fn")):
            found.append((int(match.group(1)), path, module))
    found.sort(key=lambda item: item[0])
    return found


def attach_shared_lora(model: nn.Module, spec: SunnyMoeSpec) -> list[str]:
    """Adapt shared attention plus dense-half FFNs without changing deploy size."""
    if spec.shared_lora_rank <= 0:
        return []
    selected: list[tuple[str, nn.Linear]] = []
    for path, module in list(model.named_modules()):
        if not isinstance(module, nn.Linear):
            continue
        attention = _ATTENTION_PROJECTION.search(path)
        dense_mlp = _DENSE_MLP_PROJECTION.search(path)
        if attention or (
            dense_mlp and int(dense_mlp.group(1)) < spec.sparse_start_layer
        ):
            selected.append((path, module))
    for path, module in selected:
        _set_module(
            model,
            path,
            SunnyLoRALinear(
                module, rank=spec.shared_lora_rank, alpha=spec.shared_lora_alpha
            ),
        )
    return [path for path, _ in selected]


def upcycle_model(model: nn.Module, spec: SunnyMoeSpec) -> list[str]:
    """Freeze the seed and replace selected FFNs with function-preserving MoEs."""
    for parameter in model.parameters():
        parameter.requires_grad_(False)

    candidates = find_dense_mlp(model)
    if not candidates:
        raise RuntimeError("no Llama-style gated MLP layers found in the seed model")
    hidden_size = int(getattr(getattr(model.config, "text_config", model.config), "hidden_size"))
    replaced: list[str] = []
    for layer_index, path, dense_mlp in candidates:
        if layer_index < spec.sparse_start_layer:
            continue
        sparse = SunnySparseMLP(
            dense_mlp,
            hidden_size=hidden_size,
            num_experts=spec.num_experts,
            top_k=spec.top_k,
            lora_rank=spec.lora_rank,
            lora_alpha=spec.lora_alpha,
            routing_scope=spec.routing_scope,
            router_seed=spec.router_seed + layer_index,
        )
        _set_module(model, path, sparse)
        replaced.append(path)
    if not replaced:
        raise RuntimeError(
            f"sparse_start_layer={spec.sparse_start_layer} did not select any layers"
        )
    attach_shared_lora(model, spec)
    return replaced


def parameter_summary(model: nn.Module) -> dict[str, int | float]:
    total = sum(parameter.numel() for parameter in model.parameters())
    trainable = sum(
        parameter.numel() for parameter in model.parameters() if parameter.requires_grad
    )
    return {
        "total_parameters": total,
        "trainable_parameters": trainable,
        "trainable_percent": 100.0 * trainable / total if total else 0.0,
    }
