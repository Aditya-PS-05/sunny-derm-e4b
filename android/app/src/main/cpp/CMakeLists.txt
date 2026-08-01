cmake_minimum_required(VERSION 3.22.1)
project(sunny_moe LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned by scripts/vendor_sunny_moe_runtime.sh. Build only the SmolVLM text,
# vision and CPU kernels needed by the JNI library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
endif()

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "Sunny-MoE runtime source is missing. Run scripts/vendor_sunny_moe_runtime.sh")
endif()

add_subdirectory(llama.cpp build-llama)

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
