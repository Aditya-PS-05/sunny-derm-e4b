package com.sunny.skin.inference.tier

/** User-facing subscription levels. Higher levels include lower-level access. */
enum class SunnyPlan(val displayName: String, internal val rank: Int) {
    FREE("Free", 0),
    PRO("Pro", 1),
}

/** The two inference products: included server AI and Pro on-device Sunny-MoE. */
enum class SunnyModelTier(
    val displayName: String,
    val requiredPlan: SunnyPlan,
    val localSizeLabel: String,
    val summary: String,
) {
    SUNNY_MOE(
        displayName = "Sunny MoE",
        requiredPlan = SunnyPlan.PRO,
        localSizeLabel = "3.08 GB",
        summary = "The Pro downloadable model. Runs privately and offline after installation.",
    ),
    PRO_CLOUD(
        displayName = "Sunny AI Cloud",
        requiredPlan = SunnyPlan.FREE,
        localSizeLabel = "Server",
        summary = "The included AI model runs on Sunny's server and requires no download.",
    ),
}

enum class InferencePlacement { ON_DEVICE, CLOUD }

data class InferenceRoute(
    val tier: SunnyModelTier,
    val placement: InferencePlacement,
)

/**
 * Entitlements must originate from a store/backend verification result. A value
 * that is not verified is deliberately treated as Free, even if it claims a
 * paid plan or a trial. Local preferences are never an authority for access.
 */
data class SunnyEntitlement(
    val paidPlan: SunnyPlan = SunnyPlan.FREE,
    val proTrialEndsAtMillis: Long? = null,
    val verified: Boolean = false,
    val verifiedUntilMillis: Long = Long.MIN_VALUE,
) {
    fun isCurrentlyVerified(nowMillis: Long): Boolean =
        verified && verifiedUntilMillis > nowMillis

    fun trialActive(nowMillis: Long): Boolean =
        isCurrentlyVerified(nowMillis) && (proTrialEndsAtMillis ?: Long.MIN_VALUE) > nowMillis

    fun effectivePlan(nowMillis: Long): SunnyPlan = when {
        !isCurrentlyVerified(nowMillis) -> SunnyPlan.FREE
        trialActive(nowMillis) -> SunnyPlan.PRO
        else -> paidPlan
    }
}

data class ModelAvailability(
    val sunnyMoeInstalled: Boolean,
    val proCloudAvailable: Boolean,
    val cloudProcessingConsented: Boolean,
)

enum class LocalDeliveryTrigger {
    EXPLICIT_USER_ACTION,
}

data class LocalDeliveryPolicy(
    val trigger: LocalDeliveryTrigger,
    val availableDuringTrial: Boolean,
)

/** Product rules shared by routing, settings, downloads and billing adapters. */
object SunnyAccessPolicy {
    fun canUse(
        tier: SunnyModelTier,
        entitlement: SunnyEntitlement,
        nowMillis: Long,
    ): Boolean = entitlement.effectivePlan(nowMillis).rank >= tier.requiredPlan.rank

    fun canDownloadLocal(
        tier: SunnyModelTier,
        entitlement: SunnyEntitlement,
        nowMillis: Long,
    ): Boolean = when (tier) {
        SunnyModelTier.SUNNY_MOE ->
            canUse(tier, entitlement, nowMillis) && !entitlement.trialActive(nowMillis)
        SunnyModelTier.PRO_CLOUD -> false
    }

    fun deliveryPolicy(tier: SunnyModelTier): LocalDeliveryPolicy = when (tier) {
        SunnyModelTier.SUNNY_MOE -> LocalDeliveryPolicy(
            trigger = LocalDeliveryTrigger.EXPLICIT_USER_ACTION,
            availableDuringTrial = false,
        )
        SunnyModelTier.PRO_CLOUD -> LocalDeliveryPolicy(
            trigger = LocalDeliveryTrigger.EXPLICIT_USER_ACTION,
            availableDuringTrial = false,
        )
    }

    /**
     * Selects a usable engine without silently uploading a photo. Cloud is
     * chosen only when the endpoint exists and explicit cloud consent is active.
     */
    fun preferredRoute(
        entitlement: SunnyEntitlement,
        availability: ModelAvailability,
        nowMillis: Long,
    ): InferenceRoute? {
        if (
            availability.proCloudAvailable &&
            availability.cloudProcessingConsented
        ) {
            return InferenceRoute(SunnyModelTier.PRO_CLOUD, InferencePlacement.CLOUD)
        }
        return if (
            availability.sunnyMoeInstalled &&
            canUse(SunnyModelTier.SUNNY_MOE, entitlement, nowMillis)
        ) {
            InferenceRoute(SunnyModelTier.SUNNY_MOE, InferencePlacement.ON_DEVICE)
        } else {
            null
        }
    }
}
