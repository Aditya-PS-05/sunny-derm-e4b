"""Dependency-free sizing and architecture planning for Sunny-MoE."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Mapping


@dataclass(frozen=True)
class SunnyMoePlan:
    seed_model: str
    seed_parameters: int
    hidden_size: int
    intermediate_size: int
    num_layers: int
    sparse_start_layer: int
    num_experts: int
    top_k: int
    deployment_bpw: float
    expert_parameters_per_layer: int
    sparse_layers: int
    added_parameters: int
    total_parameters: int
    active_parameters: int
    estimated_download_bytes: int
    active_weight_bytes: int
    expert_bytes: int
    cold_expert_bytes_per_token: int

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _positive(name: str, value: int) -> None:
    if value <= 0:
        raise ValueError(f"{name} must be positive, got {value}")


def build_plan(
    *,
    seed_model: str,
    seed_parameters: int,
    hidden_size: int,
    intermediate_size: int,
    num_layers: int,
    sparse_start_layer: int,
    num_experts: int = 4,
    top_k: int = 1,
    deployment_bpw: float = 5.5,
) -> SunnyMoePlan:
    """Calculate total, active, download, and cold-I/O envelopes.

    A Llama-style gated FFN has three weight matrices: gate, up, and down.
    Upcycling keeps the original FFN as expert zero and adds E-1 copies.
    With top-1 routing, active parameter count therefore stays equal to the
    dense seed while total capacity grows.
    """

    for name, value in (
        ("seed_parameters", seed_parameters),
        ("hidden_size", hidden_size),
        ("intermediate_size", intermediate_size),
        ("num_layers", num_layers),
        ("num_experts", num_experts),
        ("top_k", top_k),
    ):
        _positive(name, value)
    if not 0 <= sparse_start_layer < num_layers:
        raise ValueError(
            f"sparse_start_layer must be in [0, {num_layers}), got {sparse_start_layer}"
        )
    if top_k > num_experts:
        raise ValueError("top_k cannot exceed num_experts")
    if deployment_bpw <= 0:
        raise ValueError("deployment_bpw must be positive")

    sparse_layers = num_layers - sparse_start_layer
    expert_parameters = 3 * hidden_size * intermediate_size
    added_parameters = sparse_layers * (num_experts - 1) * expert_parameters
    total_parameters = seed_parameters + added_parameters
    active_parameters = seed_parameters + sparse_layers * (top_k - 1) * expert_parameters
    bytes_per_parameter = deployment_bpw / 8.0
    expert_bytes = round(expert_parameters * bytes_per_parameter)

    return SunnyMoePlan(
        seed_model=seed_model,
        seed_parameters=seed_parameters,
        hidden_size=hidden_size,
        intermediate_size=intermediate_size,
        num_layers=num_layers,
        sparse_start_layer=sparse_start_layer,
        num_experts=num_experts,
        top_k=top_k,
        deployment_bpw=deployment_bpw,
        expert_parameters_per_layer=expert_parameters,
        sparse_layers=sparse_layers,
        added_parameters=added_parameters,
        total_parameters=total_parameters,
        active_parameters=active_parameters,
        estimated_download_bytes=round(total_parameters * bytes_per_parameter),
        active_weight_bytes=round(active_parameters * bytes_per_parameter),
        expert_bytes=expert_bytes,
        cold_expert_bytes_per_token=sparse_layers * top_k * expert_bytes,
    )


def build_plan_from_hf_config(
    config: Mapping[str, Any],
    *,
    seed_model: str,
    seed_parameters: int,
    sparse_start_layer: int | None = None,
    num_experts: int = 4,
    top_k: int = 1,
    deployment_bpw: float = 5.5,
) -> SunnyMoePlan:
    text = config.get("text_config", config)
    num_layers = int(text["num_hidden_layers"])
    if sparse_start_layer is None:
        sparse_start_layer = num_layers // 2
    return build_plan(
        seed_model=seed_model,
        seed_parameters=seed_parameters,
        hidden_size=int(text["hidden_size"]),
        intermediate_size=int(text["intermediate_size"]),
        num_layers=num_layers,
        sparse_start_layer=sparse_start_layer,
        num_experts=num_experts,
        top_k=top_k,
        deployment_bpw=deployment_bpw,
    )
