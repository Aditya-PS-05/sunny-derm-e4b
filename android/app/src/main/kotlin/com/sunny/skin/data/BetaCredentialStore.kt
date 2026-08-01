package com.sunny.skin.data

import android.content.Context
import com.sunny.skin.data.crypto.EncryptedPreferenceValue

/** Runtime-provisioned beta credentials. Secrets are never compiled into the APK. */
class BetaCredentialStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val inferenceToken: String get() = read(KEY_INFERENCE)
    val contributionToken: String get() = read(KEY_CONTRIBUTION)

    fun update(inferenceToken: String? = null, contributionToken: String? = null) {
        val edit = prefs.edit()
        inferenceToken?.trim()?.let { value ->
            if (value.isEmpty()) edit.remove(KEY_INFERENCE)
            else edit.putString(KEY_INFERENCE, EncryptedPreferenceValue.encode(app, value))
        }
        contributionToken?.trim()?.let { value ->
            if (value.isEmpty()) edit.remove(KEY_CONTRIBUTION)
            else edit.putString(KEY_CONTRIBUTION, EncryptedPreferenceValue.encode(app, value))
        }
        check(edit.commit()) { "Could not securely store beta credentials." }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Could not clear beta credentials." }
    }

    private fun read(key: String): String {
        val stored = prefs.getString(key, null) ?: return ""
        return EncryptedPreferenceValue.decode(app, stored).orEmpty()
    }

    private companion object {
        const val PREFS = "sunny_beta_credentials"
        const val KEY_INFERENCE = "inference_token"
        const val KEY_CONTRIBUTION = "contribution_token"
    }
}
