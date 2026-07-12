package com.sunny.skin.inference

import android.graphics.Bitmap

/**
 * JNI surface to the llama.cpp / mtmd multimodal runner (the VERIFIED path for
 * this model — README §Status). The native library is built from llama.cpp with
 * the `mtmd` API and packaged as `libsunny_llama.so` per ABI.
 *
 * Native build (documented in docs/android_integration.md §3, to be added under
 * app/src/main/cpp with CMake once the .so is produced):
 *   - context created from BOTH files: the Q4_K_M language model +
 *     mmproj-e4b-derm-f16.gguf vision projector (image input needs the mmproj),
 *   - image added to the chat first, then Prompt.SCHEMA_PROMPT,
 *   - greedy sampling, n_predict = Prompt.MAX_NEW_TOKENS.
 *
 * This class is intentionally a thin declaration: it compiles without the .so
 * present; calls throw UnsatisfiedLinkError until the native lib is bundled,
 * which is why [ModelProvider] disables analysis when the weights or library
 * are absent.
 */
internal object LlamaBridge {
    @Volatile private var loaded = false

    fun ensureLibrary(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("sunny_llama")
            loaded = true
            true
        } catch (t: UnsatisfiedLinkError) {
            false
        }
    }

    /** @return an opaque native context handle, or 0 on failure. */
    external fun nativeInit(modelPath: String, mmprojPath: String, threads: Int): Long

    /** Runs one image + the schema prompt; returns raw generated text. */
    external fun nativeDescribe(
        handle: Long,
        bitmap: Bitmap,
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
    ): String

    external fun nativeFree(handle: Long)
}
