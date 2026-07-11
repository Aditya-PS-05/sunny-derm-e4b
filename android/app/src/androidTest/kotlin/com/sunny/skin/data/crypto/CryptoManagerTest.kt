package com.sunny.skin.data.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Data-at-rest crypto core: AES-256-GCM via a Keystore-wrapped data key. Verifies
 * the round-trip, that ciphertext never equals plaintext, that a random IV makes
 * repeated encryptions of the same input differ (no ECB-style leakage), and that
 * tampering fails authentication.
 */
@RunWith(AndroidJUnit4::class)
class CryptoManagerTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun encryptDecrypt_roundTrips() {
        val plain = "sensitive skin note — dark brown papule 🔬".toByteArray()
        val enc = CryptoManager.encrypt(ctx, plain)
        assertFalse("ciphertext must differ from plaintext", enc.contentEquals(plain))
        assertArrayEquals(plain, CryptoManager.decrypt(ctx, enc))
    }

    @Test
    fun repeatedEncryption_usesFreshIv() {
        val plain = ByteArray(64) { it.toByte() }
        val a = CryptoManager.encrypt(ctx, plain)
        val b = CryptoManager.encrypt(ctx, plain)
        assertFalse("same input must not produce identical ciphertext", a.contentEquals(b))
        assertArrayEquals(plain, CryptoManager.decrypt(ctx, a))
        assertArrayEquals(plain, CryptoManager.decrypt(ctx, b))
    }

    @Test(expected = Exception::class)
    fun tamperedCiphertext_failsAuthentication() {
        val enc = CryptoManager.encrypt(ctx, "hello".toByteArray()).copyOf()
        enc[enc.size - 1] = (enc[enc.size - 1] + 1).toByte() // corrupt the GCM tag
        CryptoManager.decrypt(ctx, enc) // must throw (AEAD tag mismatch)
    }

    @Test
    fun dbPassphrase_isStable32ByteFreshCopy() {
        val p1 = CryptoManager.dbPassphrase(ctx)
        val p2 = CryptoManager.dbPassphrase(ctx)
        assertEquals("data key is 256-bit", 32, p1.size)
        assertArrayEquals("passphrase must be stable across calls", p1, p2)
        assertNotSame("SQLCipher zeroes the array, so each call returns a fresh copy", p1, p2)
    }
}
