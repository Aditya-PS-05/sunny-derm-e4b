package com.sunny.skin.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sunny.skin.MainActivity
import com.sunny.skin.R

/**
 * Fires when a reminder alarm goes off: posts the notification, then either
 * re-schedules (recurring) or drops it (one-shot).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val store = ReminderStore(context)
        val reminder = store.get(id) ?: return

        postNotification(context, reminder)

        if (reminder.recurring) {
            // Advance to the next occurrence and re-arm.
            val next = reminder.copy(
                triggerAt = reminder.triggerAt + reminder.intervalDays * DAY_MS,
            )
            store.upsert(next)
            ReminderScheduler.schedule(context, next)
        } else {
            store.remove(id)
        }
    }

    private fun postNotification(context: Context, reminder: Reminder) {
        ReminderScheduler.ensureChannel(context)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, reminder.id.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
