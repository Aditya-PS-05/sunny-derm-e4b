#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACK="sunny-pad-smolvlm-500m-mobile256-v2-gguf"
DEST="$ROOT/android/sunny_model_pack/src/main/assets/sunny_model_pack/$PACK"
HOST="${SUNNY_MODEL_SOURCE_HOST:-sunny-gpu}"

mkdir -p "$DEST"

stage_remote() {
    local remote="$1" name="$2"
    local part="$DEST/$name.part"
    scp "$HOST:$remote" "$part"
    mv "$part" "$DEST/$name"
}

stage_remote /home/ec2-user/models/smolvlm-derm-pad-Q8_0.gguf \
    sunny-pad-smolvlm-500m-Q8_0.gguf
stage_remote /home/ec2-user/models/mmproj-smolvlm-derm-pad-mobile256-F16.gguf \
    sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf

install -m 0644 \
    "$ROOT/exports/model_tiers/$PACK/derm.gbnf" \
    "$ROOT/exports/model_tiers/$PACK/manifest.json" \
    "$ROOT/licenses/THIRD_PARTY_NOTICES.txt" \
    "$ROOT/licenses/Apache-2.0.txt" \
    "$DEST/"

check_file() {
    local name="$1" expected_bytes="$2" expected_sha="$3"
    local file="$DEST/$name"
    local actual_bytes actual_sha
    actual_bytes="$(stat -c '%s' "$file")"
    actual_sha="$(sha256sum "$file" | cut -d' ' -f1)"
    if [[ "$actual_bytes" != "$expected_bytes" || "$actual_sha" != "$expected_sha" ]]; then
        echo "Integrity check failed for $name" >&2
        exit 1
    fi
}

check_file sunny-pad-smolvlm-500m-Q8_0.gguf 436805632 \
    36bfbd253ea5edec715a510d97546085001c843f6616aeef5bfa818b36ce69df
check_file sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf 197108288 \
    c084c1c8259c3eb239f0303e2ba10d7db6585a22c1f44b7880774f331a00ce9a
check_file derm.gbnf 303 \
    ffc98c058fdf0f34e9d529231e7572be5ee77893a997584e1670cdef31940f09
check_file THIRD_PARTY_NOTICES.txt 2088 \
    ded7a876f2e6501c7a263e3fafaef98684165ae325eac5c81fcb8b5aeb352866
check_file Apache-2.0.txt 11357 \
    84829002701217076a39a84808ec52e45088ddbf9f6623896e5550becd8e09be
check_file manifest.json 3696 \
    e6f264af36b4b16f25e50bc37520ace15ccbfb8a9084377650b57f6df035fa21

echo "Staged and verified $PACK in $DEST"
