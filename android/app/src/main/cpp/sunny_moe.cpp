#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <filesystem>
#include <array>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "llama-ext.h"
#include "llama-model.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "SunnyMoe"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char * MODEL_FILE = "sunny-moe-text-Q4_K_M.gguf";
constexpr const char * MMPROJ_FILE = "sunny-moe-mmproj-F16.gguf";
constexpr int SPARSE_START = 12;
constexpr int SPARSE_LAYERS = 12;

struct SunnyCtx {
    llama_model * model = nullptr;
    llama_context * lctx = nullptr;
    mtmd_context * mctx = nullptr;
    int n_threads = 4;
};

std::once_flag backend_once;

void log_cb(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) {
        LOGI("%s", text);
    }
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

struct RouteAccumulator {
    std::array<std::vector<double>, SPARSE_LAYERS> sums;
    std::array<size_t, SPARSE_LAYERS> counts{};
};

bool set_route_biases(SunnyCtx * ctx, const std::array<int, SPARSE_LAYERS> * routes) {
    for (int offset = 0; offset < SPARSE_LAYERS; ++offset) {
        auto & layer = ctx->model->layers[SPARSE_START + offset];
        ggml_tensor * bias = layer.ffn_exp_probs_b;
        if (!bias || bias->type != GGML_TYPE_F32 || bias->ne[0] != 4) return false;
        std::array<float, 4> values{};
        if (routes) {
            values.fill(-1000.0f);
            const int expert = (*routes)[offset];
            if (expert < 0 || expert >= 4) return false;
            values[expert] = 1000.0f;
        }
        ggml_backend_tensor_set(bias, values.data(), 0, sizeof(values));
    }
    return true;
}

bool accumulate_router_inputs(SunnyCtx * ctx, size_t tokens, RouteAccumulator & accumulator) {
    for (int offset = 0; offset < SPARSE_LAYERS; ++offset) {
        const int layer_index = SPARSE_START + offset;
        const auto & router = ctx->model->layers[layer_index].ffn_gate_inp;
        if (!router || router->ne[0] <= 0) return false;
        const size_t embedding = static_cast<size_t>(router->ne[0]);
        float * inputs = llama_get_embeddings_layer_inp(ctx->lctx, layer_index);
        if (!inputs) return false;
        auto & sum = accumulator.sums[offset];
        if (sum.empty()) sum.assign(embedding, 0.0);
        for (size_t token = 0; token < tokens; ++token) {
            const float * row = inputs + token * embedding;
            for (size_t column = 0; column < embedding; ++column) {
                sum[column] += row[column];
            }
        }
        accumulator.counts[offset] += tokens;
    }
    return true;
}

bool select_routes(SunnyCtx * ctx, const RouteAccumulator & accumulator,
                   std::array<int, SPARSE_LAYERS> & routes) {
    for (int offset = 0; offset < SPARSE_LAYERS; ++offset) {
        const auto * router = ctx->model->layers[SPARSE_START + offset].ffn_gate_inp;
        if (!router) return false;
        const size_t embedding = static_cast<size_t>(router->ne[0]);
        const size_t experts = static_cast<size_t>(router->ne[1]);
        if (router->type != GGML_TYPE_F32 || experts != 4 ||
            accumulator.counts[offset] == 0 || accumulator.sums[offset].size() != embedding) {
            return false;
        }
        std::vector<float> weights(embedding * experts);
        ggml_backend_tensor_get(router, weights.data(), 0, weights.size() * sizeof(float));
        int selected = 0;
        double best = -INFINITY;
        for (size_t expert = 0; expert < experts; ++expert) {
            double score = 0.0;
            const float * row = weights.data() + expert * embedding;
            for (size_t column = 0; column < embedding; ++column) {
                score += accumulator.sums[offset][column] * row[column];
            }
            if (score > best) {
                best = score;
                selected = static_cast<int>(expert);
            }
        }
        routes[offset] = selected;
    }
    return true;
}

bool schema_complete(const std::string & output) {
    const size_t summary = output.find("\nSummary:");
    if (summary == std::string::npos) return false;
    return output.find('\n', summary + 1) != std::string::npos;
}

