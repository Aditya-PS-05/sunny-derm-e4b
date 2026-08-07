#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <sys/system_properties.h>

#include <chrono>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "SunnyPad"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// The JNI/library names remain stable so existing app installations can upgrade
// in place. The loaded model is the PAD-UFES-20-trained SmolVLM 500M pack.
constexpr const char * MODEL_FILE = "sunny-pad-smolvlm-500m-Q8_0.gguf";
constexpr const char * DEBUG_Q4_MODEL_FILE = "sunny-pad-smolvlm-500m-Q4_K_M.gguf";
constexpr const char * MMPROJ_FILE = "sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf";
constexpr const char * GRAMMAR_FILE = "derm.gbnf";
constexpr const char * SAFETY_LINE =
    "Safety: This is a visual description only, not a diagnosis — see a clinician for any concern.";

struct SunnyCtx {
    llama_model * model = nullptr;
    llama_context * lctx = nullptr;
    mtmd_context * mctx = nullptr;
    std::string grammar;
    int n_threads = 4;
};

using Clock = std::chrono::steady_clock;

long elapsed_ms(const Clock::time_point & start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        Clock::now() - start).count();
}

std::once_flag backend_once;

std::string system_property(const char * name) {
    char value[PROP_VALUE_MAX]{};
    __system_property_get(name, value);
    return value;
}

bool requires_cpu_only_backend() {
    const std::string soc_model = system_property("ro.soc.model");
    return soc_model.find("SM6375") != std::string::npos ||
        soc_model.find("MT6877") != std::string::npos;
}

bool accelerated_vision_is_safe(ggml_backend_dev_t accelerator) {
    if (!accelerator) return false;
    // The SM6375 / Adreno 619 OpenCL 2.0 driver (2021 compiler) corrupts Q8
    // projector output and can freeze Android's compositor. MT6877's Mali-G68
    // path is also rejected by this llama.cpp revision. Both CPUs provide
    // DOTPROD and FP16 vector instructions, so use that deterministic backend.
    if (requires_cpu_only_backend()) {
        LOGI("Disabling GPU vision on incompatible SoC %s",
             system_property("ro.soc.model").c_str());
        return false;
    }
    return true;
}

void log_cb(ggml_log_level level, const char * text, void *) {
    const bool accelerator_message = text && (
        std::strstr(text, "OpenCL") || std::strstr(text, "opencl") ||
        std::strstr(text, "offload") || std::strstr(text, "CLIP using"));
    const bool stage_message = text && (
        std::strstr(text, "encoding image") ||
        std::strstr(text, "image slice encoded") ||
        std::strstr(text, "decoding image batch") ||
        std::strstr(text, "image decoded"));
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN ||
        accelerator_message || stage_message) {
        LOGI("%s", text);
    }
}

int configured_threads(int requested) {
    char value[PROP_VALUE_MAX]{};
    if (__system_property_get("debug.sunny.threads", value) > 0) {
        const long override = std::strtol(value, nullptr, 10);
        if (override >= 1 && override <= 8) {
            LOGI("Using debug.sunny.threads=%ld", override);
            return static_cast<int>(override);
        }
        LOGI("Ignoring invalid debug.sunny.threads=%s", value);
    }
    return requested > 0 ? requested : 4;
}

