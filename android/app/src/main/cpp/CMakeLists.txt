cmake_minimum_required(VERSION 3.22.1)
project(sunny_moe LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned by scripts/vendor_sunny_moe_runtime.sh. Build only the SmolVLM text,
# vision and CPU kernels needed by the stable JNI library.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
# Build Android CPU variants and let ggml select the fastest one supported by
# the phone at runtime. The previous baseline armv8-a build discarded the
# DOTPROD and FP16 vector instructions present on Snapdragon 695/Dimensity 920,
# which made even the 500M text decoder unreasonably slow.
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL ON CACHE BOOL "" FORCE)
set(GGML_OPENCL_TARGET_VERSION "300" CACHE STRING "" FORCE)
set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
# The Adreno-specialized kernels corrupt SmolVLM activations on the POCO X4
# Pro's 2021 Adreno 619 driver. Use the portable OpenCL kernels instead.
set(GGML_OPENCL_USE_ADRENO_KERNELS OFF CACHE BOOL "" FORCE)

# Link against the open-source Khronos loader only at build time. The optional
# ggml OpenCL backend resolves Android's public vendor libOpenCL at runtime.
# Keeping it as a dynamically loaded backend means unsupported devices retain
# the CPU fallback instead of failing to load Sunny's JNI library.
set(SUNNY_OPENCL_SDK "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.opencl-sdk")
if(NOT EXISTS "${SUNNY_OPENCL_SDK}/include/CL/cl.h" OR
   NOT EXISTS "${SUNNY_OPENCL_SDK}/lib/libOpenCL.so")
    message(FATAL_ERROR
        "OpenCL link SDK is missing. Run scripts/prepare_opencl_sdk.sh")
endif()
set(OpenCL_INCLUDE_DIR "${SUNNY_OPENCL_SDK}/include" CACHE PATH "" FORCE)
set(OpenCL_LIBRARY "${SUNNY_OPENCL_SDK}/lib/libOpenCL.so" CACHE FILEPATH "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
endif()

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "SmolVLM runtime source is missing. Run scripts/vendor_sunny_moe_runtime.sh")
endif()

add_subdirectory(llama.cpp build-llama)

# SmolVLM's desktop processor expands every photo to a 2048 px tiled canvas.
# The Android projector is exported for one fixed 256 px overview, so refined
# tiles would be both incompatible and prohibitively slow on mobile CPUs.
target_compile_definitions(mtmd PRIVATE SUNNY_MOBILE_GLOBAL_IMAGE_ONLY=1)

add_library(sunny_moe SHARED sunny_moe.cpp)
target_compile_options(sunny_moe PRIVATE -O3 -fvisibility=hidden)
target_include_directories(sunny_moe PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
    llama.cpp/src
    llama.cpp/tools/mtmd)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
target_link_libraries(sunny_moe PRIVATE
    llama
    mtmd
    ggml
    ${log-lib}
    ${jnigraphics-lib}
    android)
