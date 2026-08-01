package com.sunny.skin.reminder

import kotlin.math.roundToInt

enum class ReminderIntervalUnit(
    val label: String,
    val hoursPerUnit: Int,
    val maxValue: Int,
) {
    HOURS("Hours", 1, 8_760),
    DAYS("Days", 24, 365),
    WEEKS("Weeks", 24 * 7, 52),
    MONTHS("Months", 24 * 30, 12),
}

data class ReminderInterval(
    val value: Int,
    val unit: ReminderIntervalUnit,
) {
    val totalHours: Int
        get() = value.coerceIn(1, unit.maxValue) * unit.hoursPerUnit

    fun convertedTo(newUnit: ReminderIntervalUnit): ReminderInterval = ReminderInterval(
        value = (totalHours.toFloat() / newUnit.hoursPerUnit).roundToInt()
            .coerceIn(1, newUnit.maxValue),
        unit = newUnit,
    )

    companion object {
        fun fromHours(hours: Int): ReminderInterval {
            val safe = hours.coerceAtLeast(1)
            val unit = when {
                safe % ReminderIntervalUnit.MONTHS.hoursPerUnit == 0 -> ReminderIntervalUnit.MONTHS
                safe % ReminderIntervalUnit.WEEKS.hoursPerUnit == 0 -> ReminderIntervalUnit.WEEKS
                safe % ReminderIntervalUnit.DAYS.hoursPerUnit == 0 -> ReminderIntervalUnit.DAYS
                else -> ReminderIntervalUnit.HOURS
            }
            return ReminderInterval(
                value = (safe / unit.hoursPerUnit).coerceIn(1, unit.maxValue),
                unit = unit,
            )
        }
    }
}

fun reminderIntervalLabel(hours: Int): String = when (hours) {
    1 -> "Every hour"
    24 -> "Every day"
    24 * 7 -> "Every week"
    24 * 30 -> "Every month"
    24 * 90 -> "Every 3 months"
    else -> {
        val interval = ReminderInterval.fromHours(hours)
        val singular = interval.value == 1
        val unit = when (interval.unit) {
            ReminderIntervalUnit.HOURS -> if (singular) "hour" else "hours"
            ReminderIntervalUnit.DAYS -> if (singular) "day" else "days"
            ReminderIntervalUnit.WEEKS -> if (singular) "week" else "weeks"
            ReminderIntervalUnit.MONTHS -> if (singular) "month" else "months"
        }
        "Every ${interval.value} $unit"
    }
}

/** A label for the value currently being edited, without silently changing its unit. */
fun selectedReminderIntervalLabel(value: Int, unit: ReminderIntervalUnit): String {
    val unitLabel = when (unit) {
        ReminderIntervalUnit.HOURS -> if (value == 1) "hour" else "hours"
        ReminderIntervalUnit.DAYS -> if (value == 1) "day" else "days"
        ReminderIntervalUnit.WEEKS -> if (value == 1) "week" else "weeks"
        ReminderIntervalUnit.MONTHS -> if (value == 1) "month" else "months"
    }
    return if (value == 1) "Every $unitLabel" else "Every $value $unitLabel"
}
