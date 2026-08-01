package com.sunny.skin.inference

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The sole downloadable on-device engine. Pro remains a [RemoteSunnyModel]. */
class SunnyMoeModel(
    private val packDirectory: String,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
) : SunnyModel {
    override val version = "sunny-moe-2.2b-v4-gguf"
    private var handle = 0L
    private val mutex = Mutex()

    override val isReady: Boolean get() = handle != 0L

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) return@withContext
            require(SunnyMoeBridge.ensureLibrary()) { "libsunny_moe is not available" }
            require(File(packDirectory, "manifest.json").isFile) {
                "Sunny-MoE manifest is missing from $packDirectory"
            }
            handle = SunnyMoeBridge.nativeInit(packDirectory, threads)
            check(handle != 0L) { "Sunny-MoE native initialization failed" }
        }
    }

    override suspend fun describeRaw(
        bitmap: Bitmap,
        @Suppress("UNUSED_PARAMETER") analysisId: String,
    ): String = withContext(Dispatchers.IO) {
        if (handle == 0L) warmUp()
        val nativeBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
                "Could not convert the scan photo for on-device analysis"
            }
        }
        try {
            mutex.withLock {
                SunnyMoeBridge.nativeDescribe(
                    handle = handle,
                    bitmap = nativeBitmap,
                    prompt = Prompt.SCHEMA_PROMPT,
                    maxNewTokens = Prompt.MAX_NEW_TOKENS,
                    temperature = Prompt.TEMPERATURE,
                ).also { output ->
                    check(output.isNotBlank()) {
                        "Sunny-MoE did not produce output"
                    }
                }
            }
        } finally {
            if (nativeBitmap !== bitmap) nativeBitmap.recycle()
        }
    }

    override fun close() {
        if (handle != 0L) {
            SunnyMoeBridge.nativeFree(handle)
            handle = 0L
        }
    }
}
