package com.sunny.skin.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNavigationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun spotReminderTargetsItsScan() {
        val intent = reminderTapIntent(
            context,
            Reminder("r1", "scan-42", "title", "body", 1L, 0),
        )

        assertEquals("scan-42", intent.getStringExtra(MainActivity.EXTRA_SCAN_ID))
        assertFalse(intent.getBooleanExtra(MainActivity.EXTRA_OPEN_REMINDERS, false))
    }

    @Test
    fun recurringReminderTargetsReminderCenter() {
        val intent = reminderTapIntent(
            context,
            Reminder(Reminder.RECURRING_ID, null, "title", "body", 1L, 7),
        )

        assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_OPEN_REMINDERS, false))
    }
}
