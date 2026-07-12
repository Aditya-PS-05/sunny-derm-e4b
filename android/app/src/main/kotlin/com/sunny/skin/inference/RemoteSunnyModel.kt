package com.sunny.skin.inference

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
class RemoteSunnyModel(baseUrl: String) : SunnyModel {
    private val endpoint = baseUrl.trimEnd('/') + "/v1/chat/completions"

    override val version: String = "Sunny-Gemma4-E4B (server)"

    @Volatile private var ready = false
    override val isReady: Boolean get() = ready

    override suspend fun warmUp() { ready = true }

    override suspend fun describeRaw(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val jpeg = ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, bos); bos.toByteArray()
        }
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

        // Image FIRST, then the schema prompt (F-03), greedy + capped (F-04).
        val body = JSONObject().apply {
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", dataUri))
                    })
                    put(JSONObject().apply {
                        put("type", "text"); put("text", Prompt.SCHEMA_PROMPT)
                    })
                })
            }))
            // The server model reasons before the answer, so it needs a wider
            // budget than the on-device path to reach the final schema block.
            put("max_tokens", 1024)
            put("temperature", Prompt.TEMPERATURE.toDouble())
        }

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            ready = false
            throw RuntimeException("inference API HTTP $code: ${text.take(200)}")
        }
        ready = true
        val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        cleanSchemaBlock(content)
    }

    /**
     * The server model emits reasoning/channel scaffolding before the final
     * answer. Keep the last schema block (from the final "Lesion Type:") and drop
     * any residual channel/special markers, so [SunnyDescriber]'s parser sees the
     * same six clean lines the on-device model would produce.
     */
    private fun cleanSchemaBlock(raw: String): String {
        val start = raw.lastIndexOf("Lesion Type:")
        val block = if (start >= 0) raw.substring(start) else raw
        return block.replace(Regex("<[|/a-zA-Z_]{0,32}>"), "").trim()
    }

    override fun close() {}
}
