package com.sunny.skin.network

import java.io.IOException
import java.io.InputStream
import java.net.URL

object EndpointPolicy {
    fun resolve(baseUrl: String, path: String, allowCleartext: Boolean): URL {
        val base = URL(baseUrl)
        require(base.protocol == "https" || (base.protocol == "http" && allowCleartext)) {
            "Endpoint must use HTTPS. Cleartext HTTP is allowed only in an explicit debug beta."
        }
        require(base.host.isNotBlank() && base.userInfo == null) { "Invalid endpoint host." }
        return URL(base.toString().trimEnd('/') + path)
    }

    fun readTextLimited(input: InputStream?, maxChars: Int = 256 * 1024): String {
        if (input == null) return ""
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8 * 1024)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                if (result.length + read > maxChars) {
                    throw IOException("Server response exceeded $maxChars characters.")
                }
                result.append(buffer, 0, read)
            }
            result.toString()
        }
    }
}
