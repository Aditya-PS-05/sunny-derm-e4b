#!/usr/bin/env bash
# Push the LOCAL model weights (already in this repo at exports/model_on_host/)
# onto a connected device/emulator so the app runs the REAL llama.cpp model —
# no download involved.
#
# Prereqs: app installed once (creates its external dir), a device via `adb devices`.
#
#   ./scripts/push_weights_to_device.sh
set -euo pipefail

PKG="com.sunny.skin"
here="$(cd "$(dirname "$0")/.." && pwd)"
src="$here/../exports/model_on_host"          # weights live one level up from android/
dest="/sdcard/Android/data/$PKG/files/models"  # app external files dir (app can read)

LM="e4b-derm-Q4_K_M.gguf"
MMPROJ="mmproj-e4b-derm-f16.gguf"

for f in "$LM" "$MMPROJ"; do
    if [ ! -f "$src/$f" ]; then
        echo "ERROR: $src/$f not found. Pull it first (../pull_weights.sh)." >&2
        exit 1
    fi
done

if ! adb get-state >/dev/null 2>&1; then
    echo "ERROR: no device. Connect one and check 'adb devices'." >&2
    exit 1
fi

echo "Ensuring $dest exists on device…"
adb shell mkdir -p "$dest"

echo "Pushing $LM (5.0 GB)…"
adb push "$src/$LM" "$dest/$LM"
echo "Pushing $MMPROJ (~990 MB)…"
adb push "$src/$MMPROJ" "$dest/$MMPROJ"

echo "Done. On device: Settings › AI Model should now show 'Model installed'."
echo "(Build the app with the native engine: ./gradlew installDebug -PwithLlama)"
