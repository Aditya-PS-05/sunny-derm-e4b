package com.sunny.skin.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Room database is SQLCipher-encrypted at rest: touching it through the DAO
 * must succeed (correct passphrase), yet the raw file on disk must NOT begin with
 * the plaintext SQLite magic — proving the whole file, header included, is
 * ciphertext.
 */
@RunWith(AndroidJUnit4::class)
class DbEncryptionTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun databaseFile_isEncrypted_notPlainSqlite() = runBlocking {
        // A DAO read opens the encrypted DB and forces the file onto disk.
        SunnyDatabase.get(ctx).scanDao().allScansOnce()

        val dbFile = ctx.getDatabasePath("sunny.db")
        assertTrue("db file should exist and be non-empty", dbFile.exists() && dbFile.length() > 0)

        val header = ByteArray(16)
        dbFile.inputStream().use { it.read(header) }
        // A plaintext SQLite file begins with the ASCII "SQLite format 3" + NUL.
        val prefix = header.copyOf(15).toString(Charsets.US_ASCII)
        assertFalse(
            "an encrypted SQLCipher DB must not start with the plaintext SQLite magic",
            prefix == "SQLite format 3",
        )
    }
}
