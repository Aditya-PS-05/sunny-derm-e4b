package com.sunny.skin.reminder

import org.json.JSONArray
import org.json.JSONObject

/**
 * One scheduled reminder. [intervalHours] == 0 is a one-shot (per-scan re-check);
 * > 0 is recurring. [intervalDays] remains in the stored contract so reminders
 * created by older app versions migrate without losing their schedule.
 */
data class Reminder(
    val id: String,
    val scanId: String?,
    val title: String,
    val body: String,
    val triggerAt: Long,
    val intervalDays: Int,
    val intervalHours: Int = intervalDays * 24,
) {
    val recurring: Boolean get() = intervalHours > 0
    val intervalMillis: Long get() = intervalHours.toLong() * HOUR_MS

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("scanId", scanId ?: JSONObject.NULL)
        put("title", title)
        put("body", body)
        put("triggerAt", triggerAt)
        put("intervalDays", intervalDays)
        put("intervalHours", intervalHours)
    }

    companion object {
        const val RECURRING_ID = "recurring_skin_check"

        private const val HOUR_MS = 60L * 60 * 1000

        fun fromJson(o: JSONObject): Reminder {
            val legacyDays = o.optInt("intervalDays", 0)
            return Reminder(
                id = o.getString("id"),
                scanId = if (o.isNull("scanId")) null else o.getString("scanId"),
                title = o.getString("title"),
                body = o.getString("body"),
                triggerAt = o.getLong("triggerAt"),
                intervalDays = legacyDays,
                intervalHours = if (o.has("intervalHours")) {
                    o.optInt("intervalHours", legacyDays * 24)
                } else {
                    legacyDays * 24
                },
            )
        }

        fun listFromJson(s: String): List<Reminder> =
            runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())

        fun listToJson(list: List<Reminder>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}
