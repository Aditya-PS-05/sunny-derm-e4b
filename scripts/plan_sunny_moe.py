#!/usr/bin/env python3
"""Produce a reproducible Sunny-MoE capacity and deployment plan."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

from sunny_moe.plan import build_plan_from_hf_config


DEFAULT_MODEL = "HuggingFaceTB/SmolVLM2-2.2B-Instruct"


def load_config(model: str, config_file: str | None) -> dict:
    if config_file:
        with open(config_file, encoding="utf-8") as handle:
            return json.load(handle)
    url = f"https://huggingface.co/{model}/resolve/main/config.json"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.load(response)
    except Exception as exc:
        raise RuntimeError(
            f"could not fetch {url}; pass --config-file for an offline run"
        ) from exc


def gb(value: int) -> str:
    return f"{value / 1_000_000_000:.2f} GB"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--seed-parameters", type=int, default=2_246_784_880)
    parser.add_argument("--config-file")
    parser.add_argument("--sparse-start-layer", type=int)
    parser.add_argument("--experts", type=int, default=4)
    parser.add_argument("--top-k", type=int, default=1)
    parser.add_argument("--deployment-bpw", type=float, default=5.5)
    parser.add_argument("--max-download-gb", type=float, default=3.0)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--out")
    args = parser.parse_args()

    plan = build_plan_from_hf_config(
        load_config(args.model, args.config_file),
        seed_model=args.model,
        seed_parameters=args.seed_parameters,
        sparse_start_layer=args.sparse_start_layer,
        num_experts=args.experts,
        top_k=args.top_k,
        deployment_bpw=args.deployment_bpw,
    )
    payload = plan.as_dict()
    payload["passes_download_gate"] = (
        plan.estimated_download_bytes <= args.max_download_gb * 1_000_000_000
    )

    if args.out:
        target = Path(args.out)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    if args.json:
        print(json.dumps(payload, indent=2))
        return

    print(f"Seed:                  {plan.seed_model}")
    print(f"Dense → sparse layers: {plan.sparse_start_layer}–{plan.num_layers - 1} "
          f"({plan.sparse_layers} layers)")
    print(f"Routing:               top-{plan.top_k} of {plan.num_experts}")
    print(f"Total parameters:      {plan.total_parameters / 1e9:.3f}B")
    print(f"Active parameters:     {plan.active_parameters / 1e9:.3f}B")
    print(f"Estimated download:    {gb(plan.estimated_download_bytes)} "
          f"at {plan.deployment_bpw:.2f} bpw")
    print(f"Active weight bytes:   {gb(plan.active_weight_bytes)}")
    print(f"Cold expert I/O/token: {gb(plan.cold_expert_bytes_per_token)}")
    print(f"≤{args.max_download_gb:.1f} GB gate:          "
          f"{'PASS' if payload['passes_download_gate'] else 'FAIL'}")
    if not payload["passes_download_gate"]:
        sys.exit(2)


if __name__ == "__main__":
    main()
