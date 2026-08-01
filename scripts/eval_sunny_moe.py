#!/usr/bin/env python3
"""Held-out quality, safety, latency, and router evaluation for Sunny-MoE."""

from __future__ import annotations

import argparse
from dataclasses import replace
import json
import logging
import math
import os
import statistics
import time
from pathlib import Path

from eval_model import aggregate, parse_fields, score


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def normalize(value: str | None) -> str:
    return " ".join((value or "").strip().lower().split())


def router_summary(layer_counts: dict[int, list[int]]) -> dict:
    per_layer = {}
    all_counts: list[int] | None = None
    for layer, counts in sorted(layer_counts.items()):
        total = sum(counts)
        probabilities = [count / total if total else 0.0 for count in counts]
        entropy = -sum(p * math.log(p) for p in probabilities if p > 0)
        normalized_entropy = entropy / math.log(len(counts)) if len(counts) > 1 else 1.0
        per_layer[str(layer)] = {
            "counts": counts,
            "max_share": max(probabilities, default=0.0),
            "normalized_entropy": normalized_entropy,
        }
        if all_counts is None:
            all_counts = [0] * len(counts)
        for index, count in enumerate(counts):
            all_counts[index] += count
    total = sum(all_counts or [])
    return {
        "overall_counts": all_counts or [],
        "overall_shares": [count / total for count in (all_counts or [])] if total else [],
        "worst_layer_max_share": max(
            (entry["max_share"] for entry in per_layer.values()), default=0.0
        ),
        "mean_layer_normalized_entropy": (
            statistics.mean(entry["normalized_entropy"] for entry in per_layer.values())
            if per_layer else None
        ),
        "per_layer": per_layer,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument("--checkpoint")
    source_group.add_argument("--q4-pack")
    parser.add_argument("--val", required=True)
    parser.add_argument("--images-root", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--n", type=int, default=-1)
    parser.add_argument("--max-new-tokens", type=int, default=180)
    parser.add_argument("--device", default="cuda")
    parser.add_argument(
        "--routing-scope-override",
        choices=("sequence", "token"),
        help="Evaluate a runtime routing mode without changing the signed pack manifest.",
    )
    args = parser.parse_args()

    import torch
    from PIL import Image
    from transformers import AutoModelForImageTextToText, AutoProcessor
    from sunny_moe.checkpoint import CONFIG_FILE, load_adapter_checkpoint
    from sunny_moe.layers import configure_route_cache, iter_sparse_mlp
    from sunny_moe.q4_loader import load_q4_pack
    from sunny_moe.surgery import SunnyMoeSpec, parameter_summary, upcycle_model

    class _ProcessorKwargWarningFilter(logging.Filter):
        def filter(self, record: logging.LogRecord) -> bool:
            return not record.getMessage().startswith(
                "Kwargs passed to `processor.__call__`"
            )

    logging.getLogger("transformers.processing_utils").addFilter(
        _ProcessorKwargWarningFilter()
    )

    source = Path(args.checkpoint or args.q4_pack)
    if args.checkpoint:
        saved = json.loads((source / CONFIG_FILE).read_text(encoding="utf-8"))
    else:
        saved = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    spec = SunnyMoeSpec(**saved["spec"])
    if args.routing_scope_override:
        spec = replace(spec, routing_scope=args.routing_scope_override)
    device = torch.device(args.device)
    dtype = torch.bfloat16 if device.type == "cuda" else torch.float32
    model = AutoModelForImageTextToText.from_pretrained(
        spec.seed_model, dtype=dtype, low_cpu_mem_usage=True
    )
    replaced = upcycle_model(model, spec)
    q4_load = None
    if args.checkpoint:
        load_adapter_checkpoint(model, args.checkpoint)
    else:
        q4_load = load_q4_pack(model, args.q4_pack)
    model.to(device).eval()
    processor = AutoProcessor.from_pretrained(source)
    model.config.pad_token_id = processor.tokenizer.pad_token_id
    model.generation_config.pad_token_id = processor.tokenizer.pad_token_id

    with open(args.val, encoding="utf-8") as handle:
        records = [json.loads(line) for line in handle]
    if args.n > 0:
        records = records[:args.n]

    rows = []
    latencies = []
    generated_tokens = 0
    layer_counts = {
        spec.sparse_start_layer + offset: [0] * spec.num_experts
        for offset, _ in enumerate(iter_sparse_mlp(model))
    }
    samples = []
    if device.type == "cuda":
        torch.cuda.reset_peak_memory_stats(device)
    for index, record in enumerate(records):
        image = Image.open(os.path.join(args.images_root, record["image"])).convert("RGB")
        prompt = next(
            block["text"]
            for block in record["messages"][0]["content"]
            if block["type"] == "text"
        )
        reference = next(
            block["text"]
            for block in record["messages"][1]["content"]
            if block["type"] == "text"
        )
        messages = [{
            "role": "user",
            "content": [{"type": "image"}, {"type": "text", "text": prompt}],
        }]
        rendered = processor.apply_chat_template(
            messages, add_generation_prompt=True, tokenize=False
        )
        inputs = processor(text=[rendered], images=[[image]], return_tensors="pt")
        inputs = {
            name: value.to(device=device, dtype=dtype) if value.is_floating_point()
            else value.to(device)
            for name, value in inputs.items()
        }
        configure_route_cache(model, enabled=True, reset=True)
        if device.type == "cuda":
            torch.cuda.synchronize(device)
        started = time.perf_counter()
        with torch.inference_mode():
            output = model.generate(
                **inputs, do_sample=False, max_new_tokens=args.max_new_tokens,
                use_cache=True,
            )
        if device.type == "cuda":
            torch.cuda.synchronize(device)
        elapsed = time.perf_counter() - started
        prompt_tokens = int(inputs["input_ids"].shape[1])
        token_count = max(0, int(output.shape[1]) - prompt_tokens)
        prediction = processor.batch_decode(
            output[:, prompt_tokens:], skip_special_tokens=True
        )[0].strip()
        result = score(prediction, reference)
        predicted_fields = parse_fields(prediction)
        reference_fields = parse_fields(reference)
        result["lesion_type_exact"] = int(
            normalize(predicted_fields["Lesion Type"])
            == normalize(reference_fields["Lesion Type"])
        )
        result["dx"] = record.get("meta", {}).get("dx")
        rows.append(result)
        latencies.append(elapsed)
        generated_tokens += token_count
        for offset, module in enumerate(iter_sparse_mlp(model)):
            if module._cached_indices is None:
                continue
            expert = int(module._cached_indices[0, 0].item())
            layer_counts[spec.sparse_start_layer + offset][expert] += 1
        if len(samples) < 5:
            samples.append({
                "image": record["image"],
                "prediction": prediction,
                "reference": reference,
            })
        if (index + 1) % 10 == 0 or index + 1 == len(records):
            print(f"[eval] {index + 1}/{len(records)}", flush=True)

    quality = aggregate(rows)
    type_values = [row["lesion_type_exact"] for row in rows]
    quality["lesion_type_exact"] = sum(type_values) / len(type_values) if type_values else None
    task_values = [
        value
        for row in rows
        for value in [
            row["lesion_type_exact"],
            row["exact"].get("Symmetry"),
            row["exact"].get("Borders"),
            row["exact"].get("Texture"),
            row["colour_jaccard"],
        ]
        if value is not None
    ]
    quality["mean_task_score"] = (
        sum(task_values) / len(task_values) if task_values else None
    )
    total_latency = sum(latencies)
    report = {
        "format": "sunny-moe-eval-v1",
        "source": str(source),
        "source_format": "adapter" if args.checkpoint else "grouped-q4",
        "q4_loaded_tensors": q4_load["loaded_tensors"] if q4_load else None,
        "seed_model": spec.seed_model,
        "n": len(records),
        "quality": quality,
        "router": router_summary(layer_counts),
        "performance": {
            "latency_seconds_median": statistics.median(latencies) if latencies else None,
            "latency_seconds_p95": percentile(latencies, 0.95),
            "generated_tokens": generated_tokens,
            "tokens_per_second": generated_tokens / total_latency if total_latency else 0.0,
            "peak_gpu_bytes": (
                torch.cuda.max_memory_allocated(device) if device.type == "cuda" else None
            ),
        },
        "parameters": parameter_summary(model),
        "replaced_layers": replaced,
        "samples": samples,
    }
    Path(args.out).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "quality": report["quality"],
        "router": {
            key: report["router"][key]
            for key in ("overall_counts", "worst_layer_max_share", "mean_layer_normalized_entropy")
        },
        "performance": report["performance"],
    }, indent=2))


if __name__ == "__main__":
    main()
