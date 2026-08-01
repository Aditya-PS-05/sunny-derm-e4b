// Host-side smoke test for the exact sequence-routed inference path used by
// android/app/src/main/cpp/sunny_moe.cpp. This intentionally uses llama.cpp's
// internal model API because Sunny must update its per-layer route-bias tensors.

#include <array>
#include <csignal>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <execinfo.h>
#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>

#include "ggml-backend.h"
#include "llama-ext.h"
#include "llama-model.h"
#include "llama.h"
#include "mtmd-helper.h"
#include "mtmd.h"

namespace {

constexpr int kSparseStart = 12;
constexpr int kSparseLayers = 12;

struct Runtime {
    llama_model * model = nullptr;
    llama_context * llama = nullptr;
    mtmd_context * vision = nullptr;
};

struct RouteAccumulator {
    std::array<std::vector<double>, kSparseLayers> sums;
    std::array<size_t, kSparseLayers> counts{};
};

void destroy(Runtime & runtime) {
    if (runtime.vision) mtmd_free(runtime.vision);
    if (runtime.llama) llama_free(runtime.llama);
    if (runtime.model) llama_model_free(runtime.model);
}

bool set_route_biases(Runtime & runtime,
                      const std::array<int, kSparseLayers> * routes) {
    for (int offset = 0; offset < kSparseLayers; ++offset) {
        auto * bias = runtime.model->layers[kSparseStart + offset].ffn_exp_probs_b;
        if (!bias || bias->type != GGML_TYPE_F32 || bias->ne[0] != 4) return false;
        std::array<float, 4> values{};
        if (routes) {
            values.fill(-1000.0f);
            values[(*routes)[offset]] = 1000.0f;
        }
        ggml_backend_tensor_set(bias, values.data(), 0, sizeof(values));
    }
    return true;
}

bool schema_complete(const std::string & output) {
    const size_t summary = output.find("\nSummary:");
    return summary != std::string::npos &&
           output.find('\n', summary + 1) != std::string::npos;
}

bool accumulate(Runtime & runtime, size_t tokens, RouteAccumulator & accumulator) {
    for (int offset = 0; offset < kSparseLayers; ++offset) {
        const int layer_index = kSparseStart + offset;
        const auto * router = runtime.model->layers[layer_index].ffn_gate_inp;
        float * input = llama_get_embeddings_layer_inp(runtime.llama, layer_index);
        if (!router || !input) return false;
        const size_t embedding = static_cast<size_t>(router->ne[0]);
        auto & sum = accumulator.sums[offset];
        if (sum.empty()) sum.assign(embedding, 0.0);
        for (size_t token = 0; token < tokens; ++token) {
            const float * row = input + token * embedding;
            for (size_t column = 0; column < embedding; ++column) {
                sum[column] += row[column];
            }
        }
        accumulator.counts[offset] += tokens;
    }
    return true;
}

bool select_routes(Runtime & runtime, const RouteAccumulator & accumulator,
                   std::array<int, kSparseLayers> & routes) {
    for (int offset = 0; offset < kSparseLayers; ++offset) {
        const auto * router = runtime.model->layers[kSparseStart + offset].ffn_gate_inp;
        if (!router || router->type != GGML_TYPE_F32 || router->ne[1] != 4 ||
            accumulator.counts[offset] == 0) return false;
        const size_t embedding = static_cast<size_t>(router->ne[0]);
        std::vector<float> weights(embedding * 4);
        ggml_backend_tensor_get(router, weights.data(), 0, weights.size() * sizeof(float));
        double best = -INFINITY;
        int selected = 0;
        for (int expert = 0; expert < 4; ++expert) {
            double score = 0.0;
            for (size_t column = 0; column < embedding; ++column) {
                score += accumulator.sums[offset][column] *
                         weights[static_cast<size_t>(expert) * embedding + column];
            }
            if (score > best) {
                best = score;
                selected = expert;
            }
        }
        routes[offset] = selected;
    }
    return true;
}

bool decode_sequence_prompt(Runtime & runtime, const mtmd_input_chunks * chunks,
                            std::array<int, kSparseLayers> & routes,
                            llama_pos & n_past) {
    if (!set_route_biases(runtime, nullptr)) return false;
    for (int layer = kSparseStart; layer < kSparseStart + kSparseLayers; ++layer) {
        llama_set_embeddings_layer_inp(runtime.llama, static_cast<uint32_t>(layer), true);
    }
    const auto * token_embeddings = runtime.model->tok_embd;
    const auto * traits = token_embeddings
        ? ggml_get_type_traits(token_embeddings->type)
        : nullptr;
    if (!token_embeddings || !traits || !traits->to_float) return false;
    const size_t embedding = static_cast<size_t>(token_embeddings->ne[0]);
    size_t total_tokens = 0;
    for (size_t index = 0; index < mtmd_input_chunks_size(chunks); ++index) {
        const auto * chunk = mtmd_input_chunks_get(chunks, index);
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t count = 0;
            mtmd_input_chunk_get_tokens_text(chunk, &count);
            total_tokens += count;
        } else {
            total_tokens += static_cast<size_t>(mtmd_input_chunk_get_n_tokens(chunk));
        }
    }
    if (total_tokens == 0 || total_tokens > llama_n_batch(runtime.llama)) return false;
    std::cerr << "PROMPT_TOKENS=" << total_tokens << "\n";
    std::vector<float> combined;
    combined.reserve(total_tokens * embedding);
    std::vector<unsigned char> row_buffer(token_embeddings->nb[1]);
    for (size_t index = 0; index < mtmd_input_chunks_size(chunks); ++index) {
        const auto * chunk = mtmd_input_chunks_get(chunks, index);
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t count = 0;
            const auto * tokens = mtmd_input_chunk_get_tokens_text(chunk, &count);
            for (size_t token_index = 0; token_index < count; ++token_index) {
                const auto token = tokens[token_index];
                if (token < 0 || token >= token_embeddings->ne[1]) return false;
                const size_t destination = combined.size();
                combined.resize(destination + embedding);
                const size_t offset = static_cast<size_t>(token) * token_embeddings->nb[1];
                const void * source = nullptr;
                if (token_embeddings->data) {
                    source = static_cast<const unsigned char *>(token_embeddings->data) + offset;
                } else {
                    ggml_backend_tensor_get(token_embeddings, row_buffer.data(),
                                            offset, row_buffer.size());
                    source = row_buffer.data();
                }
                traits->to_float(source, combined.data() + destination, embedding);
            }
        } else {
            if (mtmd_encode_chunk(runtime.vision, chunk) != 0) return false;
            const size_t count = static_cast<size_t>(mtmd_input_chunk_get_n_tokens(chunk));
            const float * media = mtmd_get_output_embd(runtime.vision);
            if (!media) return false;
            combined.insert(combined.end(), media, media + count * embedding);
        }
    }
    llama_batch batch = llama_batch_init(total_tokens, embedding, 1);
    batch.n_tokens = static_cast<int32_t>(total_tokens);
    std::memcpy(batch.embd, combined.data(), combined.size() * sizeof(float));
    for (int32_t index = 0; index < batch.n_tokens; ++index) {
        batch.pos[index] = index;
        batch.n_seq_id[index] = 1;
        batch.seq_id[index][0] = 0;
        batch.logits[index] = index == batch.n_tokens - 1;
    }
    const int decoded = llama_decode(runtime.llama, batch);
    llama_batch_free(batch);
    if (decoded != 0) return false;
    RouteAccumulator accumulator;
    if (!accumulate(runtime, total_tokens, accumulator)) return false;
    if (!select_routes(runtime, accumulator, routes)) return false;
    for (int layer = kSparseStart; layer < kSparseStart + kSparseLayers; ++layer) {
        llama_set_embeddings_layer_inp(runtime.llama, static_cast<uint32_t>(layer), false);
    }
    n_past = static_cast<llama_pos>(total_tokens);
    return set_route_biases(runtime, &routes);
}

}  // namespace

