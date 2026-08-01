"""Sparse top-k FFN layers used by the Sunny-MoE prototype."""

from __future__ import annotations

import copy
import math
from dataclasses import dataclass

import torch
import torch.nn.functional as F
from torch import nn


class LowRankDelta(nn.Module):
    """A trainable LoRA delta kept separate until expert-pack export."""

    def __init__(
        self,
        in_features: int,
        out_features: int,
        *,
        rank: int,
        alpha: float,
        device: torch.device | None = None,
    ) -> None:
        super().__init__()
        if rank <= 0:
            raise ValueError("rank must be positive")
        self.rank = rank
        self.scale = alpha / rank
        # Float32 adapters keep small router/expert updates numerically stable;
        # their output is cast back to the frozen expert's compute dtype.
        self.a = nn.Linear(in_features, rank, bias=False, device=device, dtype=torch.float32)
        self.b = nn.Linear(rank, out_features, bias=False, device=device, dtype=torch.float32)
        nn.init.kaiming_uniform_(self.a.weight, a=math.sqrt(5))
        nn.init.zeros_(self.b.weight)

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        return self.b(self.a(inputs.float())) * self.scale

    def merged_weight(self, *, dtype: torch.dtype | None = None) -> torch.Tensor:
        delta = torch.matmul(self.b.weight, self.a.weight) * self.scale
        return delta.to(dtype=dtype) if dtype is not None else delta


class SunnyLoRALinear(nn.Module):
    """A frozen shared linear projection with a mergeable trainable delta."""

    def __init__(self, base: nn.Linear, *, rank: int, alpha: float) -> None:
        super().__init__()
        if not isinstance(base, nn.Linear):
            raise TypeError("SunnyLoRALinear requires torch.nn.Linear")
        self.base = base
        for parameter in self.base.parameters():
            parameter.requires_grad_(False)
        self.delta = LowRankDelta(
            base.in_features, base.out_features,
            rank=rank, alpha=alpha, device=base.weight.device,
        )

    @property
    def in_features(self) -> int:
        return self.base.in_features

    @property
    def out_features(self) -> int:
        return self.base.out_features

    @property
    def weight(self) -> torch.Tensor:
        return self.base.weight

    @property
    def bias(self) -> torch.Tensor | None:
        return self.base.bias

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        base = self.base(inputs)
        return base + self.delta(inputs).to(dtype=base.dtype)

    @torch.no_grad()
    def merged_weight(self) -> torch.Tensor:
        return (
            self.base.weight.detach()
            + self.delta.merged_weight(dtype=self.base.weight.dtype)
        ).contiguous()


class SunnyExpertMLP(nn.Module):
    """A frozen cloned gated FFN plus independently trainable low-rank deltas."""

    def __init__(self, base_mlp: nn.Module, *, rank: int, alpha: float) -> None:
        super().__init__()
        required = ("gate_proj", "up_proj", "down_proj", "act_fn")
        missing = [name for name in required if not hasattr(base_mlp, name)]
        if missing:
            raise TypeError(f"MLP is missing required attributes: {', '.join(missing)}")

        self.base = base_mlp
        for parameter in self.base.parameters():
            parameter.requires_grad_(False)

        gate = self.base.gate_proj
        up = self.base.up_proj
        down = self.base.down_proj
        device = gate.weight.device
        self.gate_delta = LowRankDelta(
            gate.in_features, gate.out_features, rank=rank, alpha=alpha, device=device
        )
        self.up_delta = LowRankDelta(
            up.in_features, up.out_features, rank=rank, alpha=alpha, device=device
        )
        self.down_delta = LowRankDelta(
            down.in_features, down.out_features, rank=rank, alpha=alpha, device=device
        )

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        gate = self.base.gate_proj(inputs)
        gate = gate + self.gate_delta(inputs).to(dtype=gate.dtype)
        up = self.base.up_proj(inputs)
        up = up + self.up_delta(inputs).to(dtype=up.dtype)
        hidden = self.base.act_fn(gate) * up
        down = self.base.down_proj(hidden)
        return down + self.down_delta(hidden).to(dtype=down.dtype)

    @torch.no_grad()
    def merged_projection_weights(self) -> dict[str, torch.Tensor]:
        result: dict[str, torch.Tensor] = {}
        for name in ("gate", "up", "down"):
            projection = getattr(self.base, f"{name}_proj")
            delta = getattr(self, f"{name}_delta")
            result[f"{name}_proj.weight"] = (
                projection.weight.detach() + delta.merged_weight(dtype=projection.weight.dtype)
            ).contiguous()
            if projection.bias is not None:
                result[f"{name}_proj.bias"] = projection.bias.detach().contiguous()
        return result


