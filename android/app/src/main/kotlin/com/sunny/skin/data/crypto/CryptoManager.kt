package com.sunny.skin.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * On-device encryption keys, rooted in the AndroidKeyStore (P-01, security review).
 *
 * A non-exportable AES-256 **master key** lives in the Keystore (hardware-backed
 * where available) and never leaves it. It wraps a random 32-byte **data key**
 * whose wrapped form is kept in plain SharedPreferences — useless without the
 * Keystore. The data key is the SQLCipher passphrase for the Room DB and the
 * AES-GCM key for photo/PDF files. Encryption is always-on and independent of the
 * app-lock PIN, so data stays protected with or without a PIN and a PIN change
 * never re-encrypts anything.
 */
object CryptoManager {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "sunny_master_key"
    private const val PREFS = "sunny_crypto"
    private const val KEY_WRAPPED = "data_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    @Volatile private var dataKey: ByteArray? = null

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun gcmEncrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain) // iv (12) || ciphertext+tag
    }

    private fun gcmDecrypt(key: SecretKey, blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun loadDataKey(context: Context): ByteArray {
        dataKey?.let { return it }
        return synchronized(this) {
            dataKey ?: run {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val stored = prefs.getString(KEY_WRAPPED, null)
                val key = if (stored != null) {
                    gcmDecrypt(masterKey(), Base64.decode(stored, Base64.NO_WRAP))
                } else {
                    val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val wrapped = gcmEncrypt(masterKey(), fresh)
                    prefs.edit().putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply()
                    fresh
                }
                dataKey = key
                key
            }
        }
    }

    private fun fileKey(context: Context): SecretKey = SecretKeySpec(loadDataKey(context), "AES")

    /**
     * A FRESH copy of the SQLCipher passphrase each call — SQLCipher's
     * SupportOpenHelperFactory zeroes the array it is handed.
     */
    fun dbPassphrase(context: Context): ByteArray = loadDataKey(context).copyOf()

    /** AES-256-GCM encrypt a file blob (iv prepended). */
    fun encrypt(context: Context, plain: ByteArray): ByteArray = gcmEncrypt(fileKey(context), plain)

    /** Decrypt a blob produced by [encrypt]. Throws on tamper/wrong key. */
    fun decrypt(context: Context, blob: ByteArray): ByteArray = gcmDecrypt(fileKey(context), blob)
}
