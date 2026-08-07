package com.sunny.skin.subscription

import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.tier.SunnyPlan
import com.sunny.skin.network.JsonHttpResponse
import com.sunny.skin.network.JsonHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayEntitlementVerifierTest {
    @Test
    fun parsesShortLivedServerVerifiedProWithSignedDownloads() {
        val now = System.currentTimeMillis()
        val body = """{
            "plan":"PRO",
            "proTrialEndsAtMillis":null,
            "verifiedUntilMillis":${now + 15 * 60_000L},
            "cloudInference":{
              "baseUrl":"https://inference.example/v1/inference",
              "bearerToken":"signed-cloud-token",
              "expiresAtMillis":${now + 60 * 60_000L},
              "quota":{
                "monthlyLimit":100,"monthlyRemaining":99,
                "dailyLimit":25,"dailyRemaining":24,
                "monthResetsAtMillis":${now + 86_400_000L},
                "dayResetsAtMillis":${now + 3_600_000L}
              }
            },
            "modelDownloads":{
              "expiresAtMillis":${now + 60 * 60_000L},
              "assets":{
                "sunny-pad-smolvlm-500m-mobile256-v2-gguf/manifest.json":"https://models.example/signed-manifest"
              }
            }
        }""".trimIndent()
        val verifier = verifierReturning(200, body)

        val result = verifier.verify(
            "purchase-token-long-enough",
            listOf(BuildConfig.SUNNY_PRO_PRODUCT_ID),
            TEST_INSTALLATION_ID,
        ).getOrThrow()

        assertEquals(SunnyPlan.PRO, result.entitlement.paidPlan)
        assertTrue(result.cloudInference!!.isValid(now))
        assertEquals(99, result.cloudInference.quota.monthlyRemaining)
        assertTrue(result.modelDownloads!!.isValid(now))
    }

    @Test
    fun rejectsExpiredServerVerdict() {
        val body = """{
            "plan":"PRO",
            "proTrialEndsAtMillis":null,
            "verifiedUntilMillis":1
        }""".trimIndent()

        assertTrue(
            verifierReturning(200, body).verify(
                "purchase-token-long-enough",
                listOf(BuildConfig.SUNNY_PRO_PRODUCT_ID),
                TEST_INSTALLATION_ID,
            ).isFailure,
        )
    }

    @Test
    fun rejectsBackendFailureWithoutPublishingAccess() {
        assertTrue(
            verifierReturning(401, "{\"error\":\"subscription_inactive\"}").verify(
                "purchase-token-long-enough",
                listOf(BuildConfig.SUNNY_PRO_PRODUCT_ID),
                TEST_INSTALLATION_ID,
            ).isFailure,
        )
    }

    @Test
    fun parsesAnonymousFreeQuota() {
        val now = System.currentTimeMillis()
        val body = """{
            "plan":"FREE","proTrialEndsAtMillis":null,
            "verifiedUntilMillis":${now + 60_000L},
            "cloudInference":{
              "baseUrl":"https://inference.example/v1/inference",
              "bearerToken":"free-token","expiresAtMillis":${now + 60_000L},
              "quota":{"monthlyLimit":5,"monthlyRemaining":5,"dailyLimit":2,
                "dailyRemaining":2,"monthResetsAtMillis":${now + 86_400_000L},
                "dayResetsAtMillis":${now + 3_600_000L}}
            },"modelDownloads":null
        }""".trimIndent()

        val result = verifierReturning(200, body).issueFree(TEST_INSTALLATION_ID).getOrThrow()

        assertEquals(SunnyPlan.FREE, result.entitlement.paidPlan)
        assertEquals(5, result.cloudInference!!.quota.monthlyRemaining)
        assertTrue(result.modelDownloads == null)
    }

    private fun verifierReturning(code: Int, body: String) = PlayEntitlementVerifier(
        baseUrl = "https://entitlements.example",
        transport = JsonHttpTransport { JsonHttpResponse(code, body) },
    )

    private companion object {
        const val TEST_INSTALLATION_ID = "0123456789abcdef0123456789abcdef"
    }
}
