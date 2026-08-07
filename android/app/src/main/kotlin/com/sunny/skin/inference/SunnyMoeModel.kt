package com.sunny.skin.inference

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The bundled PAD-UFES-20-trained on-device engine. */
class SunnyMoeModel(
    private val packDirectory: String,
    private val nativeLibraryDirectory: String,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(4, 8),
) : SunnyModel {
    override val version = "sunny-pad-smolvlm-500m-mobile256-v2-gguf"
    private var handle = 0L
    private val mutex = Mutex()

    override val isReady: Boolean get() = handle != 0L

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) return@withContext
            require(SunnyMoeBridge.ensureLibrary()) { "libsunny_moe is not available" }
            require(File(packDirectory, "manifest.json").isFile) {
                "Sunny Offline manifest is missing from $packDirectory"
            }
            handle = SunnyMoeBridge.nativeInit(
                packDirectory = packDirectory,
                nativeLibraryDirectory = nativeLibraryDirectory,
                threads = threads,
            )
            check(handle != 0L) { "Sunny Offline native initialization failed" }
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
                    maxNewTokens = Prompt.DEVICE_MAX_NEW_TOKENS,
                    temperature = Prompt.TEMPERATURE,
                ).also { output ->
                    check(output.isNotBlank()) {
                        "Sunny Offline did not produce output"
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
