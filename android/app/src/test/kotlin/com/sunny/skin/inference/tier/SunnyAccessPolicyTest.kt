package com.sunny.skin.inference.tier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunnyAccessPolicyTest {
    private val now = 1_000_000L

    @Test
    fun unverifiedEntitlementGetsIncludedCloudButNotLocalMoe() {
        val forged = SunnyEntitlement(
            paidPlan = SunnyPlan.PRO,
            proTrialEndsAtMillis = now + 60_000,
            verified = false,
        )

        assertEquals(SunnyPlan.FREE, forged.effectivePlan(now))
        assertFalse(SunnyAccessPolicy.canUse(SunnyModelTier.SUNNY_MOE, forged, now))
        assertTrue(SunnyAccessPolicy.canUse(SunnyModelTier.PRO_CLOUD, forged, now))
        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.SUNNY_MOE, forged, now))
        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.PRO_CLOUD, forged, now))
    }

    @Test
    fun proTrialUsesCloudWithConsentButProIsNeverDownloadable() {
        val trial = SunnyEntitlement(
            proTrialEndsAtMillis = now + 60_000,
            verified = true,
            verifiedUntilMillis = now + 60_000,
        )
        val available = ModelAvailability(
            sunnyMoeInstalled = true,
            proCloudAvailable = true,
            cloudProcessingConsented = true,
        )

        assertEquals(
            InferenceRoute(SunnyModelTier.PRO_CLOUD, InferencePlacement.CLOUD),
            SunnyAccessPolicy.preferredRoute(trial, available, now),
        )
        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.SUNNY_MOE, trial, now))
        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.PRO_CLOUD, trial, now))
    }

    @Test
    fun cloudIsNeverSelectedWithoutExplicitConsent() {
        val pro = SunnyEntitlement(
            paidPlan = SunnyPlan.PRO,
            verified = true,
            verifiedUntilMillis = now + 60_000,
        )
        val available = ModelAvailability(
            sunnyMoeInstalled = true,
            proCloudAvailable = true,
            cloudProcessingConsented = false,
        )

        assertEquals(
            InferenceRoute(SunnyModelTier.SUNNY_MOE, InferencePlacement.ON_DEVICE),
            SunnyAccessPolicy.preferredRoute(pro, available, now),
        )
    }

    @Test
    fun localMoeIsProOnlyAndCloudIsNeverDownloaded() {
        val free = SunnyEntitlement()

        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.SUNNY_MOE, free, now))
        assertFalse(SunnyAccessPolicy.canDownloadLocal(SunnyModelTier.PRO_CLOUD, free, now))
        assertEquals(
            LocalDeliveryTrigger.EXPLICIT_USER_ACTION,
            SunnyAccessPolicy.deliveryPolicy(SunnyModelTier.SUNNY_MOE).trigger,
        )
    }

    @Test
    fun freeUserCanUseConsentedCloudRoute() {
        val availability = ModelAvailability(
            sunnyMoeInstalled = false,
            proCloudAvailable = true,
            cloudProcessingConsented = true,
        )

        assertEquals(
            InferenceRoute(SunnyModelTier.PRO_CLOUD, InferencePlacement.CLOUD),
            SunnyAccessPolicy.preferredRoute(SunnyEntitlement(), availability, now),
        )
    }

    @Test
    fun noInstalledMoeAndNoCloudMeansNoRoute() {
        val availability = ModelAvailability(
            sunnyMoeInstalled = false,
            proCloudAvailable = false,
            cloudProcessingConsented = false,
        )

        assertNull(SunnyAccessPolicy.preferredRoute(SunnyEntitlement(), availability, now))
    }
}
