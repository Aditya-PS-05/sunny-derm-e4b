package com.sunny.skin.data

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.sunny.skin.BuildConfig
import com.sunny.skin.data.model.Analysis
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    private fun fields(a: Analysis) = JSONObject().apply {
        put("lesion_type", a.lesionType)
        put("colour", a.colour)
        put("symmetry", a.symmetry)
        put("borders", a.borders)
        put("texture", a.texture)
        put("summary", a.summary)
    }

    /** @param corrected the user-edited fields, when this contribution is a correction. */
    suspend fun submit(
        bitmap: Bitmap,
        modelOutput: Analysis,
        corrected: Analysis?,
        bodyZone: String,
    ) = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUNNY_CONTRIBUTE_URL
        if (base.isBlank()) return@withContext
        runCatching {
            val jpeg = ByteArrayOutputStream().use { bos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos); bos.toByteArray()
            }
            val body = JSONObject().apply {
                put("image_b64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                put("model_output", fields(modelOutput))
                corrected?.let { put("corrected_output", fields(it)); put("corrected", true) }
                put("body_zone", bodyZone)
                put("device", Build.MODEL)
                put("app_version", BuildConfig.VERSION_NAME)
            }
            val conn = (URL(base.trimEnd('/') + "/contribute").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode // consume the response so the request completes
            conn.disconnect()
        }
        Unit
    }
}
