package com.sunny.skin.inference

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Chooses the on-device model implementation. Uses the real llama.cpp/mtmd path
 * when BOTH the native library and the two GGUF files are present; otherwise
 * falls back to [MockSunnyModel] so the app is fully functional for development
 * and demo without the ~6 GB weights on disk.
 *
 * The weights already exist locally in this repo (exports/model_on_host/); they
 * are NOT bundled in the APK (6 GB) and do not need downloading. They are found
 * at runtime by scanning, in order, these on-device folders:
 *
 *   1. filesDir/models/                              (internal, private; download target)
 *   2. getExternalFilesDir("models")                 (app external dir — adb push here for dev)
 *   3. /data/local/tmp/sunny/                        (last-resort dev push location)
 *
 * To use the local weights on a device/emulator (no download):
 *   ./scripts/push_weights_to_device.sh              (adb push -> external files dir)
 */
object ModelProvider {

    const val LM_FILE = "e4b-derm-Q4_K_M.gguf"
    const val MMPROJ_FILE = "mmproj-e4b-derm-f16.gguf"

    /** Primary internal location (also the download target). */
    fun modelsDir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Candidate directories searched for the weight files, in priority order.
     * Release builds load ONLY from internal, app-private storage (the download
     * target). The shared external dir and /data/local/tmp — both writable
     * without root and unverified — are dev conveniences, so they are scanned
     * only on debuggable builds where a GGUF fed to native llama.cpp couldn't be
     * planted by another app on a shipped install.
     */
    private fun candidateDirs(context: Context): List<File> {
        val dirs = mutableListOf(modelsDir(context))
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            context.getExternalFilesDir("models")?.let { dirs.add(it) }
            dirs.add(File("/data/local/tmp/sunny"))
        }
        return dirs
    }

    /** Resolve (languageModel, visionProjector) from the first dir holding both. */
    fun resolveWeights(context: Context): Pair<File, File>? {
        for (dir in candidateDirs(context)) {
            val lm = File(dir, LM_FILE)
            val mmproj = File(dir, MMPROJ_FILE)
            if (lm.exists() && mmproj.exists()) return lm to mmproj
        }
        return null
    }

    fun weightsPresent(context: Context): Boolean = resolveWeights(context) != null

    /** Human-readable location of the found weights, for the setup screen. */
    fun weightsLocation(context: Context): String? =
        resolveWeights(context)?.first?.parentFile?.absolutePath

    /** True once [describer] has been created against the real GGUF weights. */
    @Volatile var usingRealModel: Boolean = false
        private set

    fun create(context: Context): SunnyModel {
        val weights = resolveWeights(context)
        val realAvailable = weights != null && LlamaBridge.ensureLibrary()
        usingRealModel = realAvailable
        return if (realAvailable) {
            LlamaCppSunnyModel(
                modelPath = weights.first.absolutePath,
                mmprojPath = weights.second.absolutePath,
            )
        } else {
            MockSunnyModel()
        }
    }

    /** Single shared describer per process (keeps the model warm across screens). */
    @Volatile private var describer: SunnyDescriber? = null

    fun describer(context: Context): SunnyDescriber =
        describer ?: synchronized(this) {
            describer ?: SunnyDescriber(create(context.applicationContext)).also { describer = it }
        }

    /**
     * Drop the cached describer so the next [describer] call re-selects the
     * implementation. Called after weights appear (download or adb push) to
     * upgrade the live session from mock to the real llama.cpp model.
     */
    fun reset() {
        synchronized(this) {
            describer = null
        }
    }
}
