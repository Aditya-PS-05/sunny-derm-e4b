#!/usr/bin/env bash
# Push a complete Sunny-MoE pack to the app's debug-only external model folder.
set -euo pipefail

PKG="com.sunny.skin"
src="${1:-${SUNNY_MOE_PACK_DIR:-}}"
dest="/sdcard/Android/data/$PKG/files/models"

if [ -z "$src" ] || [ ! -d "$src" ]; then
    echo "Usage: $0 /path/to/sunny-moe-2.2b-v4-gguf" >&2
    exit 1
fi
check_file() {
    local file="$1" expected_bytes="$2" expected_sha="$3"
    if [ ! -f "$src/$file" ]; then
        echo "ERROR: incomplete Sunny-MoE pack; missing $src/$file" >&2
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

check_file manifest.json 1037 \
    1a8ce0012e240b3e2e3c6b8a2eec376396986b4fbda858dabd8ccc8b686c13ad
check_file sunny-moe-text-Q4_K_M.gguf 2210067936 \
    7e7aa651986473c94988ac99978cd5c51a7bb7b8a1e4092cd41ed835c38fd28b
check_file sunny-moe-mmproj-F16.gguf 872300704 \
    c4149a795d2c4af070d94e2130e2e1026d96bb912bbac1595b6fd12d376b91f4
if ! adb get-state >/dev/null 2>&1; then
    echo "ERROR: no device. Connect one and check 'adb devices'." >&2
    exit 1
fi

echo "Ensuring $dest exists on device…"
adb shell mkdir -p "$dest"
echo "Pushing verified Sunny-MoE pack (~3.08 GB)…"
adb push "$src/." "$dest/"

echo "Done. Settings › AI Model should show Sunny MoE as installed."
echo "The app verifies exact file sizes before enabling libsunny_moe.so."
