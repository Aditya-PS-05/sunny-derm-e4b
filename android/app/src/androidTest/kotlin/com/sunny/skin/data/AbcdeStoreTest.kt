package com.sunny.skin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
}
