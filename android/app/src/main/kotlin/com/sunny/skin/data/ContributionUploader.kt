package com.sunny.skin.data

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.sunny.skin.BuildConfig
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.network.EndpointPolicy
import com.sunny.skin.network.JsonHttpRequest
import com.sunny.skin.network.UrlConnectionJsonTransport
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ContributionContract {
    private fun fields(analysis: Analysis) = JSONObject().apply {
        put("lesion_type", analysis.lesionType)
        put("colour", analysis.colour)
        put("symmetry", analysis.symmetry)
        put("borders", analysis.borders)
        put("texture", analysis.texture)
        put("summary", analysis.summary)
    }

    fun payload(
        imageBase64: String,
        modelOutput: Analysis,
        corrected: Analysis?,
        bodyZone: String,
        device: String,
        appVersion: String,
    ): ByteArray = JSONObject().apply {
        put("image_b64", imageBase64)
        put("model_output", fields(modelOutput))
        corrected?.let {
            put("corrected_output", fields(it))
            put("corrected", true)
        }
        put("body_zone", bodyZone)
        put("device", device)
        put("app_version", appVersion)
    }.toString().toByteArray(Charsets.UTF_8)
}

/**
 * Uploads a single opted-in beta contribution — a real phone photo, the model's
 * output, and (when the user corrected it via Edit) the corrected fields — to the
 * collection endpoint, building a labeled dataset for re-fine-tuning.
 *
 * Fire-and-forget and defensive: it NEVER throws to the caller and does nothing
 * unless a collection URL is configured. The caller is responsible for checking
 * the user's opt-in first, so this class carries no consent logic by itself.
 */
object ContributionUploader {

    /** @param corrected the user-edited fields, when this contribution is a correction. */
    suspend fun submit(
        bitmap: Bitmap,
        modelOutput: Analysis,
        corrected: Analysis?,
        bodyZone: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.SUNNY_PUBLIC_RELEASE) return@withContext false
        val base = BuildConfig.SUNNY_CONTRIBUTE_URL
        if (base.isBlank()) return@withContext false
        repeat(MAX_ATTEMPTS) { attempt ->
            val sent = runCatching {
                val jpeg = encodeForUpload(bitmap)
                val endpoint = EndpointPolicy.resolve(
                    base,
                    "/contribute",
                    BuildConfig.DEBUG && BuildConfig.SUNNY_ALLOW_INSECURE_BETA_ENDPOINTS,
                )
                val payload = ContributionContract.payload(
                    imageBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
                    modelOutput = modelOutput,
                    corrected = corrected,
                    bodyZone = bodyZone,
                    device = Build.MODEL,
                    appVersion = BuildConfig.VERSION_NAME,
                )
                UrlConnectionJsonTransport().post(
                    JsonHttpRequest(
                        url = endpoint,
                        payload = payload,
                        bearerToken = BuildConfig.SUNNY_CONTRIBUTE_API_TOKEN,
                        connectTimeoutMs = 15_000,
                        readTimeoutMs = 30_000,
                    ),
                ).code in 200..299
            }.getOrDefault(false)
            if (sent) return@withContext true
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_BASE_DELAY_MS shl attempt)
        }
        false
    }

    private fun encodeForUpload(bitmap: Bitmap): ByteArray {
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
                    "Could not encode contribution photo."
                }
                output.toByteArray().also {
                    check(it.size <= MAX_IMAGE_BYTES) { "Contribution photo is too large." }
                }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private const val MAX_IMAGE_DIMENSION = 1600
    private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_BASE_DELAY_MS = 500L
}
