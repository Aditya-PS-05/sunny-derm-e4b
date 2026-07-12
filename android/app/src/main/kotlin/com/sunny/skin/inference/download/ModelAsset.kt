package com.sunny.skin.inference.download

import com.sunny.skin.BuildConfig

/**
 * The two files that make up the on-device model (exports/MODELS.md). Sizes are
 * the exact measured byte counts; [sha256Prefix] is the first 16 hex chars
 * recorded in MODELS.md — enough to catch a corrupt/wrong download. Replace with
 * the FULL sha256 once published for stronger verification.
 *
 * [remotePath] is appended to [ModelSource.baseUrl]. Configure a rights-cleared
 * HTTPS repository or CDN at build time; see exports/MODELS.md route B.
 */
enum class ModelAsset(
    val fileName: String,
    val sizeBytes: Long,
    val sha256Prefix: String,
    val remotePath: String,
) {
    LANGUAGE_MODEL(
        fileName = "e4b-derm-Q4_K_M.gguf",
        sizeBytes = 5_302_272_736L,
        sha256Prefix = "e41e8bf3d8184980",
        remotePath = "e4b-derm-Q4_K_M.gguf",
    ),
    VISION_PROJECTOR(
        fileName = "mmproj-e4b-derm-f16.gguf",
        sizeBytes = 990_372_192L,
        sha256Prefix = "23474645acf3e10f",
        remotePath = "mmproj-e4b-derm-f16.gguf",
    );

    companion object {
        /** Total bytes to fetch for a full model install (~6.0 GB). */
        val totalBytes: Long get() = entries.sumOf { it.sizeBytes }
    }
}

/**
 * Where the weights are fetched from. The URL is empty by default and supplied
 * with the modelBaseUrl Gradle property or SUNNY_MODEL_BASE_URL environment
 * variable only after publication and data-rights clearance.
 */
object ModelSource {
    val baseUrl: String = BuildConfig.SUNNY_MODEL_BASE_URL

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun urlFor(asset: ModelAsset): String = baseUrl + asset.remotePath
}
