package com.sunny.skin.subscription

import com.sunny.skin.inference.tier.SunnyEntitlement
import com.sunny.skin.inference.tier.SunnyPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionEntitlementsTest {
    @After
    fun tearDown() {
        SubscriptionEntitlements.clear()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnverifiedEntitlements() {
        SubscriptionEntitlements.publishVerified(
            SunnyEntitlement(paidPlan = SunnyPlan.PRO, verified = false),
        )
    }

    @Test
    fun verifiedEntitlementIsProcessScopedAndCanBeCleared() {
        SubscriptionEntitlements.publishVerified(
            SunnyEntitlement(
                paidPlan = SunnyPlan.PRO,
                verified = true,
                verifiedUntilMillis = System.currentTimeMillis() + 60_000,
            ),
        )

        assertTrue(SubscriptionEntitlements.current.value.verified)
        assertEquals(SunnyPlan.PRO, SubscriptionEntitlements.current.value.paidPlan)

        SubscriptionEntitlements.clear()
        assertFalse(SubscriptionEntitlements.current.value.verified)
        assertEquals(SunnyPlan.FREE, SubscriptionEntitlements.current.value.paidPlan)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExpiredVerifiedEntitlement() {
        val now = 1_000_000L
        SubscriptionEntitlements.publishVerified(
            SunnyEntitlement(
                paidPlan = SunnyPlan.PRO,
                verified = true,
                verifiedUntilMillis = now,
            ),
            nowMillis = now,
        )
    }

    @Test
    fun mirrorsSuccessfulCloudConsumptionWithoutGrantingAuthority() {
        val now = System.currentTimeMillis()
        SubscriptionEntitlements.publishVerified(
            entitlement = SunnyEntitlement(
                paidPlan = SunnyPlan.FREE,
                verified = true,
                verifiedUntilMillis = now + 60_000,
            ),
            cloudInference = CloudInferenceAuthorization(
                baseUrl = "https://cloud.example/v1/inference",
                bearerToken = "signed-token",
                expiresAtMillis = now + 60_000,
                quota = CloudQuota(
                    monthlyLimit = 5,
                    monthlyRemaining = 5,
                    dailyLimit = 2,
                    dailyRemaining = 2,
                    monthResetsAtMillis = now + 86_400_000,
                    dayResetsAtMillis = now + 3_600_000,
                ),
            ),
            nowMillis = now,
        )

        SubscriptionEntitlements.recordCloudAnalysis()

        assertEquals(4, SubscriptionEntitlements.cloudInference.value!!.quota.monthlyRemaining)
        assertEquals(1, SubscriptionEntitlements.cloudInference.value!!.quota.dailyRemaining)
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeEntitlementCannotReceiveModelDownloadUrls() {
        val now = System.currentTimeMillis()
        SubscriptionEntitlements.publishVerified(
            entitlement = SunnyEntitlement(
                paidPlan = SunnyPlan.FREE,
                verified = true,
                verifiedUntilMillis = now + 60_000,
            ),
            modelDownloads = ModelDownloadAuthorization(
                expiresAtMillis = now + 60_000,
                assetUrls = mapOf("model/file.gguf" to "https://models.example/signed"),
            ),
            nowMillis = now,
        )
    }
}
