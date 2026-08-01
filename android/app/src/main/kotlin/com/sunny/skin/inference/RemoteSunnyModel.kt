package com.sunny.skin.inference

import android.graphics.Bitmap
import android.util.Base64
import com.sunny.skin.network.EndpointPolicy
import com.sunny.skin.network.JsonHttpRequest
import com.sunny.skin.network.JsonHttpTransport
import com.sunny.skin.network.UrlConnectionJsonTransport
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RemoteInferenceContract {
    fun requestPayload(imageDataUri: String): ByteArray = JSONObject().apply {
        put("messages", JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", imageDataUri))
                })
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", Prompt.SCHEMA_PROMPT)
                })
            })
        }))
        put("max_tokens", 1024)
        put("temperature", Prompt.TEMPERATURE.toDouble())
    }.toString().toByteArray(Charsets.UTF_8)

    fun parseResponse(response: String): String {
        val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val start = content.lastIndexOf("Lesion Type:")
        val block = if (start >= 0) content.substring(start) else content
        return block.replace(Regex("<[|/a-zA-Z_]{0,32}>"), "").trim()
    }
}

/**
 * INTERIM "server method": instead of running on-device, this sends the scan
 * photo to a remote llama.cpp inference server (OpenAI-compatible
 * /v1/chat/completions with vision) and returns its raw six-line text, which
 * [SunnyDescriber] then parses and guards exactly like the on-device path.
 *
 * This is a temporary bridge while the on-device model is finalized: unlike the
 * on-device path, the photo for the current scan DOES leave the device to the
 * configured API. Enabled only when [com.sunny.skin.BuildConfig.SUNNY_INFERENCE_API_URL]
 * is set at build time.
 */
class RemoteSunnyModel(
    baseUrl: String,
    private val apiToken: String,
    private val transport: JsonHttpTransport = UrlConnectionJsonTransport(),
    private val onAnalysisConsumed: () -> Unit = {},
) : SunnyModel {
    private val endpoint = EndpointPolicy.resolve(baseUrl, "/v1/chat/completions")
    private val mirroredAnalysisIds = ConcurrentHashMap.newKeySet<String>()

    override val version: String = "Sunny-Gemma4-E4B (server)"

    @Volatile private var ready = false
    override val isReady: Boolean get() = ready

    override suspend fun warmUp() { ready = true }

    override suspend fun describeRaw(bitmap: Bitmap, analysisId: String): String = withContext(Dispatchers.IO) {
        val jpeg = encodeForServer(bitmap)
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

        // Image FIRST, then the schema prompt (F-03). The server model reasons
        // before its schema, so it uses a wider internal token budget.
        val payload = RemoteInferenceContract.requestPayload(dataUri)
        try {
            val response = transport.post(
                JsonHttpRequest(
                    url = endpoint,
                    payload = payload,
                    bearerToken = apiToken,
                    connectTimeoutMs = 20_000,
                    readTimeoutMs = 180_000,
                    analysisId = analysisId,
                ),
            )
            if (response.code !in 200..299) {
                if (response.code == 429) updateQuotaFromError(response.body)
                throw RuntimeException(
                    "inference API HTTP ${response.code}: ${response.body.take(200)}",
                )
            }
            ready = true
            RemoteInferenceContract.parseResponse(response.body).also {
                if (mirroredAnalysisIds.add(analysisId)) onAnalysisConsumed()
            }
        } catch (error: Exception) {
            ready = false
            throw error
        }
    }

    private fun updateQuotaFromError(body: String) {
        runCatching {
            val quota = JSONObject(body).getJSONObject("quota")
            com.sunny.skin.subscription.SubscriptionEntitlements.updateCloudQuota(
                com.sunny.skin.subscription.CloudQuota(
                    monthlyLimit = quota.getInt("monthlyLimit"),
                    monthlyRemaining = quota.getInt("monthlyRemaining"),
                    dailyLimit = quota.getInt("dailyLimit"),
                    dailyRemaining = quota.getInt("dailyRemaining"),
                    monthResetsAtMillis = quota.getLong("monthResetsAtMillis"),
                    dayResetsAtMillis = quota.getLong("dayResetsAtMillis"),
                ),
            )
        }
    }

    private fun encodeForServer(bitmap: Bitmap): ByteArray {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxDimension > MAX_IMAGE_DIMENSION) {
            val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxDimension
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "Could not encode scan photo."
                }
                output.toByteArray().also {
                    check(it.size <= MAX_IMAGE_BYTES) { "Encoded scan photo is too large." }
                }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    override fun close() {}

    private companion object {
        const val MAX_IMAGE_DIMENSION = 1600
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }
}
