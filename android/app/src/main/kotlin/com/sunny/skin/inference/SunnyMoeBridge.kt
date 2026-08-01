package com.sunny.skin.inference

import android.graphics.Bitmap

/** JNI boundary for the Colibri-style Sunny-MoE Android runtime. */
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

    /** Opens manifest.json and its independently addressable dense/expert files. */
    external fun nativeInit(packDirectory: String, threads: Int): Long

    external fun nativeDescribe(
        handle: Long,
        bitmap: Bitmap,
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
    ): String

    external fun nativeFree(handle: Long)
}
