#!/usr/bin/env bash
# Prepare pinned Khronos OpenCL headers + an Android link library for CMake.
# The generated loader is not packaged; compatible phones provide public
# libOpenCL.so, and ggml loads its OpenCL backend optionally at runtime.
set -euo pipefail

HEADERS_COMMIT="703b18b4895013cc258ec39222e10c41600e5187"
LOADER_COMMIT="18fdcd58286376124f938948aa8ed156079c1c16"
NDK_VERSION="28.2.13676358"

root="$(cd "$(dirname "$0")/.." && pwd)"
destination="$root/.opencl-sdk"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ndk="${ANDROID_NDK_HOME:-$sdk_root/ndk/$NDK_VERSION}"

if [[ -f "$destination/include/CL/cl.h" && -f "$destination/lib/libOpenCL.so" ]]; then
    echo "OpenCL link SDK is ready at $destination"
    exit 0
fi
if [[ ! -f "$ndk/build/cmake/android.toolchain.cmake" ]]; then
    echo "Android NDK $NDK_VERSION was not found under $ndk" >&2
    exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git clone --quiet https://github.com/KhronosGroup/OpenCL-Headers.git "$work/headers"
git -C "$work/headers" checkout --quiet "$HEADERS_COMMIT"
git clone --quiet https://github.com/KhronosGroup/OpenCL-ICD-Loader.git "$work/loader"
git -C "$work/loader" checkout --quiet "$LOADER_COMMIT"

cmake -S "$work/loader" -B "$work/build" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=26 \
    -DANDROID_STL=c++_shared \
    -DOPENCL_ICD_LOADER_HEADERS_DIR="$work/headers"
cmake --build "$work/build" --target OpenCL

mkdir -p "$destination/include" "$destination/lib"
cp -R "$work/headers/CL" "$destination/include/"
cp "$work/build/libOpenCL.so" "$destination/lib/"
echo "OpenCL link SDK is ready at $destination"
