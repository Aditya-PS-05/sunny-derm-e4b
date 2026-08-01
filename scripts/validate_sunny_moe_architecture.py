#!/usr/bin/env python3
"""Validate Sunny's surgery against a real Transformers architecture without weights."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="HuggingFaceTB/SmolVLM2-2.2B-Instruct")
    parser.add_argument("--sparse-start-layer", type=int)
    parser.add_argument("--experts", type=int, default=4)
    parser.add_argument("--top-k", type=int, default=1)
    parser.add_argument("--lora-rank", type=int, default=16)
    parser.add_argument("--shared-lora-rank", type=int, default=0)
    parser.add_argument("--out")
    args = parser.parse_args()

    from accelerate import init_empty_weights
    from transformers import AutoConfig, AutoModelForImageTextToText
    from sunny_moe.surgery import (
        SunnyMoeSpec,
        find_dense_mlp,
        parameter_summary,
        upcycle_model,
    )

    config = AutoConfig.from_pretrained(args.model)
    with init_empty_weights():
        model = AutoModelForImageTextToText.from_config(config)
    dense_summary = parameter_summary(model)
    candidates = find_dense_mlp(model)
    text_config = getattr(config, "text_config", config)
    expected_layers = int(text_config.num_hidden_layers)
    if len(candidates) != expected_layers:
        raise RuntimeError(
            f"found {len(candidates)} gated FFNs, expected {expected_layers}"
        )
    sparse_start = args.sparse_start_layer
    if sparse_start is None:
        sparse_start = expected_layers // 2
    replaced = upcycle_model(
        model,
        SunnyMoeSpec(
            seed_model=args.model,
            sparse_start_layer=sparse_start,
            num_experts=args.experts,
            top_k=args.top_k,
            lora_rank=args.lora_rank,
            shared_lora_rank=args.shared_lora_rank,
        ),
    )
    sparse_summary = parameter_summary(model)
    result = {
        "model": args.model,
        "architecture": model.__class__.__name__,
        "decoder_layers_found": len(candidates),
        "dense_seed_parameters": dense_summary["total_parameters"],
        "replaced_layers": len(replaced),
        "first_replaced": replaced[0],
        "last_replaced": replaced[-1],
        "upcycled_parameters_including_training_adapters": sparse_summary[
            "total_parameters"
        ],
        "trainable_router_adapter_parameters": sparse_summary["trainable_parameters"],
    }
    print(json.dumps(result, indent=2))
    if args.out:
        target = Path(args.out)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
