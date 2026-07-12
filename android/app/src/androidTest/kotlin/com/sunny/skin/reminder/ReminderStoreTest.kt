package com.sunny.skin.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reminders_areEncryptedAtRest_andRoundTrip() {
        val id = "private-${System.nanoTime()}"
        val store = ReminderStore(ctx)
        val reminder = Reminder(id, "scan-secret", "Private title", "Private body", 12345L, 0)
        try {
            store.upsert(reminder)
            assertEquals(reminder, ReminderStore(ctx).get(id))

            val raw = ctx.getSharedPreferences("sunny_reminders", Context.MODE_PRIVATE)
                .getString("reminders_json", "").orEmpty()
            assertTrue(raw.startsWith("sunny:v1:"))
            assertFalse(raw.contains("scan-secret"))
            assertFalse(raw.contains("Private body"))
        } finally {
            store.remove(id)
        }
    }
}
