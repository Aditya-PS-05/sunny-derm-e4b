package com.sunny.skin.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules reminder alarms via [AlarmManager]. Uses inexact alarms
 * (`set`, windowed) so no SCHEDULE_EXACT_ALARM permission is needed — a
 * few minutes' drift is fine for a skin-check reminder and it is battery
 * friendly. The [ReminderReceiver] posts the notification when it fires.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "sunny_reminders"
    const val EXTRA_ID = "reminder_id"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Skin check reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Reminders to re-check your skin and saved spots." }
            )
        }
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.sunny.skin.REMINDER"
            putExtra(EXTRA_ID, id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    /** (Re)schedule an alarm for the reminder's [Reminder.triggerAt]. */
    fun schedule(context: Context, reminder: Reminder) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, reminder.id)
        // Inexact but wakes the device; grouped by the system to save battery.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, id))
    }
}