bool decode_sequence_prompt(SunnyCtx * ctx, const mtmd_input_chunks * chunks,
                            std::array<int, SPARSE_LAYERS> & routes,
                            llama_pos & n_past) {
    if (!set_route_biases(ctx, nullptr)) return false;
    const auto disable_extraction = [&] {
        for (int layer = SPARSE_START; layer < SPARSE_START + SPARSE_LAYERS; ++layer) {
            llama_set_embeddings_layer_inp(ctx->lctx, static_cast<uint32_t>(layer), false);
        }
    };
    const auto fail = [&] {
        llama_memory_clear(llama_get_memory(ctx->lctx), true);
        disable_extraction();
        set_route_biases(ctx, nullptr);
        return false;
    };
    for (int layer = SPARSE_START; layer < SPARSE_START + SPARSE_LAYERS; ++layer) {
        llama_set_embeddings_layer_inp(ctx->lctx, static_cast<uint32_t>(layer), true);
    }

    const ggml_tensor * token_embeddings = ctx->model->tok_embd;
    const auto * traits = token_embeddings
        ? ggml_get_type_traits(token_embeddings->type)
        : nullptr;
    if (!token_embeddings || !traits || !traits->to_float) return fail();
    const size_t embedding = static_cast<size_t>(token_embeddings->ne[0]);
    const size_t count = mtmd_input_chunks_size(chunks);
    size_t total_tokens = 0;
    for (size_t index = 0; index < count; ++index) {
        const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks, index);
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t token_count = 0;
            mtmd_input_chunk_get_tokens_text(chunk, &token_count);
            total_tokens += token_count;
        } else {
            total_tokens += static_cast<size_t>(mtmd_input_chunk_get_n_tokens(chunk));
        }
    }
    if (total_tokens == 0 || total_tokens > llama_n_batch(ctx->lctx)) return fail();

    std::vector<float> combined;
    combined.reserve(total_tokens * embedding);
    std::vector<unsigned char> row_buffer(token_embeddings->nb[1]);
    for (size_t index = 0; index < count; ++index) {
        const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks, index);
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t token_count = 0;
            const llama_token * tokens = mtmd_input_chunk_get_tokens_text(chunk, &token_count);
            for (size_t token_index = 0; token_index < token_count; ++token_index) {
                const llama_token token = tokens[token_index];
                if (token < 0 || token >= token_embeddings->ne[1]) return fail();
                const size_t destination = combined.size();
                combined.resize(destination + embedding);
                const size_t offset = static_cast<size_t>(token) * token_embeddings->nb[1];
                const void * source = nullptr;
                if (token_embeddings->data) {
                    source = static_cast<const unsigned char *>(token_embeddings->data) + offset;
                } else {
                    ggml_backend_tensor_get(
                        token_embeddings, row_buffer.data(), offset, row_buffer.size());
                    source = row_buffer.data();
                }
                traits->to_float(source, combined.data() + destination, embedding);
            }
        } else {
            if (mtmd_encode_chunk(ctx->mctx, chunk) != 0) return fail();
            const size_t token_count =
                static_cast<size_t>(mtmd_input_chunk_get_n_tokens(chunk));
            const float * media = mtmd_get_output_embd(ctx->mctx);
            if (!media) return fail();
            combined.insert(
                combined.end(), media, media + token_count * embedding);
        }
    }

    llama_batch batch = llama_batch_init(static_cast<int32_t>(total_tokens),
                                         static_cast<int32_t>(embedding), 1);
    batch.n_tokens = static_cast<int32_t>(total_tokens);
    std::memcpy(batch.embd, combined.data(), combined.size() * sizeof(float));
    for (int32_t index = 0; index < batch.n_tokens; ++index) {
        batch.pos[index] = index;
        batch.n_seq_id[index] = 1;
        batch.seq_id[index][0] = 0;
        batch.logits[index] = index == batch.n_tokens - 1;
    }
    const int decoded = llama_decode(ctx->lctx, batch);
    llama_batch_free(batch);
    if (decoded != 0) return fail();

    RouteAccumulator accumulator;
    if (!accumulate_router_inputs(ctx, total_tokens, accumulator) ||
        !select_routes(ctx, accumulator, routes)) return fail();
    n_past = static_cast<llama_pos>(total_tokens);
    disable_extraction();
    return set_route_biases(ctx, &routes);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeInit(
        JNIEnv * env, jobject, jstring packDirectory, jint threads) {
    std::call_once(backend_once, [] {
        llama_log_set(log_cb, nullptr);
        llama_backend_init();
    });

    const std::filesystem::path directory(jstr(env, packDirectory));
    const std::filesystem::path model_path = directory / MODEL_FILE;
    const std::filesystem::path mmproj_path = directory / MMPROJ_FILE;
    if (!std::filesystem::is_regular_file(model_path) ||
        !std::filesystem::is_regular_file(mmproj_path)) {
        LOGE("Sunny-MoE GGUF files are missing from %s", directory.c_str());
        return 0;
    }

    auto * ctx = new SunnyCtx();
    ctx->n_threads = threads > 0 ? threads : 4;
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    // Sunny changes twelve tiny route-bias tensors between the probe and
    // generation passes. GGUF mmap pages are read-only, so load the text
    // weights into the CPU backend's writable buffer.
    model_params.use_mmap = false;
    ctx->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!ctx->model) {
        LOGE("failed to load %s", model_path.c_str());
        free_context(ctx);
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 1024;
    context_params.n_batch = 768;
    context_params.n_ubatch = 768;
    context_params.n_threads = ctx->n_threads;
    context_params.n_threads_batch = ctx->n_threads;
    ctx->lctx = llama_init_from_model(ctx->model, context_params);
    if (!ctx->lctx) {
        LOGE("failed to initialize the Sunny-MoE context");
        free_context(ctx);
        return 0;
    }

    mtmd_context_params vision_params = mtmd_context_params_default();
    vision_params.use_gpu = false;
    vision_params.print_timings = false;
    vision_params.n_threads = ctx->n_threads;
    ctx->mctx = mtmd_init_from_file(mmproj_path.c_str(), ctx->model, vision_params);
    if (!ctx->mctx) {
        LOGE("failed to load %s", mmproj_path.c_str());
        free_context(ctx);
        return 0;
    }
    LOGI("Sunny-MoE runtime initialized");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeDescribe(
        JNIEnv * env, jobject, jlong handle, jobject bitmap, jstring prompt,
        jint maxNewTokens, jfloat) {
    auto * ctx = reinterpret_cast<SunnyCtx *>(handle);
    if (!ctx || !bitmap) return env->NewStringUTF("");

    std::vector<unsigned char> rgb;
    uint32_t width = 0;
    uint32_t height = 0;
    if (!bitmap_to_rgb(env, bitmap, rgb, width, height)) {
        LOGE("expected an RGBA_8888 bitmap");
        return env->NewStringUTF("");
    }
    mtmd_bitmap * image = mtmd_bitmap_init(width, height, rgb.data());
    if (!image) return env->NewStringUTF("");

    const std::string media_marker = mtmd_default_marker();
    const std::string user_prompt = jstr(env, prompt);
    const std::string rendered =
        "<|im_start|>User:" + media_marker + user_prompt +
        "<end_of_utterance>\nAssistant:";
    mtmd_input_text input_text{};
    input_text.text = rendered.c_str();
    // The rendered chat already starts with <|im_start|>; adding the tokenizer
    // BOS would duplicate that token and diverge from AutoProcessor training.
    input_text.add_special = false;
    input_text.parse_special = true;

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    const mtmd_bitmap * images[] = {image};
    if (!chunks || mtmd_tokenize(ctx->mctx, chunks, &input_text, images, 1) != 0) {
        if (chunks) mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(image);
        LOGE("failed to tokenize the Sunny-MoE multimodal prompt");
        return env->NewStringUTF("");
    }

    std::array<int, SPARSE_LAYERS> routes{};
    llama_pos n_past = 0;
    if (!decode_sequence_prompt(ctx, chunks, routes, n_past)) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(image);
        llama_memory_clear(llama_get_memory(ctx->lctx), true);
        set_route_biases(ctx, nullptr);
        LOGE("failed to select Sunny-MoE expert routes");
        return env->NewStringUTF("");
    }

    const llama_vocab * vocab = llama_model_get_vocab(ctx->model);
    llama_sampler * sampler = llama_sampler_init_greedy();
    std::string result;
    std::vector<char> piece(512);
    const int limit = maxNewTokens > 0 ? maxNewTokens : 180;
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
        // The contract is exactly six lines. Some quantized checkpoints keep
        // emitting harmless filler after the completed Summary, so stop at
        // that line instead of burning CPU until the hard token ceiling.
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
    set_route_biases(ctx, nullptr);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_sunny_skin_inference_SunnyMoeBridge_nativeFree(
        JNIEnv *, jobject, jlong handle) {
    free_context(reinterpret_cast<SunnyCtx *>(handle));
}