int main(int argc, char ** argv) {
    if (argc < 4) {
        std::cerr << "usage: sunny_moe_native_smoke MODEL MMPROJ IMAGE [TOKENS]\n";
        return 2;
    }
    std::signal(SIGSEGV, [](int signal) {
        void * frames[64];
        const int count = backtrace(frames, 64);
        backtrace_symbols_fd(frames, count, STDERR_FILENO);
        _exit(128 + signal);
    });
    const int max_tokens = argc >= 5 ? std::atoi(argv[4]) : 100;
    llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    mtmd_helper_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    llama_backend_init();
    Runtime runtime;
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = false;
    runtime.model = llama_model_load_from_file(argv[1], model_params);
    if (!runtime.model) return 3;
    auto context_params = llama_context_default_params();
    context_params.n_ctx = 1024;
    context_params.n_batch = 768;
    context_params.n_ubatch = 768;
    context_params.n_threads = 4;
    context_params.n_threads_batch = 4;
    runtime.llama = llama_init_from_model(runtime.model, context_params);
    auto vision_params = mtmd_context_params_default();
    vision_params.use_gpu = false;
    vision_params.n_threads = 4;
    runtime.vision = mtmd_init_from_file(argv[2], runtime.model, vision_params);
    if (!runtime.llama || !runtime.vision) {
        destroy(runtime);
        return 4;
    }
    const auto bitmap = mtmd_helper_bitmap_init_from_file(runtime.vision, argv[3], false);
    if (!bitmap.bitmap) {
        destroy(runtime);
        return 5;
    }
    const std::string rendered =
        std::string("<|im_start|>User:") + mtmd_default_marker() +
        "You are a dermatology description assistant. Look at this skin lesion photo and "
        "describe what you see. Do NOT diagnose or name a disease. Report only observable "
        "features in this exact format:\n"
        "Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>\n"
        "Colour: <colours present>\n"
        "Symmetry: <symmetric / asymmetric>\n"
        "Borders: <smooth / irregular / well- or poorly-defined>\n"
        "Texture: <smooth / rough / raised / scaly>\n"
        "Summary: <one plain-language sentence describing the lesion's appearance and "
        "reminding the user this is not a diagnosis><end_of_utterance>\nAssistant:";
    mtmd_input_text text{rendered.c_str(), false, true};
    auto * chunks = mtmd_input_chunks_init();
    const mtmd_bitmap * bitmaps[] = {bitmap.bitmap};
    if (!chunks || mtmd_tokenize(runtime.vision, chunks, &text, bitmaps, 1) != 0) {
        mtmd_bitmap_free(bitmap.bitmap);
        destroy(runtime);
        return 6;
    }
    std::array<int, kSparseLayers> routes{};
    llama_pos n_past = 0;
    if (!decode_sequence_prompt(runtime, chunks, routes, n_past)) {
        std::cerr << "route probe failed\n";
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bitmap.bitmap);
        destroy(runtime);
        return 7;
    }
    std::cout << "ROUTES=";
    for (size_t i = 0; i < routes.size(); ++i) {
        if (i) std::cout << ',';
        std::cout << routes[i];
    }
    std::cout << "\n";
    const auto * vocab = llama_model_get_vocab(runtime.model);
    auto * sampler = llama_sampler_init_greedy();
    std::string output;
    std::vector<char> piece(512);
    for (int i = 0; i < max_tokens; ++i) {
        const llama_token token = llama_sampler_sample(sampler, runtime.llama, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int count = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        if (count < 0) {
            piece.resize(static_cast<size_t>(-count));
            count = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        }
        if (count > 0) output.append(piece.data(), static_cast<size_t>(count));
        if (schema_complete(output)) break;
        llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(runtime.llama, next) != 0) return 9;
    }
    std::cout << "OUTPUT=" << output << "\n";
    llama_sampler_free(sampler);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bitmap.bitmap);
    set_route_biases(runtime, nullptr);
    destroy(runtime);
    llama_backend_free();
    return 0;
}
