#!/usr/bin/env bash
# Push the complete PAD-trained pack to the app's debug-only external model folder.
set -euo pipefail

PKG="com.sunny.skin"
src="${1:-${SUNNY_PAD_PACK_DIR:-}}"
dest="/sdcard/Android/data/$PKG/files/models"

if [ -z "$src" ] || [ ! -d "$src" ]; then
    echo "Usage: $0 /path/to/sunny-pad-smolvlm-500m-mobile256-v2-gguf" >&2
    exit 1
fi
check_file() {
    local file="$1" expected_bytes="$2" expected_sha="$3"
    if [ ! -f "$src/$file" ]; then
        echo "ERROR: incomplete Sunny Offline pack; missing $src/$file" >&2
        exit 1
    fi
    local actual_bytes actual_sha
    actual_bytes="$(stat -c '%s' "$src/$file")"
    actual_sha="$(sha256sum "$src/$file" | cut -d' ' -f1)"
    if [ "$actual_bytes" != "$expected_bytes" ] || [ "$actual_sha" != "$expected_sha" ]; then
        echo "ERROR: integrity check failed for $src/$file" >&2
        exit 1
    fi
}

check_file manifest.json 3696 \
    e6f264af36b4b16f25e50bc37520ace15ccbfb8a9084377650b57f6df035fa21
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
if ! adb get-state >/dev/null 2>&1; then
    echo "ERROR: no device. Connect one and check 'adb devices'." >&2
    exit 1
fi

echo "Ensuring $dest exists on device…"
adb shell mkdir -p "$dest"
echo "Pushing verified Sunny Offline pack (~605 MiB)…"
adb push "$src/." "$dest/"

echo "Done. Settings › AI Model should show Sunny Offline as installed."
echo "The app verifies exact file sizes before enabling libsunny_moe.so."
