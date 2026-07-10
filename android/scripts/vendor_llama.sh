#!/usr/bin/env bash
# Vendor llama.cpp for the native mtmd bridge. Clones a pinned commit into
# app/src/main/cpp/llama.cpp so the CMake externalNativeBuild can build it.
#
#   ./scripts/vendor_llama.sh
#
# Then build the app WITH the native model:
#   ./gradlew assembleDebug -PwithLlama
set -euo pipefail

# Pinned to the commit whose mtmd/llama API this bridge was written against.
# Bump deliberately and re-verify sunny_llama.cpp against the new headers.
LLAMA_COMMIT="72874f559c598b8f89fbb24864868337cf5afb4c"
REPO="https://github.com/ggml-org/llama.cpp.git"

here="$(cd "$(dirname "$0")/.." && pwd)"
dest="$here/app/src/main/cpp/llama.cpp"

if [ -d "$dest/.git" ]; then
    echo "llama.cpp already present at $dest"
    git -C "$dest" fetch --depth 1 origin "$LLAMA_COMMIT"
    git -C "$dest" checkout -q "$LLAMA_COMMIT"
else
    echo "Cloning llama.cpp @ $LLAMA_COMMIT ..."
    git clone "$REPO" "$dest"
    git -C "$dest" checkout -q "$LLAMA_COMMIT"
fi
echo "Done. Build with:  ./gradlew assembleDebug -PwithLlama"
