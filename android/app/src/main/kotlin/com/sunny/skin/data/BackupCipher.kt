package com.sunny.skin.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned AES-GCM envelope used by Sunny backup archives. */
object BackupCipher {
    private val MAGIC_V1 = "SUNNYB01".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "SUNNYB02".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val V1_ITERATIONS = 210_000
    private const val V2_ITERATIONS = 600_000

    fun write(file: File, password: CharArray, block: (CipherOutputStream) -> Unit) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt, V2_ITERATIONS)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            FileOutputStream(file).use { raw ->
                raw.write(MAGIC_V2)
                raw.write(salt)
                raw.write(iv)
                CipherOutputStream(BufferedOutputStream(raw), cipher).use(block)
            }
        } finally {
            key.fill(0)
        }
    }

    fun <T> read(file: File, password: CharArray, block: (InputStream) -> T): T {
        FileInputStream(file).use { raw ->
            val magic = raw.readExact(MAGIC_V2.size)
            val iterations = when {
                magic.contentEquals(MAGIC_V2) -> V2_ITERATIONS
                magic.contentEquals(MAGIC_V1) -> V1_ITERATIONS
                else -> throw IllegalArgumentException("Not a Sunny backup.")
            }
            val salt = raw.readExact(SALT_BYTES)
            val iv = raw.readExact(IV_BYTES)
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Backup is truncated." }
            val key = derive(password, salt, iterations)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                }
                return CipherInputStream(BufferedInputStream(raw), cipher).use(block)
            } finally {
                key.fill(0)
            }
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun InputStream.readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count < 0) return bytes.copyOf(offset)
            offset += count
        }
        return bytes
    }
}
