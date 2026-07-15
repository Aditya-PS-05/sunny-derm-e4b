package com.sunny.skin.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms don't survive a reboot, so re-arm every pending reminder on boot.
 * Past-due recurring reminders are advanced to their next future occurrence;
 * past-due one-shots are dropped (their moment has passed).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val store = ReminderStore(context)
        val now = System.currentTimeMillis()
        store.all().forEach { r ->
            when {
                r.triggerAt > now -> ReminderScheduler.schedule(context, r)
                r.recurring -> {
                    val step = r.intervalMillis
                    var t = r.triggerAt
                    while (t <= now) t += step
                    val next = r.copy(triggerAt = t)
                    store.upsert(next)
                    ReminderScheduler.schedule(context, next)
                }
                else -> store.remove(r.id) // stale one-shot
            }
        }
    }
}
