package com.sunny.skin.subscription

import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.tier.SunnyEntitlement
import com.sunny.skin.inference.tier.SunnyPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CloudQuota(
    val monthlyLimit: Int,
    val monthlyRemaining: Int,
    val dailyLimit: Int,
    val dailyRemaining: Int,
    val monthResetsAtMillis: Long,
    val dayResetsAtMillis: Long,
) {
    val exhausted: Boolean get() = monthlyRemaining <= 0 || dailyRemaining <= 0
}

data class CloudInferenceAuthorization(
    val baseUrl: String,
    val bearerToken: String,
    val expiresAtMillis: Long,
    val quota: CloudQuota,
) {
    fun isValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        baseUrl.startsWith("https://") && bearerToken.isNotBlank() && expiresAtMillis > nowMillis
}

data class ModelDownloadAuthorization(
    val expiresAtMillis: Long,
    val assetUrls: Map<String, String>,
) {
    fun isValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis > nowMillis && assetUrls.isNotEmpty() &&
            assetUrls.values.all { it.startsWith("https://") }

    fun urlFor(remotePath: String, nowMillis: Long = System.currentTimeMillis()): String? =
        takeIf { it.isValid(nowMillis) }?.assetUrls?.get(remotePath)
}

/**
 * In-memory bridge for a future Play Billing + backend verification adapter.
 * Entitlements are intentionally not restored from local preferences: every app
 * start must re-query an authority before cloud inference is enabled.
 */
object SubscriptionEntitlements {
    private val _current = MutableStateFlow(SunnyEntitlement())
    val current: StateFlow<SunnyEntitlement> = _current.asStateFlow()
    private val _cloudInference = MutableStateFlow<CloudInferenceAuthorization?>(null)
    val cloudInference: StateFlow<CloudInferenceAuthorization?> = _cloudInference.asStateFlow()
    private val _modelDownloads = MutableStateFlow<ModelDownloadAuthorization?>(null)
    val modelDownloads: StateFlow<ModelDownloadAuthorization?> = _modelDownloads.asStateFlow()

    /**
     * Effective access used by app features. Local debug builds without the
     * unfinished store backend act as Pro so physical-device model testing is
     * possible; every distributable build remains fail-closed on verification.
     */
    fun accessEntitlement(): SunnyEntitlement = if (
        BuildConfig.DEBUG && BuildConfig.SUNNY_ENTITLEMENT_API_URL.isBlank()
    ) {
        SunnyEntitlement(
            paidPlan = SunnyPlan.PRO,
            verified = true,
            verifiedUntilMillis = Long.MAX_VALUE,
        )
    } else {
        _current.value
    }

    fun publishVerified(
        entitlement: SunnyEntitlement,
        cloudInference: CloudInferenceAuthorization? = null,
        modelDownloads: ModelDownloadAuthorization? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        require(entitlement.verified) { "Only verified entitlements may be published." }
        require(entitlement.verifiedUntilMillis > nowMillis) {
            "An expired entitlement cannot be published."
        }
        require(cloudInference == null || cloudInference.isValid(nowMillis)) {
            "Cloud inference authorization is invalid or expired."
        }
        require(modelDownloads == null || modelDownloads.isValid(nowMillis)) {
            "Model download authorization is invalid or expired."
        }
        require(
            modelDownloads == null || entitlement.effectivePlan(nowMillis) == SunnyPlan.PRO,
        ) { "Only verified Pro access may receive model download URLs." }
        _current.value = entitlement
        _cloudInference.value = cloudInference
        _modelDownloads.value = modelDownloads
    }

    /**
     * A release-signed private beta may use an operator-issued gateway token
     * before Play products are active. The gateway still authenticates every
     * inference and model request; this only unlocks beta UI/features locally.
     * Public release builds can never enter this path.
     */
    fun publishPrivateBetaAccess(bearerToken: String) {
        check(!BuildConfig.SUNNY_PUBLIC_RELEASE) {
            "Private beta access cannot be enabled in a public release."
        }
        check(BuildConfig.SUNNY_INFERENCE_API_URL.isNotBlank()) {
            "Private beta access requires a configured HTTPS inference gateway."
        }
        require(bearerToken.length >= 32) { "Private beta access token is invalid." }
        _current.value = SunnyEntitlement(
            paidPlan = SunnyPlan.PRO,
            verified = true,
            verifiedUntilMillis = Long.MAX_VALUE,
        )
        _cloudInference.value = null
        _modelDownloads.value = null
    }

    /** Optimistic local mirror; the server remains authoritative for enforcement. */
    fun recordCloudAnalysis() {
        val current = _cloudInference.value ?: return
        _cloudInference.value = current.copy(
            quota = current.quota.copy(
                monthlyRemaining = (current.quota.monthlyRemaining - 1).coerceAtLeast(0),
                dailyRemaining = (current.quota.dailyRemaining - 1).coerceAtLeast(0),
            ),
        )
    }

    fun updateCloudQuota(quota: CloudQuota) {
        val current = _cloudInference.value ?: return
        _cloudInference.value = current.copy(quota = quota)
    }

    fun clear() {
        _current.value = SunnyEntitlement()
        _cloudInference.value = null
        _modelDownloads.value = null
    }
}
