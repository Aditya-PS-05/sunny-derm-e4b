package com.sunny.skin.inference.download

/**
 * The two files that make up the on-device model (exports/MODELS.md). Sizes are
 * the exact measured byte counts; [sha256Prefix] is the first 16 hex chars
 * recorded in MODELS.md — enough to catch a corrupt/wrong download. Replace with
 * the FULL sha256 once published for stronger verification.
 *
 * [remotePath] is appended to [ModelSource.baseUrl]. Point that at the Hugging
 * Face repo (or CDN) the GGUFs get pushed to; see exports/MODELS.md route B.
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
 * Where the weights are fetched from. Set [baseUrl] to your published repo, e.g.
 * a Hugging Face resolve URL:
 *   https://huggingface.co/<user>/<repo>/resolve/main/
 * Kept in one place so swapping to a smaller re-quant (int8 mmproj / Q4_0 LM)
 * is a config change, not a code change.
 */
object ModelSource {
    // TODO: replace with the real published base URL before enabling download.
    const val baseUrl: String = "https://huggingface.co/REPLACE_ME/sunny-gemma4-e4b-derm/resolve/main/"

    val isConfigured: Boolean get() = !baseUrl.contains("REPLACE_ME")

    fun urlFor(asset: ModelAsset): String = baseUrl + asset.remotePath
}
