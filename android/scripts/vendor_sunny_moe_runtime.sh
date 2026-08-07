#!/usr/bin/env bash
# Fetch the pinned llama.cpp/mtmd runtime used by the PAD-trained Gemma 4 model.
# The legacy script name is retained for existing CI and developer commands.
set -euo pipefail

LLAMA_COMMIT="72874f559c598b8f89fbb24864868337cf5afb4c"
ARCHIVE="https://github.com/ggml-org/llama.cpp/archive/${LLAMA_COMMIT}.tar.gz"

root="$(cd "$(dirname "$0")/.." && pwd)"
destination="$root/app/src/main/cpp/llama.cpp"
patch_file="$root/patches/llama-sunny-moe-mixed-ffn.patch"

runtime_ready() {
    grep -q "LLM_ARCH_GEMMA4" "$destination/src/llama-arch.h" 2>/dev/null &&
        grep -q "Sunny-MoE keeps the first half dense" \
            "$destination/src/models/llama.cpp" 2>/dev/null &&
        grep -q "SUNNY_MOBILE_GLOBAL_IMAGE_ONLY" \
            "$destination/tools/mtmd/mtmd-image.cpp" 2>/dev/null
}

if [[ -d "$destination" ]]; then
    if runtime_ready; then
        echo "Gemma 4 runtime source is already ready at $destination"
        exit 0
    fi
    echo "Refusing to replace existing runtime source: $destination" >&2
    exit 1
fi
mkdir -p "$destination"
curl --fail --location --silent --show-error "$ARCHIVE" \
    | tar -xz --strip-components=1 -C "$destination"
git -C "$root" apply --unsafe-paths \
    --directory=app/src/main/cpp/llama.cpp "$patch_file"
if ! runtime_ready; then
    echo "Pinned Sunny llama.cpp patch did not apply completely" >&2
    exit 1
fi
echo "Gemma 4 runtime source ready at $destination"
