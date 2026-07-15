package com.sunny.skin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sessionPersistsProgressAndCanBeCleared() {
        CheckSessionStore(context).clear()
        val store = CheckSessionStore(context)
        val started = store.start(listOf("scan-a", "scan-b"))!!

        store.setStatus("scan-a", CheckSessionStatus.COMPLETED)
        store.setStatus("scan-b", CheckSessionStatus.SKIPPED)

        val restored = CheckSessionStore(context).active.value!!
        assertEquals(started.id, restored.id)
        assertEquals(2, restored.resolvedCount)
        assertEquals(CheckSessionStatus.COMPLETED, restored.items[0].status)
        assertEquals(CheckSessionStatus.SKIPPED, restored.items[1].status)

        store.clear()
        assertNull(CheckSessionStore(context).active.value)
    }
}
