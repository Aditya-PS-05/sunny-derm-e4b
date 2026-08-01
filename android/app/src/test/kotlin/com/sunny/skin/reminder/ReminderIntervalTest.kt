package com.sunny.skin.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIntervalTest {

    @Test
    fun convertsHoursToTheMostReadableUnit() {
        assertEquals(
            ReminderInterval(3, ReminderIntervalUnit.MONTHS),
            ReminderInterval.fromHours(24 * 90),
        )
        assertEquals(
            ReminderInterval(36, ReminderIntervalUnit.HOURS),
            ReminderInterval.fromHours(36),
        )
    }

    @Test
    fun changingUnitsPreservesTheApproximateDuration() {
        val twoDays = ReminderInterval(48, ReminderIntervalUnit.HOURS)
            .convertedTo(ReminderIntervalUnit.DAYS)

        assertEquals(2, twoDays.value)
        assertEquals(48, twoDays.totalHours)
    }

    @Test
    fun createsClearDisplayLabels() {
        assertEquals("Every hour", reminderIntervalLabel(1))
        assertEquals("Every day", reminderIntervalLabel(24))
        assertEquals("Every 36 hours", reminderIntervalLabel(36))
        assertEquals("Every 2 weeks", reminderIntervalLabel(24 * 14))
    }

    @Test
    fun editedLabelPreservesTheSelectedUnit() {
        assertEquals(
            "Every 720 hours",
            selectedReminderIntervalLabel(720, ReminderIntervalUnit.HOURS),
        )
        assertEquals(
            "Every month",
            selectedReminderIntervalLabel(1, ReminderIntervalUnit.MONTHS),
        )
    }
}
