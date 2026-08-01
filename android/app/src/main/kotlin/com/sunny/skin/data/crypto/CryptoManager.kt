package com.sunny.skin.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * On-device encryption keys, rooted in the AndroidKeyStore (P-01, security review).
 *
 * A non-exportable AES-256 **master key** lives in the Keystore (hardware-backed
 * where available) and never leaves it. It wraps a random 32-byte **data key**
 * whose wrapped form is kept in SharedPreferences — useless without the
 * Keystore. The data key is the SQLCipher passphrase for the Room DB and the
 * AES-GCM key for photo/PDF files. Encryption is always on. When app lock is
 * enabled, the data key receives a second PIN-derived wrapping layer inside the
 * Keystore envelope, so unlocking the UI also unlocks the encryption key.
 */
object CryptoManager {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "sunny_master_key"
    private const val PREFS = "sunny_crypto"
    private const val KEY_WRAPPED = "data_key"
    private const val KEY_PIN_WRAPPED = "pin_wrapped_data_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12
    private const val PIN_SALT_LEN = 16
    private const val LEGACY_PIN_KDF_ITERATIONS = 600_000
    // The inner PIN layer is already wrapped by a non-exportable Android
    // Keystore key and app attempts are rate-limited. This target preserves the
    // layered protection without making every unlock take several seconds.
    private const val PIN_KDF_ITERATIONS = 210_000
    private val PIN_ENVELOPE_V2 = byteArrayOf(
        0x53, 0x55, 0x4e, 0x4e, 0x59, 0x50, 0x30, 0x32, // "SUNNYP02"
    )

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
                check(!prefs.contains(KEY_PIN_WRAPPED)) { "Sunny is locked." }
                val stored = prefs.getString(KEY_WRAPPED, null)
                val key = if (stored != null) {
                    gcmDecrypt(masterKey(), Base64.decode(stored, Base64.NO_WRAP))
                } else {
                    val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val wrapped = gcmEncrypt(masterKey(), fresh)
                    check(
                        prefs.edit().putString(
                            KEY_WRAPPED,
                            Base64.encodeToString(wrapped, Base64.NO_WRAP),
                        ).commit(),
                    ) { "Could not persist the encryption key." }
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

    /** Stream-decrypt a stored file blob without creating a plaintext temp file. */
    fun decryptTo(context: Context, input: InputStream, output: OutputStream) {
        val iv = ByteArray(IV_LEN)
        var offset = 0
        while (offset < iv.size) {
            val count = input.read(iv, offset, iv.size - offset)
            if (count < 0) break
            offset += count
        }
        require(offset == IV_LEN) { "encrypted file is truncated" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, fileKey(context), GCMParameterSpec(GCM_TAG_BITS, iv))
        CipherInputStream(input, cipher).use { decrypted -> decrypted.copyTo(output) }
    }

    /** True when the Keystore envelope additionally requires the user's app PIN. */
    fun isPinProtected(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(KEY_PIN_WRAPPED)

    /**
     * Re-wrap the data key under both Android Keystore and the app PIN. The
     * Keystore layer is outermost, so a copied preferences file cannot be used
     * for offline PIN guessing without access to this app's non-exportable key.
     */
    fun protectWithPin(context: Context, pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit)) { "PIN must contain four digits." }
        val app = context.applicationContext
        val current = loadDataKey(app)
        val salt = ByteArray(PIN_SALT_LEN).also { SecureRandom().nextBytes(it) }
        val derived = derivePinKey(pin, salt, PIN_KDF_ITERATIONS)
        try {
            val pinEnvelope = PIN_ENVELOPE_V2 + salt +
                gcmEncrypt(SecretKeySpec(derived, "AES"), current)
            val keystoreEnvelope = gcmEncrypt(masterKey(), pinEnvelope)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            check(
                prefs.edit()
                    .putString(KEY_PIN_WRAPPED, Base64.encodeToString(keystoreEnvelope, Base64.NO_WRAP))
                    .remove(KEY_WRAPPED)
                    .commit(),
            ) { "Could not enable PIN encryption." }
        } finally {
            derived.fill(0)
        }
    }

    /** Unlock the in-memory data key. Authentication failure never changes it. */
    fun unlockWithPin(context: Context, pin: String): Boolean {
        val app = context.applicationContext
        val stored = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PIN_WRAPPED, null) ?: return runCatching {
                loadDataKey(app)
                true
            }.getOrDefault(false)
        var legacyEnvelope = false
        val accepted = runCatching {
            val inner = gcmDecrypt(masterKey(), Base64.decode(stored, Base64.NO_WRAP))
            val versioned = inner.size > PIN_ENVELOPE_V2.size + PIN_SALT_LEN + IV_LEN + 16 &&
                PIN_ENVELOPE_V2.indices.all { inner[it] == PIN_ENVELOPE_V2[it] }
            legacyEnvelope = !versioned
            val saltOffset = if (versioned) PIN_ENVELOPE_V2.size else 0
            val cipherOffset = saltOffset + PIN_SALT_LEN
            require(inner.size > cipherOffset + IV_LEN + 16) { "PIN envelope is truncated." }
            val salt = inner.copyOfRange(saltOffset, cipherOffset)
            val derived = derivePinKey(
                pin,
                salt,
                if (versioned) PIN_KDF_ITERATIONS else LEGACY_PIN_KDF_ITERATIONS,
            )
            try {
                val unlocked = gcmDecrypt(
                    SecretKeySpec(derived, "AES"),
                    inner.copyOfRange(cipherOffset, inner.size),
                )
                require(unlocked.size == 32) { "Invalid data key." }
                synchronized(this) {
                    dataKey?.fill(0)
                    dataKey = unlocked
                }
            } finally {
                derived.fill(0)
                inner.fill(0)
            }
            true
        }.getOrDefault(false)
        if (accepted && legacyEnvelope) {
            // One valid legacy unlock migrates the envelope. If persistence
            // fails, retain the old envelope and retry on the next unlock.
            runCatching { protectWithPin(app, pin) }
        }
        return accepted
    }

    /** Remove PIN wrapping while retaining the Keystore protection. */
    fun removePinProtection(context: Context) {
        val app = context.applicationContext
        val current = synchronized(this) { dataKey?.copyOf() }
            ?: error("Unlock Sunny before removing the PIN.")
        try {
            val wrapped = gcmEncrypt(masterKey(), current)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            check(
                prefs.edit()
                    .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .remove(KEY_PIN_WRAPPED)
                    .commit(),
            ) { "Could not remove PIN encryption." }
        } finally {
            current.fill(0)
        }
    }

    /** Erase the process copy whenever the app returns to its locked state. */
    fun lock() {
        synchronized(this) {
            dataKey?.fill(0)
            dataKey = null
        }
    }

    private fun derivePinKey(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
