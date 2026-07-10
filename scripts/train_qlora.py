#!/usr/bin/env python3
"""
train_qlora.py — QLoRA fine-tune of Gemma 4 E4B (multimodal) for structured
skin-lesion *description* (not diagnosis).

Target model: google/gemma-4-E4B-it  (April-2026 Gemma 4, ~8B effective / 4B in
VRAM; multimodal). Loads 4-bit (bitsandbytes NF4), attaches LoRA adapters to the
attention + MLP projections of the language tower, and trains only the adapters.

VRAM budget: 4-bit base ~10-12 GB + activations; fits a single 23 GB A10G at
batch size 1 with grad-accum. Gradient checkpointing on.

Auth: reads HF_TOKEN from the environment (never hard-coded). The account must
have accepted the Gemma license on the model page first.

Data: train.jsonl / val.jsonl from generate_labels.py — chat-with-image records
    {"image": "images/<id>.jpg",
     "messages":[{"role":"user","content":[{"type":"image"},{"type":"text",...}]},
                 {"role":"assistant","content":[{"type":"text","text": target}]}]}

Usage (on the A10G, in the `derm` conda env with the training stack installed):
    python train_qlora.py \
        --model google/gemma-4-E4B-it \
        --data-dir ~/derm/labels --images-root ~/derm/data \
        --out ~/derm/out/e4b-derm-lora \
        --epochs 3 --batch-size 1 --grad-accum 8 --lr 2e-4
"""
import argparse, json, os


def get_model_class():
    """Prefer the multimodal class; fall back to image-text-to-text, then the
    Gemma3n-specific class for the 3n fallback model."""
    import transformers
    for name in ("AutoModelForMultimodalLM", "AutoModelForImageTextToText",
                 "Gemma3nForConditionalGeneration"):
        cls = getattr(transformers, name, None)
        if cls is not None:
            return name, cls
    raise RuntimeError("No suitable multimodal model class in this transformers version")


def load_records(path, images_root):
    from PIL import Image
    recs = []
    with open(path) as f:
        for line in f:
            r = json.loads(line)
            r["_img_abspath"] = os.path.join(images_root, r["image"])
            recs.append(r)
    return recs


