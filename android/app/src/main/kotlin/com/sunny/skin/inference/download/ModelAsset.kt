package com.sunny.skin.inference.download

import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.subscription.SubscriptionEntitlements

/** One immutable file in the install-time Sunny Offline pack. */
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
 * The only local pack accepted by this app. Lite, Medium, Sunny-MoE, and the
 * legacy HAM10000-derived GGUF pair are intentionally absent.
 */
object ModelPackCatalog {
    private const val remoteDir = "sunny-pad-smolvlm-500m-mobile256-v2-gguf"

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
            asset("sunny-pad-smolvlm-500m-Q8_0.gguf", 436_805_632L, "36bfbd253ea5edec715a510d97546085001c843f6616aeef5bfa818b36ce69df"),
            asset("sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf", 197_108_288L, "c084c1c8259c3eb239f0303e2ba10d7db6585a22c1f44b7880774f331a00ce9a"),
            asset("derm.gbnf", 303L, "ffc98c058fdf0f34e9d529231e7572be5ee77893a997584e1670cdef31940f09"),
            asset("THIRD_PARTY_NOTICES.txt", 2_088L, "ded7a876f2e6501c7a263e3fafaef98684165ae325eac5c81fcb8b5aeb352866"),
            asset("Apache-2.0.txt", 11_357L, "84829002701217076a39a84808ec52e45088ddbf9f6623896e5550becd8e09be"),
            asset("manifest.json", 3_696L, "e6f264af36b4b16f25e50bc37520ace15ccbfb8a9084377650b57f6df035fa21"),
        ),
        // The 605 MiB pack still needs physical Android RSS/thermal validation.
        minimumRamBytes = 3_000_000_000L,
    )

    fun publishedPack(tier: SunnyModelTier): ModelPack? = when (tier) {
        SunnyModelTier.SUNNY_MOE -> sunnyMoe
        SunnyModelTier.PRO_CLOUD -> null
    }
}

/** Legacy signed-download source retained for older/debug installations. */
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
        check(tier == SunnyModelTier.SUNNY_MOE) { "Only Sunny Offline has downloadable weights." }
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
