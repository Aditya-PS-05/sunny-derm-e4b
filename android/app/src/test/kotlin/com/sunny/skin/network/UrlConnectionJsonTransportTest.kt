package com.sunny.skin.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlConnectionJsonTransportTest {
    @Test
    fun post_writesPayloadAndAuthorizationAndReadsSuccess() {
        val connection = FakeConnection(200, "{\"ok\":true}")
        val response = UrlConnectionJsonTransport { connection }.post(
            JsonHttpRequest(
                url = URL("https://beta.example/test"),
                payload = "{\"request\":1}".toByteArray(),
                bearerToken = "token-123",
                connectTimeoutMs = 1234,
                readTimeoutMs = 5678,
            ),
        )

        assertEquals(200, response.code)
        assertEquals("{\"ok\":true}", response.body)
        assertArrayEquals("{\"request\":1}".toByteArray(), connection.written.toByteArray())
        assertEquals("Bearer token-123", connection.headers["Authorization"])
        assertEquals(1234, connection.connectTimeout)
        assertEquals(5678, connection.readTimeout)
        assertTrue(connection.disconnected)
    }

    @Test
    fun post_readsErrorBodyAndAlwaysDisconnects() {
        val connection = FakeConnection(503, "temporarily unavailable")

        val response = UrlConnectionJsonTransport { connection }.post(
            JsonHttpRequest(
                url = URL("https://beta.example/test"),
                payload = byteArrayOf(),
                connectTimeoutMs = 100,
                readTimeoutMs = 100,
            ),
        )

        assertEquals(503, response.code)
        assertEquals("temporarily unavailable", response.body)
        assertTrue(connection.disconnected)
    }

    @Test(expected = IOException::class)
    fun post_rejectsOversizedResponse() {
        val connection = FakeConnection(200, "12345")
        try {
            UrlConnectionJsonTransport { connection }.post(
                JsonHttpRequest(
                    url = URL("https://beta.example/test"),
                    payload = byteArrayOf(),
                    connectTimeoutMs = 100,
                    readTimeoutMs = 100,
                    maxResponseChars = 4,
                ),
            )
        } finally {
            assertTrue(connection.disconnected)
        }
    }

    private class FakeConnection(
        private val status: Int,
        body: String,
    ) : HttpURLConnection(URL("https://beta.example")) {
        val written = ByteArrayOutputStream()
        val headers = mutableMapOf<String, String>()
        var disconnected = false
        private val response = body.toByteArray()

        override fun getOutputStream() = written
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun getErrorStream() = ByteArrayInputStream(response)
        override fun setRequestProperty(key: String, value: String) {
            headers[key] = value
        }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
}
