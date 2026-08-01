package com.sunny.skin.network

import java.net.HttpURLConnection
import java.net.URL

data class JsonHttpRequest(
    val url: URL,
    val payload: ByteArray,
    val bearerToken: String = "",
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseChars: Int = 256 * 1024,
    val analysisId: String? = null,
)

data class JsonHttpResponse(val code: Int, val body: String)

fun interface JsonHttpTransport {
    fun post(request: JsonHttpRequest): JsonHttpResponse
}

class UrlConnectionJsonTransport(
    private val open: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) : JsonHttpTransport {
    override fun post(request: JsonHttpRequest): JsonHttpResponse {
        val connection = open(request.url).apply {
            requestMethod = "POST"
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (request.bearerToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${request.bearerToken}")
            }
            request.analysisId?.takeIf {
                it.length in 16..128 && it.all { char -> char.isLetterOrDigit() || char in "._-" }
            }?.let { setRequestProperty("X-Sunny-Analysis-Id", it) }
            setFixedLengthStreamingMode(request.payload.size)
        }
        return try {
            connection.outputStream.use { it.write(request.payload) }
            val code = connection.responseCode
            val body = EndpointPolicy.readTextLimited(
                if (code in 200..299) connection.inputStream else connection.errorStream,
                request.maxResponseChars,
            )
            JsonHttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
