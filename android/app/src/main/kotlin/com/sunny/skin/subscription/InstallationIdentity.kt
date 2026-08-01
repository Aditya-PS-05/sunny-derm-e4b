package com.sunny.skin.subscription

import android.content.Context
import java.security.SecureRandom

/**
 * Random app-install identifier used only for anonymous Free quota accounting.
 * It contains no hardware, account, advertising, or user identifier.
 */
class InstallationIdentity(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sunny_installation_identity",
        Context.MODE_PRIVATE,
    )

    val value: String
        get() = preferences.getString(KEY_ID, null)?.takeIf(::isValid)
            ?: generate().also { id ->
                check(preferences.edit().putString(KEY_ID, id).commit()) {
                    "Could not persist the anonymous installation identifier."
                }
            }

    private fun generate(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isValid(value: String): Boolean =
        value.length in 20..128 && value.all { it.isLetterOrDigit() || it in "._-" }

    private companion object {
        const val KEY_ID = "anonymous_installation_id"
    }
}
