package com.sunny.skin.inference

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device model backed by llama.cpp/mtmd via [LlamaBridge]. Loads the
 * Q4_K_M language model + the mmproj vision projector once and keeps the native
 * context warm for the session (N-02). Serializes calls with a mutex because a
 * single llama context is not re-entrant.
 *
 * Wiring checklist to activate this path (replacing the mock):
 *   1. Build `libsunny_llama.so` from llama.cpp (mtmd) for arm64-v8a and add the
 *      CMake target under app/src/main/cpp.
 *   2. Ship the weights: e4b-derm-Q4_K_M.gguf (5.0 GB) + mmproj-e4b-derm-f16.gguf
 *      (990 MB) — via a first-run download or an OBB/asset (see exports/MODELS.md);
 *      they are marked noCompress in build.gradle so they mmap directly.
 *   3. ModelProvider will then pick this impl automatically.
 */
class LlamaCppSunnyModel(
    private val modelPath: String,
    private val mmprojPath: String,
    // Cap at 4: on big.LITTLE phones (e.g. SD695: 2×A78 + 6×A55) spilling onto the
    // slow little cores adds contention and can be slower than fewer, faster threads.
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
) : SunnyModel {

    override val version = "gemma4-e4b-derm-q4km"
    private var handle: Long = 0L
    private val mutex = Mutex()

    override val isReady: Boolean get() = handle != 0L

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) return@withContext
            require(LlamaBridge.ensureLibrary()) { "libsunny_llama not available" }
            require(File(modelPath).exists()) { "model weights missing: $modelPath" }
            require(File(mmprojPath).exists()) { "mmproj missing: $mmprojPath" }
            handle = LlamaBridge.nativeInit(modelPath, mmprojPath, threads)
            check(handle != 0L) { "native init failed" }
        }
    }

    override suspend fun describeRaw(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (handle == 0L) warmUp()
        mutex.withLock {
            LlamaBridge.nativeDescribe(
                handle = handle,
                bitmap = bitmap,
                prompt = Prompt.SCHEMA_PROMPT,
                maxNewTokens = Prompt.MAX_NEW_TOKENS,
                temperature = Prompt.TEMPERATURE,
            )
        }
    }

    override fun close() {
        if (handle != 0L) {
            LlamaBridge.nativeFree(handle)
            handle = 0L
        }
    }
}
