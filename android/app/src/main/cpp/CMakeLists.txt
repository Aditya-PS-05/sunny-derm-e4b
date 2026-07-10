cmake_minimum_required(VERSION 3.22.1)
project(sunny_llama CXX C)

# Vendored llama.cpp (scripts/vendor_llama.sh). Build ONLY the pieces we need:
# ggml + llama + the standalone mtmd library (LLAMA_BUILD_MTMD) — no tools,
# examples, tests, server or curl. mtmd links ggml+llama only, so common is off.
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD     ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)   # NDK has no drop-in libomp
# mtmd video support shells out to ffmpeg via posix_spawn (bionic API 28+) and is
# useless on Android — we do single-image inference. Off keeps us minSdk-26 clean.
set(MTMD_VIDEO           OFF CACHE BOOL "" FORCE)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp not vendored. Run scripts/vendor_llama.sh first "
        "(see android/README.md § native model).")
endif()

add_subdirectory(llama.cpp)

add_library(sunny_llama SHARED sunny_llama.cpp)

target_include_directories(sunny_llama PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
    llama.cpp/tools/mtmd)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(sunny_llama
    llama
    mtmd
    ggml
    ${log-lib}
    ${jnigraphics-lib}
    android)
