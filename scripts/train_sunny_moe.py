#!/usr/bin/env python3
"""Upcycle SmolVLM2 and train Sunny's routers plus per-expert LoRA deltas.

The dense seed and cloned full-rank expert weights remain frozen. Only routers
and low-rank expert deltas train, keeping the 4.01B-parameter candidate within
a single 24 GB GPU's intended memory envelope.
"""

from __future__ import annotations

import argparse
import logging
import os
import time


PRODUCTION_SEED = "HuggingFaceTB/SmolVLM2-2.2B-Instruct"
SMOKE_SEED = "HuggingFaceTB/SmolVLM2-500M-Video-Instruct"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=PRODUCTION_SEED)
    parser.add_argument(
        "--quantized-pack",
        help="calibrate against an existing quantized expert pack",
    )
    parser.add_argument(
        "--calibration-scope",
        choices=("routers", "adapters"),
        default="routers",
        help="with --quantized-pack, train routers only or routers plus correction LoRA",
    )
    parser.add_argument("--smoke", action="store_true", help="use the 500M seed and two steps")
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--images-root", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--sparse-start-layer", type=int)
    parser.add_argument("--experts", type=int, default=4)
    parser.add_argument("--top-k", type=int, default=1)
    parser.add_argument("--routing-scope", choices=("sequence", "token"), default="sequence")
    parser.add_argument("--lora-rank", type=int, default=16)
    parser.add_argument("--lora-alpha", type=float, default=16.0)
    parser.add_argument("--shared-lora-rank", type=int, default=16)
    parser.add_argument("--shared-lora-alpha", type=float, default=16.0)
    parser.add_argument(
        "--image-longest-edge", type=int, default=0,
        help="resize to this edge; 0 uses one native SmolVLM vision tile",
    )
    parser.add_argument("--router-aux-coef", type=float, default=0.10)
    parser.add_argument("--router-z-coef", type=float, default=0.001)
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--warmup-ratio", type=float, default=0.03)
    parser.add_argument("--max-steps", type=int, default=-1)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    import torch
    import transformers
    from transformers import AutoModelForImageTextToText, AutoProcessor, Trainer, TrainingArguments
    from sunny_moe.checkpoint import save_adapter_checkpoint
    from sunny_moe.layers import configure_routing_mask, iter_sparse_mlp, router_losses
    from sunny_moe.q4_loader import load_q4_pack
    from sunny_moe.surgery import SunnyMoeSpec, parameter_summary, upcycle_model
    from train_qlora import build_collator, load_records

    class _ProcessorKwargWarningFilter(logging.Filter):
        def filter(self, record: logging.LogRecord) -> bool:
            return not record.getMessage().startswith(
                "Kwargs passed to `processor.__call__`"
            )

    logging.getLogger("transformers.processing_utils").addFilter(
        _ProcessorKwargWarningFilter()
    )

    if args.smoke:
        args.model = SMOKE_SEED
        args.max_steps = 2

    saved_quantized = None
    if args.quantized_pack:
        import json

        with open(os.path.join(args.quantized_pack, "manifest.json"), encoding="utf-8") as handle:
            saved_quantized = json.load(handle)
        args.model = saved_quantized["seed_model"]

    token = os.environ.get("HF_TOKEN")
    print(
        f"[env] transformers={transformers.__version__} torch={torch.__version__} "
        f"cuda={torch.cuda.is_available()} token={'set' if token else 'not-needed'}"
    )
    if not torch.cuda.is_available():
        raise RuntimeError("Sunny-MoE training requires a CUDA GPU")

    print(f"[model] loading frozen BF16 seed: {args.model}")
    model = AutoModelForImageTextToText.from_pretrained(
        args.model,
        dtype=torch.bfloat16,
        low_cpu_mem_usage=True,
        token=token,
        attn_implementation="eager",
    )
    processor_source = args.quantized_pack or args.model
    processor = AutoProcessor.from_pretrained(processor_source, token=token)
    if processor.tokenizer.pad_token_id is None:
        processor.tokenizer.pad_token = processor.tokenizer.eos_token
    max_image_size = getattr(processor.image_processor, "max_image_size", {})
    native_edge = (
        max_image_size.get("longest_edge")
        if hasattr(max_image_size, "get") else None
    )
    image_edge = args.image_longest_edge or native_edge or 512
    processor.image_processor.size = {"longest_edge": int(image_edge)}
    model.config.pad_token_id = processor.tokenizer.pad_token_id
    if getattr(model, "generation_config", None) is not None:
        model.generation_config.pad_token_id = processor.tokenizer.pad_token_id
    print(f"[vision] longest_edge={image_edge}px (single native tile)")

    text_config = getattr(model.config, "text_config", model.config)
    num_layers = int(text_config.num_hidden_layers)
    sparse_start = args.sparse_start_layer
    if sparse_start is None:
        sparse_start = num_layers // 2
    if saved_quantized:
        spec = SunnyMoeSpec(**saved_quantized["spec"])
    else:
        spec = SunnyMoeSpec(
            seed_model=args.model,
            sparse_start_layer=sparse_start,
            num_experts=args.experts,
            top_k=args.top_k,
            lora_rank=args.lora_rank,
            lora_alpha=args.lora_alpha,
            routing_scope=args.routing_scope,
            router_seed=args.seed,
            shared_lora_rank=args.shared_lora_rank,
            shared_lora_alpha=args.shared_lora_alpha,
        )
    replaced = upcycle_model(model, spec)
    if args.quantized_pack:
        loaded = load_q4_pack(model, args.quantized_pack)
        if args.calibration_scope == "routers":
            for parameter in model.parameters():
                parameter.requires_grad_(False)
            for module in iter_sparse_mlp(model):
                module.router.weight.requires_grad_(True)
        print(
            f"[calibrate] loaded {loaded['loaded_tensors']} deployed tensors; "
            + (
                "only FP32 routers will train"
                if args.calibration_scope == "routers"
                else "routers and fresh correction LoRA will train"
            )
        )
    summary = parameter_summary(model)
    print(f"[moe] replaced {len(replaced)} FFNs: {replaced[0]} … {replaced[-1]}")
    print(
        f"[moe] total={summary['total_parameters'] / 1e9:.3f}B "
        f"trainable={summary['trainable_parameters'] / 1e6:.2f}M "
        f"({summary['trainable_percent']:.3f}%)"
    )

    model.config.use_cache = False
    model.gradient_checkpointing_enable(
        gradient_checkpointing_kwargs={"use_reentrant": False}
    )
    if hasattr(model, "enable_input_require_grads"):
        model.enable_input_require_grads()

    train = load_records(os.path.join(args.data_dir, "train.jsonl"), args.images_root)
    val = load_records(os.path.join(args.data_dir, "val.jsonl"), args.images_root)
    print(f"[data] train={len(train)} val={len(val)}")

    class MoeTrainer(Trainer):
        def compute_loss(
            self, model, inputs, return_outputs=False, num_items_in_batch=None
        ):
            labels = inputs.get("labels")
            attention_mask = inputs.get("attention_mask")
            route_mask = labels.eq(-100) if labels is not None else None
            if route_mask is not None and attention_mask is not None:
                route_mask = route_mask & attention_mask.bool()
            configure_routing_mask(model, route_mask)
            outputs = model(**inputs)
            base_loss = outputs.loss
            aux_loss, z_loss = router_losses(model)
            loss = base_loss
            if aux_loss is not None:
                loss = loss + args.router_aux_coef * aux_loss
            if z_loss is not None:
                loss = loss + args.router_z_coef * z_loss
            return (loss, outputs) if return_outputs else loss

    training_args = TrainingArguments(
        output_dir=args.out,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        warmup_ratio=args.warmup_ratio,
        lr_scheduler_type="cosine",
        logging_steps=1 if args.smoke else 5,
        save_strategy="no",  # save only the compact router/adapter checkpoint below
        eval_strategy="no" if args.smoke or not val else "epoch",
        bf16=True,
        tf32=True,
        optim="adamw_torch_fused",
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        max_steps=args.max_steps,
        seed=args.seed,
        report_to="none",
        remove_unused_columns=False,
        dataloader_pin_memory=False,
    )
    trainer = MoeTrainer(
        model=model,
        args=training_args,
        data_collator=build_collator(processor),
        train_dataset=train,
        eval_dataset=val if val and not args.smoke else None,
    )
    started = time.time()
    result = trainer.train()
    runtime = time.time() - started
    gpu_peak = torch.cuda.max_memory_allocated() if torch.cuda.is_available() else 0
    extra = {
        "runtime_seconds": runtime,
        "max_gpu_memory_bytes": gpu_peak,
        "train_metrics": result.metrics,
        "arguments": vars(args),
        "parameter_summary": summary,
        "calibrated_quantized_pack": args.quantized_pack,
    }
    save_adapter_checkpoint(
        model, args.out, spec=spec, replaced_modules=replaced, extra=extra
    )
    processor.save_pretrained(args.out)
    print(
        f"[done] compact checkpoint -> {args.out}; runtime={runtime / 60:.1f} min "
        f"peak_gpu={gpu_peak / 1e9:.2f} GB"
    )


if __name__ == "__main__":
    main()
