#!/usr/bin/env bash
# Fetch the pinned SmolVLM2/ggml runtime and apply Sunny's mixed dense/MoE
# loader patch. The source is intentionally untracked; the commit and patch are
# tracked so CI and release builds are reproducible.
set -euo pipefail

LLAMA_COMMIT="72874f559c598b8f89fbb24864868337cf5afb4c"
ARCHIVE="https://github.com/ggml-org/llama.cpp/archive/${LLAMA_COMMIT}.tar.gz"

root="$(cd "$(dirname "$0")/.." && pwd)"
destination="$root/app/src/main/cpp/llama.cpp"
patch="$root/patches/llama-sunny-moe-mixed-ffn.patch"

if [[ -d "$destination" ]]; then
    if grep -q "Sunny-MoE keeps the first half dense" \
        "$destination/src/models/llama.cpp" 2>/dev/null; then
        echo "Sunny-MoE runtime source is already ready at $destination"
        exit 0
    fi
    echo "Refusing to replace existing runtime source: $destination" >&2
    exit 1
fi
mkdir -p "$destination"
curl --fail --location --silent --show-error "$ARCHIVE" \
    | tar -xz --strip-components=1 -C "$destination"
patch --directory="$destination" --strip=1 --dry-run < "$patch"
patch --directory="$destination" --strip=1 < "$patch"
echo "Sunny-MoE runtime source ready at $destination"