@dataclass(frozen=True)
class RouterStats:
    counts: tuple[int, ...]
    probabilities: tuple[float, ...]


class SunnySparseMLP(nn.Module):
    """Top-k routed experts with optional per-sequence route pinning.

    Experts are identical at initialization and top-k weights are normalized,
    so replacing a dense FFN is initially function-preserving. Low-rank expert
    deltas then break symmetry without training billions of full-rank weights.
    """

    def __init__(
        self,
        base_mlp: nn.Module,
        *,
        hidden_size: int,
        num_experts: int = 4,
        top_k: int = 1,
        lora_rank: int = 16,
        lora_alpha: float = 16.0,
        routing_scope: str = "sequence",
        router_seed: int = 42,
    ) -> None:
        super().__init__()
        if not 1 <= top_k <= num_experts:
            raise ValueError("top_k must be between one and num_experts")
        if routing_scope not in {"token", "sequence"}:
            raise ValueError("routing_scope must be 'token' or 'sequence'")

        self.hidden_size = hidden_size
        self.num_experts = num_experts
        self.top_k = top_k
        self.routing_scope = routing_scope

        clones = [base_mlp]
        clones.extend(copy.deepcopy(base_mlp) for _ in range(num_experts - 1))
        self.experts = nn.ModuleList(
            SunnyExpertMLP(clone, rank=lora_rank, alpha=lora_alpha) for clone in clones
        )
        device = self.experts[0].base.gate_proj.weight.device
        self.router = nn.Linear(
            hidden_size, num_experts, bias=False, device=device, dtype=torch.float32
        )
        if device.type != "meta":
            generator = torch.Generator(device=device)
            generator.manual_seed(router_seed)
            nn.init.normal_(self.router.weight, mean=0.0, std=0.01, generator=generator)

        self.last_aux_loss: torch.Tensor | None = None
        self.last_z_loss: torch.Tensor | None = None
        self.last_stats: RouterStats | None = None
        self._route_cache_enabled = False
        self._cached_indices: torch.Tensor | None = None
        self._cached_weights: torch.Tensor | None = None
        self._routing_mask: torch.Tensor | None = None

    def set_route_cache(self, enabled: bool) -> None:
        self._route_cache_enabled = enabled
        if not enabled:
            self.reset_route_cache()

    def reset_route_cache(self) -> None:
        self._cached_indices = None
        self._cached_weights = None

    def set_routing_mask(self, mask: torch.Tensor | None) -> None:
        """Restrict sequence pooling to prompt/image positions during training."""
        self._routing_mask = mask

    def _select(
        self, router_inputs: torch.Tensor
    ) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        logits = self.router(router_inputs.float())
        probabilities = torch.softmax(logits, dim=-1)
        selected_probabilities, selected_indices = torch.topk(
            probabilities, k=self.top_k, dim=-1
        )
        if self.top_k == 1:
            # A normalized top-1 weight is the constant one and therefore gives
            # the router no task-loss gradient. This straight-through value is
            # exactly one in the forward pass (preserving the dense seed), but
            # differentiates as the selected router probability in backward.
            selected_weights = (
                torch.ones_like(selected_probabilities)
                + selected_probabilities
                - selected_probabilities.detach()
            )
        else:
            selected_weights = selected_probabilities / selected_probabilities.sum(
                dim=-1, keepdim=True
            ).clamp_min(1e-9)

        top_one = F.one_hot(
            selected_indices[:, 0], num_classes=self.num_experts
        ).float().mean(dim=0)
        importance = probabilities.mean(dim=0)
        self.last_aux_loss = self.num_experts * torch.sum(top_one.detach() * importance)
        self.last_z_loss = torch.mean(torch.logsumexp(logits, dim=-1).square())
        counts = torch.bincount(
            selected_indices[:, 0], minlength=self.num_experts
        ).detach().cpu()
        self.last_stats = RouterStats(
            counts=tuple(int(value) for value in counts.tolist()),
            probabilities=tuple(float(value) for value in importance.detach().cpu().tolist()),
        )
        return selected_indices, selected_weights, probabilities

    def _sequence_routes(
        self, hidden_states: torch.Tensor
    ) -> tuple[torch.Tensor, torch.Tensor]:
        batch, sequence, _ = hidden_states.shape
        cache_valid = (
            self._route_cache_enabled
            and self._cached_indices is not None
            and self._cached_indices.shape[0] == batch
        )
        if cache_valid:
            selected_indices = self._cached_indices.to(hidden_states.device)
            selected_weights = self._cached_weights.to(hidden_states.device)
            self.last_aux_loss = None
            self.last_z_loss = None
        else:
            mask = self._routing_mask
            if mask is not None and tuple(mask.shape) == (batch, sequence):
                weights = mask.to(device=hidden_states.device, dtype=torch.float32).unsqueeze(-1)
                pooled = (hidden_states.float() * weights).sum(dim=1) / weights.sum(
                    dim=1
                ).clamp_min(1.0)
            else:
                pooled = hidden_states.float().mean(dim=1)
            selected_indices, selected_weights, _ = self._select(pooled)
            if self._route_cache_enabled:
                self._cached_indices = selected_indices.detach()
                self._cached_weights = selected_weights.detach()
        selected_indices = selected_indices[:, None, :].expand(batch, sequence, self.top_k)
        selected_weights = selected_weights[:, None, :].expand(batch, sequence, self.top_k)
        return (
            selected_indices.reshape(batch * sequence, self.top_k),
            selected_weights.reshape(batch * sequence, self.top_k),
        )

    def forward(self, hidden_states: torch.Tensor) -> torch.Tensor:
        original_shape = hidden_states.shape
        flat = hidden_states.reshape(-1, original_shape[-1])
        if hidden_states.ndim == 3 and self.routing_scope == "sequence":
            selected_indices, selected_weights = self._sequence_routes(hidden_states)
        else:
            selected_indices, selected_weights, _ = self._select(flat)

        output = torch.zeros_like(flat)
        for expert_id, expert in enumerate(self.experts):
            locations = (selected_indices == expert_id).nonzero(as_tuple=False)
            if locations.numel() == 0:
                continue
            token_indices = locations[:, 0]
            slots = locations[:, 1]
            expert_output = expert(flat.index_select(0, token_indices))
            weights = selected_weights[token_indices, slots].to(expert_output.dtype).unsqueeze(-1)
            output.index_add_(0, token_indices, expert_output * weights)
        return output.reshape(original_shape)


def iter_sparse_mlp(model: nn.Module):
    for module in model.modules():
        if isinstance(module, SunnySparseMLP):
            yield module


def router_losses(model: nn.Module) -> tuple[torch.Tensor | None, torch.Tensor | None]:
    aux = [module.last_aux_loss for module in iter_sparse_mlp(model)
           if module.last_aux_loss is not None]
    z = [module.last_z_loss for module in iter_sparse_mlp(model)
         if module.last_z_loss is not None]
    aux_loss = torch.stack(aux).mean() if aux else None
    z_loss = torch.stack(z).mean() if z else None
    return aux_loss, z_loss


def configure_route_cache(model: nn.Module, *, enabled: bool, reset: bool = True) -> None:
    for module in iter_sparse_mlp(model):
        if reset:
            module.reset_route_cache()
        module.set_route_cache(enabled)


def configure_routing_mask(model: nn.Module, mask: torch.Tensor | None) -> None:
    for module in iter_sparse_mlp(model):
        module.set_routing_mask(mask)
