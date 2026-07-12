package com.sunny.skin.reminder

import android.app.PendingIntent
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
            // Advance to the NEXT future occurrence. A single +interval step can
            // still land in the past if delivery was deferred (Doze) by more than
            // one interval, and setAndAllowWhileIdle would then fire immediately
            // in a loop — so catch up like BootReceiver does.
            val step = reminder.intervalDays * DAY_MS
            val now = System.currentTimeMillis()
            var next = reminder.triggerAt + step
            while (next <= now) next += step
            val advanced = reminder.copy(triggerAt = next)
            store.upsert(advanced)
            ReminderScheduler.schedule(context, advanced)
        } else {
            store.remove(id)
        }
    }

    private fun postNotification(context: Context, reminder: Reminder) {
        ReminderScheduler.ensureChannel(context)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, reminder.id.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sunny reminder")
            .setContentText("Open Sunny to view your private skin-tracking reminder.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify call.
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
