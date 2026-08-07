package com.sunny.skin.inference

import android.graphics.Bitmap

/** Stable JNI boundary for the PAD-trained SmolVLM 500M Android runtime. */
internal object SunnyMoeBridge {
    @Volatile private var loaded = false

    fun ensureLibrary(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("sunny_moe")
            loaded = true
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    /** Opens the checksum-verified text model and vision projector. */
    external fun nativeInit(
        packDirectory: String,
        nativeLibraryDirectory: String,
        threads: Int,
    ): Long

    external fun nativeDescribe(
        handle: Long,
        bitmap: Bitmap,
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
    ): String

    external fun nativeFree(handle: Long)
}
