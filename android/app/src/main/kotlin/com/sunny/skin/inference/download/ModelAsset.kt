package com.sunny.skin.inference.download

import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.subscription.SubscriptionEntitlements

/** One immutable file in the downloadable Sunny-MoE pack. */
data class ModelAsset(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val remotePath: String,
)

enum class ModelRuntime { SUNNY_MOE }

/** App-signed model metadata. A CDN response cannot change these checksums. */
data class ModelPack(
    val tier: SunnyModelTier,
    val version: String,
    val runtime: ModelRuntime,
    val assets: List<ModelAsset>,
    val minimumRamBytes: Long,
) {
    val totalBytes: Long = assets.sumOf { it.sizeBytes }
}

/**
 * The only local pack accepted by this app. Lite, Medium and the legacy GGUF
 * Pro pair are intentionally absent; Sunny MoE is the sole Pro local route.
 */
object ModelPackCatalog {
    private const val remoteDir = "sunny-moe-2.2b-v4-gguf"

    private fun asset(file: String, bytes: Long, sha256: String) = ModelAsset(
        fileName = file,
        sizeBytes = bytes,
        sha256 = sha256,
        remotePath = "$remoteDir/$file",
    )

    val sunnyMoe = ModelPack(
        tier = SunnyModelTier.SUNNY_MOE,
        version = remoteDir,
        runtime = ModelRuntime.SUNNY_MOE,
        assets = listOf(
            asset("sunny-moe-text-Q4_K_M.gguf", 2_210_067_936L, "7e7aa651986473c94988ac99978cd5c51a7bb7b8a1e4092cd41ed835c38fd28b"),
            asset("sunny-moe-mmproj-F16.gguf", 872_300_704L, "c4149a795d2c4af070d94e2130e2e1026d96bb912bbac1595b6fd12d376b91f4"),
            asset("manifest.json", 1_037L, "1a8ce0012e240b3e2e3c6b8a2eec376396986b4fbda858dabd8ccc8b686c13ad"),
        ),
        // Conservative until physical Android RSS/thermal validation is complete.
        minimumRamBytes = 6_000_000_000L,
    )

    fun publishedPack(tier: SunnyModelTier): ModelPack? = when (tier) {
        SunnyModelTier.SUNNY_MOE -> sunnyMoe
        SunnyModelTier.PRO_CLOUD -> null
    }
}

/** Pro-authorized signed model URLs, with a debug-only static-origin fallback. */
object ModelSource {
    val baseUrl: String = BuildConfig.SUNNY_MODEL_BASE_URL
    val privateBetaOriginConfigured: Boolean
        get() = !BuildConfig.SUNNY_PUBLIC_RELEASE && baseUrl.isNotBlank()
    val isConfigured: Boolean
        get() = SubscriptionEntitlements.modelDownloads.value?.isValid() == true ||
            privateBetaOriginConfigured

    data class Request(val url: String, val bearerToken: String = "")

    fun canAccess(tier: SunnyModelTier): Boolean =
        tier == SunnyModelTier.SUNNY_MOE && isConfigured

    fun requestFor(
        tier: SunnyModelTier,
        asset: ModelAsset,
        privateBetaBearerToken: String = "",
    ): Request {
        check(tier == SunnyModelTier.SUNNY_MOE) { "Only Sunny MoE has downloadable weights." }
        SubscriptionEntitlements.modelDownloads.value?.urlFor(asset.remotePath)?.let {
            return Request(it)
        }
        check(privateBetaOriginConfigured) {
            "A current Pro model-download authorization is required."
        }
        check(privateBetaBearerToken.isNotBlank()) {
            "The private beta access token is required for this download."
        }
        return Request(baseUrl + asset.remotePath, privateBetaBearerToken)
    }
}
