#!/bin/bash
# Pull the trained model weights from the GPU host into this project folder.
# Run this from YOUR machine (it has the SSH key + host alias; the agent sandbox
# does not). Replace 'gpu' with your SSH alias for the box if different.
set -euo pipefail
HOST="${1:-gpu}"                 # usage: ./pull_weights.sh <ssh-alias>
REMOTE=ec2-user@"$HOST":/home/ec2-user/derm/out
DEST="$(cd "$(dirname "$0")" && pwd)/exports/model_on_host"
mkdir -p "$DEST/adapter"

echo ">> Q4_K_M language model (5.0 GB) + vision projector (990 MB)"
rsync -avP "$REMOTE/e4b-derm-Q4_K_M.gguf" \
           "$REMOTE/mmproj-e4b-derm-f16.gguf" "$DEST/"
echo ">> LoRA adapter (134 MB) + config"
rsync -avP "$REMOTE/e4b-derm-lora/" "$DEST/adapter/"
echo ">> (optional) full fp16 merged checkpoint (~15 GB) — uncomment to pull:"
echo "#   rsync -avP $REMOTE/e4b-derm-merged/ $DEST/merged/"

echo ">> verifying sizes:"
ls -lh "$DEST"/*.gguf "$DEST/adapter/adapter_model.safetensors"
echo "Expected: Q4_K_M 5302272736 B | mmproj 990372192 B | adapter 139602808 B"
