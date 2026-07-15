// JNI bridge: implements com.sunny.skin.inference.LlamaBridge against llama.cpp
// + mtmd (the verified multimodal path for this model). One image + the fixed
// schema prompt in, raw six-line text out. Greedy decoding, capped generation.
//
// Built only when assembling with -PwithLlama and after vendoring llama.cpp
// (scripts/vendor_llama.sh). The Kotlin side serialises calls with a mutex, so
// the non-thread-safe mtmd eval path is safe here.

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "SunnyLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace {

struct SunnyCtx {
    llama_model   * model = nullptr;
    llama_context * lctx  = nullptr;
    mtmd_context  * mctx  = nullptr;
    int             n_threads = 4;
};

// Route llama/ggml logs to logcat (errors/warnings only) to keep noise down.
void log_cb(ggml_log_level level, const char * text, void * /*user*/) {
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) LOGI("%s", text);
}

// Convert an Android RGBA_8888 bitmap into a tightly-packed RGB24 buffer.
bool bitmap_to_rgb(JNIEnv * env, jobject bitmap, std::vector<unsigned char> & out,
                   uint32_t & nx, uint32_t & ny) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return false;

    void * pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return false;

    nx = info.width;
    ny = info.height;
    out.resize((size_t) nx * ny * 3);
    const auto * src = static_cast<const unsigned char *>(pixels);
    for (uint32_t y = 0; y < ny; ++y) {
        const unsigned char * row = src + (size_t) y * info.stride;
        for (uint32_t x = 0; x < nx; ++x) {
            const unsigned char * px = row + (size_t) x * 4;   // R,G,B,A
            size_t o = ((size_t) y * nx + x) * 3;
            out[o + 0] = px[0];
            out[o + 1] = px[1];
            out[o + 2] = px[2];
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

std::string jstr(JNIEnv * env, jstring s) {
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sunny_skin_inference_LlamaBridge_nativeInit(
        JNIEnv * env, jobject /*thiz*/, jstring modelPath, jstring mmprojPath, jint threads) {

    static bool backend_ready = false;
    if (!backend_ready) {
        llama_log_set(log_cb, nullptr);
        llama_backend_init();
        backend_ready = true;
    }

    const std::string model_path  = jstr(env, modelPath);
    const std::string mmproj_path = jstr(env, mmprojPath);

    auto * ctx = new SunnyCtx();
    ctx->n_threads = threads > 0 ? threads : 4;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;                 // CPU on Android (Vulkan is opt-in, later)
    ctx->model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (!ctx->model) { LOGE("failed to load model: %s", model_path.c_str()); delete ctx; return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx        = 1024;   // image(~256)+prompt(~130)+output(180) fits; small KV = less RAM thrash on low-end phones
    cparams.n_batch      = 512;
    cparams.n_ubatch     = 512;
    cparams.n_threads    = ctx->n_threads;
    cparams.n_threads_batch = ctx->n_threads;
    ctx->lctx = llama_init_from_model(ctx->model, cparams);
    if (!ctx->lctx) { LOGE("failed to create context"); llama_model_free(ctx->model); delete ctx; return 0; }

    mtmd_context_params mp = mtmd_context_params_default();
    mp.use_gpu     = false;
    mp.print_timings = false;
    mp.n_threads   = ctx->n_threads;
    ctx->mctx = mtmd_init_from_file(mmproj_path.c_str(), ctx->model, mp);
    if (!ctx->mctx) { LOGE("failed to init mtmd/mmproj"); llama_free(ctx->lctx);
        llama_model_free(ctx->model); delete ctx; return 0; }

    LOGI("Sunny native model ready");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_sunny_skin_inference_LlamaBridge_nativeDescribe(
        JNIEnv * env, jobject /*thiz*/, jlong handle, jobject bitmap,
        jstring prompt, jint maxNewTokens, jfloat /*temperature*/) {

    auto * ctx = reinterpret_cast<SunnyCtx *>(handle);
    if (!ctx) return env->NewStringUTF("");

    // 1. Bitmap -> RGB -> mtmd_bitmap
    std::vector<unsigned char> rgb;
    uint32_t nx = 0, ny = 0;
    if (!bitmap_to_rgb(env, bitmap, rgb, nx, ny)) return env->NewStringUTF("");
    mtmd_bitmap * mbmp = mtmd_bitmap_init(nx, ny, rgb.data());
    if (!mbmp) return env->NewStringUTF("");

    // 2. Gemma chat-formatted prompt with the media marker where the image goes.
    const std::string marker = mtmd_default_marker();
    const std::string user   = jstr(env, prompt);
    const std::string text   =
        "<start_of_turn>user\n" + marker + "\n" + user + "<end_of_turn>\n<start_of_turn>model\n";

    mtmd_input_text itext;
    itext.text          = text.c_str();
    itext.add_special   = true;    // BOS
    itext.parse_special = true;    // parse <start_of_turn> etc.

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    const mtmd_bitmap * bitmaps[1] = { mbmp };
    if (mtmd_tokenize(ctx->mctx, chunks, &itext, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(mbmp);
        return env->NewStringUTF("");
    }

    // 3. Evaluate prompt + image (mtmd handles image encode + batching).
    llama_pos n_past = 0;
    const int n_batch = (int) llama_n_batch(ctx->lctx);
    if (mtmd_helper_eval_chunks(ctx->mctx, ctx->lctx, chunks, /*n_past*/0,
                                /*seq_id*/0, n_batch, /*logits_last*/true, &n_past) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(mbmp);
        return env->NewStringUTF("");
    }

    // 4. Greedy generation loop (deterministic; F-04).
    const llama_vocab * vocab = llama_model_get_vocab(ctx->model);
    llama_sampler * smpl = llama_sampler_init_greedy();

    std::string result;
    char piece[256];
    const int cap = maxNewTokens > 0 ? maxNewTokens : 180;
    for (int i = 0; i < cap; ++i) {
        llama_token id = llama_sampler_sample(smpl, ctx->lctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) result.append(piece, n);
        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx->lctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(mbmp);

    // Reset KV cache so the warm context is clean for the next scan.
    llama_memory_clear(llama_get_memory(ctx->lctx), true);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_sunny_skin_inference_LlamaBridge_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * ctx = reinterpret_cast<SunnyCtx *>(handle);
    if (!ctx) return;
    if (ctx->mctx)  mtmd_free(ctx->mctx);
    if (ctx->lctx)  llama_free(ctx->lctx);
    if (ctx->model) llama_model_free(ctx->model);
    delete ctx;
}

} // extern "C"
