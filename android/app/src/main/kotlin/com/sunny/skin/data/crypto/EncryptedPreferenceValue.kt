package com.sunny.skin.data.crypto

import android.content.Context
import android.util.Base64

/** Small AES-GCM envelope for sensitive values that must remain in SharedPreferences. */
object EncryptedPreferenceValue {
    private const val PREFIX = "sunny:v1:"

    fun encode(context: Context, plain: String): String =
        PREFIX + Base64.encodeToString(
            CryptoManager.encrypt(context.applicationContext, plain.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )

    /** Returns null for a corrupt encrypted value. Legacy plaintext is returned for migration. */
    fun decode(context: Context, stored: String): String? {
        if (!isEncrypted(stored)) return stored
        return runCatching {
            val blob = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            CryptoManager.decrypt(context.applicationContext, blob).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)
}
