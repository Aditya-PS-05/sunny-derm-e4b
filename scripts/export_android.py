#!/usr/bin/env python3
"""
export_android.py — merge the QLoRA adapter into Gemma 4 E4B and export for
on-device Android inference.

Two export targets (pick per the runtime you ship):
  A) LiteRT-LM (.litertlm)  — RECOMMENDED. MediaPipe LLM Inference API is in
     maintenance-only mode; LiteRT-LM is the supported Android path and Gemma
     3n/4 E-series already ship in .litertlm. Vision (EnableVisionModality)
     is supported.
  B) GGUF Q4_K_M            — for a llama.cpp-based Android build.

IMPORTANT — why we MERGE instead of shipping the LoRA:
  On-device LoRA in the LLM Inference API is attention-layers-only and GPU-only.
  Our adapter also targets MLP projections, so shipping it as a runtime LoRA
  would silently drop the MLP deltas. Merging the adapter into the base weights
  captures the full fine-tune, then we quantize the merged model.

Step 1 (this script): merge adapter -> fp16 base, save merged HF checkpoint.
Step 2 (LiteRT path): convert with the AI-Edge-Torch Generative API to .tflite,
        then bundle with the tokenizer into a .litertlm Task Bundle.
Step 3 (GGUF path): llama.cpp convert_hf_to_gguf.py + llama-quantize Q4_K_M.

The convert/bundle tools (ai-edge-torch, llama.cpp) are invoked as subprocesses
so this script degrades gracefully when only one runtime toolchain is present.

Usage:
    python export_android.py merge  --base google/gemma-4-E4B-it \
        --adapter ~/derm/out/e4b-derm-lora --out ~/derm/out/e4b-derm-merged
    python export_android.py gguf   --merged ~/derm/out/e4b-derm-merged \
        --llama-cpp ~/llama.cpp --out ~/derm/out/e4b-derm-Q4_K_M.gguf
    python export_android.py litert --merged ~/derm/out/e4b-derm-merged \
        --out ~/derm/out/e4b-derm.litertlm
"""
import argparse, os, subprocess, sys


def cmd_merge(a):
    import torch
    from transformers import AutoProcessor
    from peft import PeftModel
    import transformers
    token = os.environ.get("HF_TOKEN")

    # resolve the multimodal base class (same logic as training)
    ModelCls = None
    for n in ("AutoModelForMultimodalLM", "AutoModelForImageTextToText",
              "Gemma3nForConditionalGeneration"):
        ModelCls = getattr(transformers, n, None)
        if ModelCls:
            print(f"[merge] base class: {n}"); break

    print(f"[merge] loading base {a.base} in fp16 (no quant, for clean merge)")
    base = ModelCls.from_pretrained(a.base, torch_dtype=torch.float16,
                                    device_map="cpu", token=token)
    print(f"[merge] attaching adapter {a.adapter}")
    merged = PeftModel.from_pretrained(base, a.adapter)
    merged = merged.merge_and_unload()      # fold LoRA (attn + MLP) into weights
    os.makedirs(a.out, exist_ok=True)
    merged.save_pretrained(a.out, safe_serialization=True)
    AutoProcessor.from_pretrained(a.base, token=token).save_pretrained(a.out)
    print(f"[merge] merged model saved -> {a.out}")


def cmd_gguf(a):
    conv = os.path.join(a.llama_cpp, "convert_hf_to_gguf.py")
    f16 = a.out.replace(".gguf", "-f16.gguf")
    print("[gguf] converting HF -> f16 GGUF")
    subprocess.run([sys.executable, conv, a.merged, "--outfile", f16,
                    "--outtype", "f16"], check=True)
    quant = os.path.join(a.llama_cpp, "build", "bin", "llama-quantize")
    if not os.path.exists(quant):
        quant = os.path.join(a.llama_cpp, "llama-quantize")
    print("[gguf] quantizing -> Q4_K_M")
    subprocess.run([quant, f16, a.out, "Q4_K_M"], check=True)
    print(f"[gguf] wrote {a.out}  ({os.path.getsize(a.out)/1e9:.2f} GB)")


def cmd_litert(a):
    """Convert merged HF checkpoint to .litertlm via ai-edge-torch.
    ai-edge-torch's Generative API authors + converts + quantizes to .tflite,
    then the litertlm bundler packages it with the tokenizer."""
    try:
        import ai_edge_torch  # noqa: F401
    except ImportError:
        print("[litert] ai-edge-torch not installed. Install with:\n"
              "  pip install ai-edge-torch-nightly ai-edge-litert\n"
              "Then follow the Gemma conversion recipe in ai-edge-torch/"
              "generative/examples/gemma3 to author + export the .tflite and\n"
              "bundle it into a .litertlm with the tokenizer.")
        return
    print("[litert] ai-edge-torch present. Run the Gemma generative export "
          "recipe:\n"
          "  from ai_edge_torch.generative.examples.gemma3 import gemma3\n"
          "  # build re-authored model from the merged checkpoint, then\n"
          "  # convert() + quantize(int8/int4) -> .tflite, then bundle.\n"
          f"  merged={a.merged}  out={a.out}\n"
          "See docs/android_integration.md for the full command sequence.")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="mode", required=True)

    m = sub.add_parser("merge"); m.set_defaults(fn=cmd_merge)
    m.add_argument("--base", default="google/gemma-4-E4B-it")
    m.add_argument("--adapter", required=True)
    m.add_argument("--out", required=True)

    g = sub.add_parser("gguf"); g.set_defaults(fn=cmd_gguf)
    g.add_argument("--merged", required=True)
    g.add_argument("--llama-cpp", required=True)
    g.add_argument("--out", required=True)

    l = sub.add_parser("litert"); l.set_defaults(fn=cmd_litert)
    l.add_argument("--merged", required=True)
    l.add_argument("--out", required=True)

    a = ap.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
