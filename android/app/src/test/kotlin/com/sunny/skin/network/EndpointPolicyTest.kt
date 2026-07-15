package com.sunny.skin.network

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun httpsEndpoint_isAccepted() {
        val url = EndpointPolicy.resolve("https://beta.example", "/v1/test", false)

        assertEquals("https://beta.example/v1/test", url.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextEndpoint_isRejectedByDefault() {
        EndpointPolicy.resolve("http://beta.example", "/v1/test", false)
    }

    @Test
    fun explicitDebugCleartext_keepsBetaCompatibility() {
        val url = EndpointPolicy.resolve("http://127.0.0.1:8080/", "/v1/test", true)

        assertEquals("http://127.0.0.1:8080/v1/test", url.toString())
    }

    @Test(expected = IOException::class)
    fun oversizedResponse_isRejected() {
        EndpointPolicy.readTextLimited(ByteArrayInputStream("12345".toByteArray()), maxChars = 4)
    }
}