std::string jstr(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool bitmap_to_rgb(JNIEnv * env, jobject bitmap, std::vector<unsigned char> & output,
                   uint32_t & width, uint32_t & height) {
    AndroidBitmapInfo info{};
    const int info_result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (info_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed: %d", info_result);
        return false;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("unsupported bitmap format: %d (%ux%u, stride %u)",
             info.format, info.width, info.height, info.stride);
        return false;
    }
    void * pixels = nullptr;
    const int lock_result = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (lock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_lockPixels failed: %d", lock_result);
        return false;
    }
    width = info.width;
    height = info.height;
    output.resize(static_cast<size_t>(width) * height * 3);
    const auto * source = static_cast<const unsigned char *>(pixels);
    for (uint32_t y = 0; y < height; ++y) {
        const auto * row = source + static_cast<size_t>(y) * info.stride;
        for (uint32_t x = 0; x < width; ++x) {
            const auto * pixel = row + static_cast<size_t>(x) * 4;
            const size_t destination = (static_cast<size_t>(y) * width + x) * 3;
            output[destination] = pixel[0];
            output[destination + 1] = pixel[1];
            output[destination + 2] = pixel[2];
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

void free_context(SunnyCtx * ctx) {
    if (!ctx) return;
    if (ctx->mctx) mtmd_free(ctx->mctx);
    if (ctx->lctx) llama_free(ctx->lctx);
    if (ctx->model) llama_model_free(ctx->model);
    delete ctx;
}

bool schema_complete(const std::string & output) {
    static constexpr const char * fields[] = {
        "Lesion Type:", "Colour:", "Symmetry:", "Borders:", "Texture:", "Summary:",
    };
    size_t cursor = 0;
    const size_t thought = output.find("<|channel>thought");
    if (thought != std::string::npos) {
        const size_t answer = output.rfind("<channel|>");
        if (answer == std::string::npos || answer < thought) return false;
        cursor = answer;
    }
    for (const char * field : fields) {
        cursor = output.find(field, cursor);
        if (cursor == std::string::npos) return false;
        cursor += std::char_traits<char>::length(field);
    }
    return output.find(SAFETY_LINE, cursor) != std::string::npos;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeInit(
        JNIEnv * env, jobject, jstring packDirectory, jstring nativeLibraryDirectory,
        jint threads) {
    const auto init_started = Clock::now();
    const std::string native_library_directory = jstr(env, nativeLibraryDirectory);
    std::call_once(backend_once, [&native_library_directory] {
        llama_log_set(log_cb, nullptr);
        mtmd_helper_log_set(log_cb, nullptr);
        LOGI("Loading inference backends from %s", native_library_directory.c_str());
        if (requires_cpu_only_backend()) {
            // These SoCs are ARMv8.2 with DOTPROD + FP16 vector arithmetic.
            // Loading the exact CPU plugin avoids spending ~5 seconds starting
            // an OpenCL driver which must not receive work anyway.
            const auto cpu_backend = std::filesystem::path(native_library_directory) /
                "libggml-cpu-android_armv8.2_2.so";
            if (!ggml_backend_load(cpu_backend.c_str())) {
                LOGE("Could not load optimized CPU backend %s", cpu_backend.c_str());
                ggml_backend_load_all_from_path(native_library_directory.c_str());
            } else {
                LOGI("Loaded CPU-only ARMv8.2 backend for %s",
                     system_property("ro.soc.model").c_str());
            }
        } else {
            // Runtime scoring selects the fastest compatible CPU variant and
            // registers optional accelerators on other phones.
            ggml_backend_load_all_from_path(native_library_directory.c_str());
        }
        llama_backend_init();
        LOGI("llama runtime: %s", llama_print_system_info());
    });

    ggml_backend_dev_t accelerator =
        ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU);
    const bool use_accelerated_vision = accelerated_vision_is_safe(accelerator);
    if (use_accelerated_vision) {
        LOGI("Using accelerator device for vision: %s", ggml_backend_dev_name(accelerator));
    } else {
        LOGI("No compatible accelerator found; using CPU fallback");
    }

    const std::filesystem::path directory(jstr(env, packDirectory));
    std::filesystem::path model_path = directory / MODEL_FILE;
    if (system_property("debug.sunny.text_quant") == "q4km") {
        const auto debug_model_path = directory / DEBUG_Q4_MODEL_FILE;
        if (std::filesystem::is_regular_file(debug_model_path)) {
            model_path = debug_model_path;
            LOGI("Using debug Q4_K_M text model");
        } else {
            LOGI("debug.sunny.text_quant=q4km ignored; model file is missing");
        }
    }
    const std::filesystem::path mmproj_path = directory / MMPROJ_FILE;
    const std::filesystem::path grammar_path = directory / GRAMMAR_FILE;
    if (!std::filesystem::is_regular_file(model_path) ||
        !std::filesystem::is_regular_file(mmproj_path) ||
        !std::filesystem::is_regular_file(grammar_path)) {
        LOGE("Sunny PAD model pack is incomplete in %s", directory.c_str());
        return 0;
    }

    auto * ctx = new SunnyCtx();
    ctx->n_threads = configured_threads(threads);
    std::ifstream grammar_stream(grammar_path);
    ctx->grammar.assign(
        std::istreambuf_iterator<char>(grammar_stream),
        std::istreambuf_iterator<char>());
    if (ctx->grammar.empty()) {
        LOGE("failed to read %s", grammar_path.c_str());
        free_context(ctx);
        return 0;
    }
    llama_model_params model_params = llama_model_default_params();
    // Text generation stays on CPU. Vision is offloaded only on devices whose
    // accelerator path passes the guard above.
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = true;
    ctx->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!ctx->model) {
        LOGE("failed to load %s", model_path.c_str());
        free_context(ctx);
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    // The mobile projector uses one 256 px global image (16 visual tokens), so the
    // schema prompt and bounded response fit comfortably in 1024 tokens.
    context_params.n_ctx = 1024;
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = ctx->n_threads;
    context_params.n_threads_batch = ctx->n_threads;
    ctx->lctx = llama_init_from_model(ctx->model, context_params);
    if (!ctx->lctx) {
        LOGE("failed to initialize the Sunny PAD context");
        free_context(ctx);
        return 0;
    }

    mtmd_context_params vision_params = mtmd_context_params_default();
    vision_params.use_gpu = use_accelerated_vision;
    // Adreno 619 advertises subgroup shuffle support, but its 2021 OpenCL
    // compiler rejects llama.cpp's flash-attention kernel and leaves the GPU
    // queue waiting forever. Use the regular attention graph for vision only;
    // text attention remains AUTO on the CPU context above.
    vision_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    vision_params.print_timings = false;
    vision_params.n_threads = ctx->n_threads;
    // Avoid spending a full vision pass before the user's first real image.
    vision_params.warmup = false;
    ctx->mctx = mtmd_init_from_file(mmproj_path.c_str(), ctx->model, vision_params);
    if (!ctx->mctx) {
        LOGE("failed to load %s", mmproj_path.c_str());
        free_context(ctx);
        return 0;
    }
    LOGI("Sunny PAD SmolVLM 500M runtime initialized in %ld ms with %d threads",
         elapsed_ms(init_started), ctx->n_threads);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeDescribe(
        JNIEnv * env, jobject, jlong handle, jobject bitmap, jstring prompt,
        jint maxNewTokens, jfloat) {
    auto * ctx = reinterpret_cast<SunnyCtx *>(handle);
    if (!ctx || !bitmap) return env->NewStringUTF("");

    const auto describe_started = Clock::now();
    std::vector<unsigned char> rgb;
    uint32_t width = 0;
    uint32_t height = 0;
    if (!bitmap_to_rgb(env, bitmap, rgb, width, height)) {
        LOGE("expected an RGBA_8888 bitmap");
        return env->NewStringUTF("");
    }
    mtmd_bitmap * image = mtmd_bitmap_init(width, height, rgb.data());
    if (!image) return env->NewStringUTF("");

    // Match the SmolVLM image-first template used during fine-tuning:
    // <|im_start|>User:<image>{prompt}<end_of_utterance>\nAssistant:
    const std::string rendered =
        std::string("<|im_start|>User:") + mtmd_default_marker() + jstr(env, prompt) +
        "<end_of_utterance>\nAssistant:";
    mtmd_input_text input_text{};
    input_text.text = rendered.c_str();
    input_text.add_special = false;
    input_text.parse_special = true;

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    const mtmd_bitmap * images[] = {image};
    if (!chunks || mtmd_tokenize(ctx->mctx, chunks, &input_text, images, 1) != 0) {
        if (chunks) mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(image);
        LOGE("failed to tokenize the Sunny PAD multimodal prompt");
        return env->NewStringUTF("");
    }

    LOGI("Analyzing %ux%u image as %zu multimodal chunks",
         width, height, mtmd_input_chunks_size(chunks));

    llama_pos n_past = 0;
    const auto prompt_started = Clock::now();
    int32_t evaluated = 0;
    const size_t chunk_count = mtmd_input_chunks_size(chunks);
    for (size_t index = 0; index < chunk_count; ++index) {
        const auto * chunk = mtmd_input_chunks_get(chunks, index);
        const auto chunk_started = Clock::now();
        const bool logits_last = index + 1 == chunk_count;
        evaluated = mtmd_helper_eval_chunk_single(
            ctx->mctx, ctx->lctx, chunk, n_past, 0, 512, logits_last, &n_past);
        LOGI("Prompt chunk %zu/%zu type %d evaluated in %ld ms (%d positions)",
             index + 1, chunk_count, mtmd_input_chunk_get_type(chunk),
             elapsed_ms(chunk_started), static_cast<int>(n_past));
        if (evaluated != 0) break;
    }
    if (evaluated != 0) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(image);
        llama_memory_clear(llama_get_memory(ctx->lctx), true);
        LOGE("failed to evaluate the Sunny PAD multimodal prompt: %d", evaluated);
        return env->NewStringUTF("");
    }
    LOGI("Vision and prompt evaluated in %ld ms (%d positions)",
         elapsed_ms(prompt_started), static_cast<int>(n_past));

    const llama_vocab * vocab = llama_model_get_vocab(ctx->model);
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler * grammar =
        llama_sampler_init_grammar(vocab, ctx->grammar.c_str(), "root");
    if (!sampler || !grammar) {
        if (sampler) llama_sampler_free(sampler);
        if (grammar) llama_sampler_free(grammar);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(image);
        llama_memory_clear(llama_get_memory(ctx->lctx), true);
        LOGE("failed to initialize the Sunny output grammar");
        return env->NewStringUTF("");
    }
    llama_sampler_chain_add(sampler, grammar);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string result;
    std::vector<char> piece(512);
    const int limit = maxNewTokens > 0 ? maxNewTokens : 256;
    int generated = 0;
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx->lctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int count = llama_token_to_piece(
            vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (count < 0) {
            piece.resize(static_cast<size_t>(-count));
            count = llama_token_to_piece(
                vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        }
        if (count > 0) result.append(piece.data(), static_cast<size_t>(count));
        generated = i + 1;
        if (generated % 16 == 0) {
            LOGI("Generated %d tokens in %ld ms", generated, elapsed_ms(describe_started));
        }
        if (schema_complete(result)) break;
        llama_batch token_batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx->lctx, token_batch) != 0) {
            LOGE("decode failed after %d generated tokens", i);
            break;
        }
    }

    llama_sampler_free(sampler);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(image);
    llama_memory_clear(llama_get_memory(ctx->lctx), true);
    // Return only the schema block, matching the cloud response parser.
    const size_t schema = result.rfind("Lesion Type:");
    const std::string visible = schema == std::string::npos ? result : result.substr(schema);
    LOGI("Analysis finished in %ld ms (%d generated tokens, %zu output bytes, schema %s)",
         elapsed_ms(describe_started), generated, visible.size(),
         schema_complete(result) ? "complete" : "incomplete");
    return env->NewStringUTF(visible.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeFree(
        JNIEnv *, jobject, jlong handle) {
    free_context(reinterpret_cast<SunnyCtx *>(handle));
}
