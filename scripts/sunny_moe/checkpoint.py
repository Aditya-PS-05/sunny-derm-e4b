"""Small adapter/router checkpoints for Sunny-MoE training."""

from __future__ import annotations

import json
from pathlib import Path

from torch import nn

from .surgery import SunnyMoeSpec


WEIGHTS_FILE = "sunny_moe_adapters.safetensors"
CONFIG_FILE = "sunny_moe_config.json"


def save_adapter_checkpoint(
    model: nn.Module,
    output_dir: str,
    *,
    spec: SunnyMoeSpec,
    replaced_modules: list[str],
    extra: dict | None = None,
) -> None:
    from safetensors.torch import save_file

    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    state = {
        name: parameter.detach().cpu().contiguous()
        for name, parameter in model.named_parameters()
        if parameter.requires_grad
    }
    if not state:
        raise RuntimeError("refusing to save an empty Sunny-MoE adapter checkpoint")
    save_file(state, str(target / WEIGHTS_FILE), metadata={"format": "sunny-moe-v1"})
    payload = {
        "format": "sunny-moe-v1",
        "spec": spec.as_dict(),
        "replaced_modules": replaced_modules,
        "trainable_tensors": sorted(state),
    }
    if extra:
        payload["training"] = extra
    (target / CONFIG_FILE).write_text(
        json.dumps(payload, indent=2) + "\n", encoding="utf-8"
    )


def load_adapter_checkpoint(model: nn.Module, checkpoint_dir: str) -> dict:
    from safetensors.torch import load_file

    source = Path(checkpoint_dir)
    payload = json.loads((source / CONFIG_FILE).read_text(encoding="utf-8"))
    state = load_file(str(source / WEIGHTS_FILE), device="cpu")
    expected = {name for name, parameter in model.named_parameters() if parameter.requires_grad}
    actual = set(state)
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise RuntimeError(
            f"adapter tensor mismatch; missing={missing[:5]} unexpected={unexpected[:5]}"
        )
    result = model.load_state_dict(state, strict=False)
    if result.unexpected_keys:
        raise RuntimeError(f"unexpected adapter keys: {result.unexpected_keys}")
    return payload
