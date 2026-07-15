package com.sunny.skin.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderJsonTest {

    @Test
    fun legacyDayIntervalMigratesToHours() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("scanId", JSONObject.NULL)
            .put("title", "Reminder")
            .put("body", "Body")
            .put("triggerAt", 123L)
            .put("intervalDays", 7)

        assertEquals(24 * 7, Reminder.fromJson(legacy).intervalHours)
    }

    @Test
    fun customHourIntervalRoundTrips() {
        val original = Reminder(
            id = "custom",
            scanId = null,
            title = "Reminder",
            body = "Body",
            triggerAt = 123L,
            intervalDays = 1,
            intervalHours = 36,
        )

        assertEquals(36, Reminder.fromJson(original.toJson()).intervalHours)
    }
}