def build_collator(processor):
    """Collate chat-with-image records into model inputs, masking the prompt so
    loss is computed only on the assistant target tokens."""
    import torch
    from PIL import Image

    def _render_messages(rec):
        # Rebuild the messages, substituting the actual PIL image for the
        # {"type":"image"} placeholder and keeping the assistant target.
        img = Image.open(rec["_img_abspath"]).convert("RGB")
        msgs = []
        for m in rec["messages"]:
            content = []
            for c in m["content"]:
                if c["type"] == "image":
                    content.append({"type": "image", "image": img})
                else:
                    content.append({"type": "text", "text": c["text"]})
            msgs.append({"role": m["role"], "content": content})
        return msgs, img

    def _prompt_token_len(msgs, img):
        """Number of text tokens in the prompt (everything up to the assistant
        turn), so we can mask them out of the loss. Rendered WITH the image so
        the soft image-token expansion is counted correctly."""
        prompt_msgs = [m for m in msgs if m["role"] != "assistant"]
        prompt_text = processor.apply_chat_template(
            prompt_msgs, add_generation_prompt=True, tokenize=False).strip()
        enc = processor(text=[prompt_text], images=[[img]],
                        return_tensors="pt", padding=False, truncation=True,
                        max_length=1024)
        return int(enc["input_ids"].shape[1])

    def collate(batch):
        texts, images_batch, prompt_lens = [], [], []
        for rec in batch:
            msgs, img = _render_messages(rec)
            # full templated text (prompt + target) for teacher forcing
            text = processor.apply_chat_template(
                msgs, add_generation_prompt=False, tokenize=False)
            texts.append(text.strip())
            images_batch.append([img])
            prompt_lens.append(_prompt_token_len(msgs, img))
        model_inputs = processor(
            text=texts, images=images_batch, return_tensors="pt",
            padding=True, truncation=True, max_length=1024)
        labels = model_inputs["input_ids"].clone()
        # 1) mask the PROMPT tokens per example -> loss only on assistant target.
        #    (left/right padding handled by locating non-pad positions.)
        pad_id = processor.tokenizer.pad_token_id
        attn = model_inputs.get("attention_mask")
        for i, plen in enumerate(prompt_lens):
            row = labels[i]
            if attn is not None:
                real_pos = (attn[i] == 1).nonzero(as_tuple=True)[0]
                # mask the first `plen` real (non-pad) tokens = the prompt span
                if len(real_pos) > 0:
                    row[real_pos[:plen]] = -100
            else:
                row[:plen] = -100
        # 2) mask pad tokens
        labels[labels == pad_id] = -100
        # 3) mask image/soft-media tokens if the processor exposes them
        for a in ("image_token_id", "boi_token_id", "eoi_token_id"):
            tid = getattr(processor, a, None) or getattr(
                getattr(processor, "tokenizer", object()), a, None)
            if isinstance(tid, int):
                labels[labels == tid] = -100
        model_inputs["labels"] = labels
        return model_inputs

    return collate


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="google/gemma-4-E4B-it")
    ap.add_argument("--data-dir", required=True)
    ap.add_argument("--images-root", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=float, default=3)
    ap.add_argument("--batch-size", type=int, default=1)
    ap.add_argument("--grad-accum", type=int, default=8)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--lora-r", type=int, default=16)
    ap.add_argument("--lora-alpha", type=int, default=16)
    ap.add_argument("--lora-dropout", type=float, default=0.05)
    ap.add_argument("--warmup-ratio", type=float, default=0.03)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--max-steps", type=int, default=-1)
    args = ap.parse_args()

    import torch
    import transformers
    from transformers import (AutoProcessor, BitsAndBytesConfig,
                              TrainingArguments, Trainer)
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training

    token = os.environ.get("HF_TOKEN")  # never hard-coded
    print(f"[env] transformers={transformers.__version__} torch={torch.__version__} "
          f"cuda={torch.cuda.is_available()} token={'set' if token else 'MISSING'}")

    # ---- 4-bit quantization config (QLoRA) --------------------------------
    bnb = BitsAndBytesConfig(
        load_in_4bit=True, bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16, bnb_4bit_use_double_quant=True)

    cls_name, ModelCls = get_model_class()
    print(f"[model] loading {args.model} with {cls_name} (4-bit NF4)")
    model = ModelCls.from_pretrained(
        args.model, quantization_config=bnb, torch_dtype=torch.bfloat16,
        device_map="auto", token=token, attn_implementation="eager")
    processor = AutoProcessor.from_pretrained(args.model, token=token)
    if processor.tokenizer.pad_token_id is None:
        processor.tokenizer.pad_token = processor.tokenizer.eos_token

    # NOTE: we deliberately do NOT call prepare_model_for_kbit_training here.
    # On this ~8B multimodal model it upcasts *every* param (all towers) to
    # fp32, which OOMs a 23 GB A10G on top of the 10 GB 4-bit base. Instead we
    # do the two things that actually matter for QLoRA by hand: enable gradient
    # checkpointing and make inputs require grad, and freeze the base weights.
    for p in model.parameters():
        p.requires_grad_(False)          # freeze all base weights (LoRA adds its own)
    model.gradient_checkpointing_enable(
        gradient_checkpointing_kwargs={"use_reentrant": False})
    if hasattr(model, "enable_input_require_grads"):
        model.enable_input_require_grads()
    model.config.use_cache = False

    # ---- LoRA on the LANGUAGE tower only ----------------------------------
    # Restrict to the text decoder's projections. Targeting by suffix name
    # (q/k/v/o/gate/up/down_proj) would also match vision/audio-tower linears
    # and inflate memory; we scope to modules under the language model.
    def _lang_lora_targets(m):
        """Return FULL module paths (PEFT matches these exactly) for the
        text-decoder projections only, so vision/audio-tower linears are left
        untouched. Falls back to suffix names if the heuristic finds nothing."""
        wanted = ("q_proj", "k_proj", "v_proj", "o_proj",
                  "gate_proj", "up_proj", "down_proj")
        names = []
        for n, mod in m.named_modules():
            if mod.__class__.__name__ in ("Linear", "Linear4bit", "Linear8bitLt") \
               and n.split(".")[-1] in wanted \
               and ("language_model" in n or "text_model" in n
                    or ".model.layers." in n):
                names.append(n)
        if not names:
            return list(wanted)
        return names

    lora = LoraConfig(
        r=args.lora_r, lora_alpha=args.lora_alpha, lora_dropout=args.lora_dropout,
        bias="none", task_type="CAUSAL_LM",
        target_modules=_lang_lora_targets(model))
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    # ---- data --------------------------------------------------------------
    train = load_records(os.path.join(args.data_dir, "train.jsonl"), args.images_root)
    val = load_records(os.path.join(args.data_dir, "val.jsonl"), args.images_root)
    print(f"[data] train={len(train)} val={len(val)}")
    collate = build_collator(processor)

    targs = TrainingArguments(
        output_dir=args.out, num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr, warmup_ratio=args.warmup_ratio,
        lr_scheduler_type="cosine", logging_steps=5, save_strategy="epoch",
        eval_strategy="epoch" if val else "no",
        per_device_eval_batch_size=1, bf16=True, optim="paged_adamw_8bit",
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        max_steps=args.max_steps, seed=args.seed, report_to="none",
        remove_unused_columns=False, dataloader_pin_memory=False)

    trainer = Trainer(
        model=model, args=targs, data_collator=collate,
        train_dataset=train, eval_dataset=val if val else None)

    trainer.train()
    trainer.save_model(args.out)          # saves LoRA adapter
    processor.save_pretrained(args.out)
    with open(os.path.join(args.out, "train_config.json"), "w") as f:
        json.dump(vars(args), f, indent=2)
    print(f"[done] adapter saved -> {args.out}")


if __name__ == "__main__":
    main()
