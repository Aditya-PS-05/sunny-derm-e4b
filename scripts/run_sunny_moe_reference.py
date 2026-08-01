#!/usr/bin/env python3
"""Run a trained Sunny-MoE checkpoint on an image with pinned expert routes.

This is the correctness oracle for the later C/Android streaming runtime. It
loads all expert weights through PyTorch, but uses the same sequence-level
top-1 routing and pins each layer's selected route from prefill through decode.
"""

from __future__ import annotations

import argparse
import json
import logging
import time
from pathlib import Path


DEFAULT_PROMPT = (
    "You are a dermatology description assistant. Look at this skin lesion photo and "
    "describe what you see. Do NOT diagnose or name a disease. Report only observable "
    "features in this exact format:\n"
    "Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>\n"
    "Colour: <colours present>\n"
    "Symmetry: <symmetric / asymmetric>\n"
    "Borders: <smooth / irregular / well- or poorly-defined>\n"
    "Texture: <smooth / rough / raised / scaly>\n"
    "Summary: <one plain-language sentence describing the lesion's appearance and "
    "reminding the user this is not a diagnosis>"
)


def main() -> None:
    parser = argparse.ArgumentParser()
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument("--checkpoint")
    source_group.add_argument("--q4-pack")
    parser.add_argument("--image", required=True)
    parser.add_argument("--prompt", default=DEFAULT_PROMPT)
    parser.add_argument("--max-new-tokens", type=int, default=180)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--out-json")
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
    config_name = CONFIG_FILE if args.checkpoint else "manifest.json"
    saved = json.loads((source / config_name).read_text(encoding="utf-8"))
    spec = SunnyMoeSpec(**saved["spec"])
    if args.device.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but is unavailable")
    device = torch.device(args.device)
    dtype = torch.bfloat16 if device.type == "cuda" else torch.float32

    model = AutoModelForImageTextToText.from_pretrained(
        spec.seed_model, dtype=dtype, low_cpu_mem_usage=True
    )
    replaced = upcycle_model(model, spec)
    if args.checkpoint:
        load_adapter_checkpoint(model, args.checkpoint)
    else:
        load_q4_pack(model, args.q4_pack)
    model.to(device).eval()
    processor = AutoProcessor.from_pretrained(source)

    image = Image.open(args.image).convert("RGB")
    messages = [{
        "role": "user",
        "content": [
            {"type": "image"},
            {"type": "text", "text": args.prompt},
        ],
    }]
    text = processor.apply_chat_template(
        messages, add_generation_prompt=True, tokenize=False
    )
    inputs = processor(text=[text], images=[[image]], return_tensors="pt")
    inputs = {
        name: value.to(device=device, dtype=dtype) if value.is_floating_point()
        else value.to(device)
        for name, value in inputs.items()
    }

    configure_route_cache(model, enabled=True, reset=True)
    if device.type == "cuda":
        torch.cuda.reset_peak_memory_stats(device)
        torch.cuda.synchronize(device)
    started = time.perf_counter()
    with torch.inference_mode():
        generated = model.generate(
            **inputs,
            do_sample=False,
            max_new_tokens=args.max_new_tokens,
            use_cache=True,
        )
    if device.type == "cuda":
        torch.cuda.synchronize(device)
    elapsed = time.perf_counter() - started
    prompt_tokens = int(inputs["input_ids"].shape[1])
    new_tokens = max(0, int(generated.shape[1]) - prompt_tokens)
    decoded = processor.batch_decode(
        generated[:, prompt_tokens:], skip_special_tokens=True
    )[0].strip()
    routes = []
    for layer_offset, module in enumerate(iter_sparse_mlp(model)):
        selected = (
            int(module._cached_indices[0, 0].item())
            if module._cached_indices is not None else None
        )
        routes.append({
            "layer": spec.sparse_start_layer + layer_offset,
            "selected_expert": selected,
            "router_counts": list(module.last_stats.counts) if module.last_stats else None,
        })
    result = {
        "text": decoded,
        "seed_model": spec.seed_model,
        "replaced_layers": replaced,
        "parameters": parameter_summary(model),
        "prompt_tokens": prompt_tokens,
        "new_tokens": new_tokens,
        "elapsed_seconds": elapsed,
        "tokens_per_second": new_tokens / elapsed if elapsed else 0.0,
        "peak_gpu_bytes": (
            torch.cuda.max_memory_allocated(device) if device.type == "cuda" else None
        ),
        "routes": routes,
    }
    print(decoded)
    print(
        f"\n[metrics] {new_tokens} tokens in {elapsed:.2f}s "
        f"({result['tokens_per_second']:.2f} tok/s)"
    )
    if args.out_json:
        Path(args.out_json).write_text(
            json.dumps(result, indent=2) + "\n", encoding="utf-8"
        )


if __name__ == "__main__":
    main()
