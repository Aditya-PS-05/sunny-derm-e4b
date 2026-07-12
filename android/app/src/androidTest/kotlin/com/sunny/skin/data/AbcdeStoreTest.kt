package com.sunny.skin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Per-scan ABCDE answers persist, read back, and clear. */
@RunWith(AndroidJUnit4::class)
class AbcdeStoreTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val store = AbcdeStore(ctx)

    @Test
    fun setGetClear_roundTrips() {
        val id = "test-scan-${System.nanoTime()}"
        try {
            assertEquals(AbcdeAnswer.UNSET, store.get(id)[AbcdeItem.ASYMMETRY])

            store.set(id, AbcdeItem.ASYMMETRY, AbcdeAnswer.YES)
            store.set(id, AbcdeItem.COLOUR, AbcdeAnswer.NO)

            val m = store.get(id)
            assertEquals(AbcdeAnswer.YES, m[AbcdeItem.ASYMMETRY])
            assertEquals(AbcdeAnswer.NO, m[AbcdeItem.COLOUR])
            assertEquals(AbcdeAnswer.UNSET, m[AbcdeItem.BORDER])
        } finally {
            store.clear(id)
            assertEquals(AbcdeAnswer.UNSET, store.get(id)[AbcdeItem.ASYMMETRY])
        }
    }

    @Test
    fun answers_areEncryptedAtRest_andLegacyPlaintextMigrates() {
        val prefs = ctx.getSharedPreferences("sunny_abcde", Context.MODE_PRIVATE)
        val encryptedId = "encrypted-${System.nanoTime()}"
        val legacyId = "legacy-${System.nanoTime()}"
        try {
            store.set(encryptedId, AbcdeItem.BORDER, AbcdeAnswer.YES)
            val stored = prefs.getString(encryptedId, "").orEmpty()
            assertTrue(stored.startsWith("sunny:v1:"))
            assertFalse(stored.contains("YES"))

            prefs.edit().putString(legacyId, "{\"COLOUR\":\"NO\"}").commit()
            assertEquals(AbcdeAnswer.NO, store.get(legacyId)[AbcdeItem.COLOUR])
            assertTrue(prefs.getString(legacyId, "").orEmpty().startsWith("sunny:v1:"))
        } finally {
            store.clear(encryptedId)
            store.clear(legacyId)
        }
    }
}
