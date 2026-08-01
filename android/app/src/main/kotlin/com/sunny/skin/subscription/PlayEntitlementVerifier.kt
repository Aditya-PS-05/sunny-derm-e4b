package com.sunny.skin.subscription

import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.tier.SunnyEntitlement
import com.sunny.skin.inference.tier.SunnyPlan
import com.sunny.skin.network.EndpointPolicy
import com.sunny.skin.network.JsonHttpRequest
import com.sunny.skin.network.JsonHttpTransport
import com.sunny.skin.network.UrlConnectionJsonTransport
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

data class VerifiedPlayPurchase(
    val entitlement: SunnyEntitlement,
    val cloudInference: CloudInferenceAuthorization?,
    val modelDownloads: ModelDownloadAuthorization?,
)

/** Exchanges anonymous/free or Play purchase credentials for short-lived server access. */
class PlayEntitlementVerifier(
    private val baseUrl: String = BuildConfig.SUNNY_ENTITLEMENT_API_URL,
    private val transport: JsonHttpTransport = UrlConnectionJsonTransport(),
) {
    fun issueFree(installationId: String): Result<VerifiedPlayPurchase> = runCatching {
        require(baseUrl.isNotBlank()) { "The entitlement service is not configured." }
        requireInstallationId(installationId)
        request(
            path = "/v1/access/free",
            payload = JSONObject().put("installationId", installationId),
            allowFree = true,
        )
    }

    fun verify(
        purchaseToken: String,
        productIds: List<String>,
        installationId: String,
    ): Result<VerifiedPlayPurchase> =
        runCatching {
            require(baseUrl.isNotBlank()) { "The entitlement service is not configured." }
            requireInstallationId(installationId)
            require(purchaseToken.length in 20..4096) { "Invalid Play purchase token." }
            val allowed = setOf(BuildConfig.SUNNY_PRO_PRODUCT_ID)
            require(productIds.isNotEmpty() && productIds.all { it in allowed }) {
                "Purchase contains an unknown product."
            }
            request(
                path = "/v1/entitlements/google-play",
                payload = JSONObject()
                .put("purchaseToken", purchaseToken)
                .put("products", JSONArray(productIds))
                .put("installationId", installationId),
                allowFree = false,
            )
        }

    private fun request(path: String, payload: JSONObject, allowFree: Boolean): VerifiedPlayPurchase {
        val response = transport.post(
            JsonHttpRequest(
                url = EndpointPolicy.resolve(baseUrl, path),
                payload = payload.toString().toByteArray(Charsets.UTF_8),
                connectTimeoutMs = 15_000,
                readTimeoutMs = 30_000,
                maxResponseChars = 64 * 1024,
            ),
        )
        if (response.code !in 200..299) {
            throw IOException("Entitlement verification failed (HTTP ${response.code}).")
        }
        return parse(response.body, allowFree)
    }

    private fun parse(body: String, allowFree: Boolean): VerifiedPlayPurchase {
        val json = JSONObject(body)
        val now = System.currentTimeMillis()
        val verifiedUntil = json.getLong("verifiedUntilMillis")
        require(verifiedUntil > now) { "The server returned an expired entitlement." }
        val plan = runCatching { SunnyPlan.valueOf(json.getString("plan")) }
            .getOrElse { throw IllegalArgumentException("The server returned an unknown plan.") }
        require(allowFree || plan == SunnyPlan.PRO) {
            "A paid verification response did not grant Pro access."
        }
        val trialEnds = if (json.isNull("proTrialEndsAtMillis")) null
        else json.optLong("proTrialEndsAtMillis").takeIf { it > now }
        val cloud = json.optJSONObject("cloudInference")?.let {
            val quota = it.getJSONObject("quota")
            CloudInferenceAuthorization(
                baseUrl = it.getString("baseUrl"),
                bearerToken = it.getString("bearerToken"),
                expiresAtMillis = it.getLong("expiresAtMillis"),
                quota = CloudQuota(
                    monthlyLimit = quota.getInt("monthlyLimit"),
                    monthlyRemaining = quota.getInt("monthlyRemaining"),
                    dailyLimit = quota.getInt("dailyLimit"),
                    dailyRemaining = quota.getInt("dailyRemaining"),
                    monthResetsAtMillis = quota.getLong("monthResetsAtMillis"),
                    dayResetsAtMillis = quota.getLong("dayResetsAtMillis"),
                ),
            ).also { authorization ->
                require(authorization.isValid(now)) {
                    "The server returned invalid cloud inference authorization."
                }
            }
        }
        val modelDownloads = json.optJSONObject("modelDownloads")?.let { downloads ->
            val assetsJson = downloads.getJSONObject("assets")
            val assets = buildMap {
                val keys = assetsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, assetsJson.getString(key))
                }
            }
            ModelDownloadAuthorization(
                expiresAtMillis = downloads.getLong("expiresAtMillis"),
                assetUrls = assets,
            ).also { authorization ->
                require(authorization.isValid(now)) {
                    "The server returned invalid model download authorization."
                }
            }
        }
        return VerifiedPlayPurchase(
            entitlement = SunnyEntitlement(
                paidPlan = plan,
                proTrialEndsAtMillis = trialEnds,
                verified = true,
                verifiedUntilMillis = verifiedUntil,
            ),
            cloudInference = cloud,
            modelDownloads = modelDownloads,
        )
    }

    private fun requireInstallationId(value: String) {
        require(value.length in 20..128 && value.all { it.isLetterOrDigit() || it in "._-" }) {
            "Invalid installation identifier."
        }
    }
}
