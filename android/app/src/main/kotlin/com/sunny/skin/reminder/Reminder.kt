package com.sunny.skin.reminder

import org.json.JSONArray
import org.json.JSONObject

/**
 * One scheduled reminder. [intervalDays] == 0 is a one-shot (per-scan re-check);
 * > 0 is a recurring reminder (e.g. the regular skin check) that re-schedules
 * itself each time it fires. [scanId] is set for per-scan reminders, null for
 * the global recurring one.
 */
data class Reminder(
    val id: String,
    val scanId: String?,
    val title: String,
    val body: String,
    val triggerAt: Long,
    val intervalDays: Int,
) {
    val recurring: Boolean get() = intervalDays > 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("scanId", scanId ?: JSONObject.NULL)
        put("title", title)
        put("body", body)
        put("triggerAt", triggerAt)
        put("intervalDays", intervalDays)
    }

    companion object {
        const val RECURRING_ID = "recurring_skin_check"

        fun fromJson(o: JSONObject) = Reminder(
            id = o.getString("id"),
            scanId = if (o.isNull("scanId")) null else o.getString("scanId"),
            title = o.getString("title"),
            body = o.getString("body"),
            triggerAt = o.getLong("triggerAt"),
            intervalDays = o.getInt("intervalDays"),
        )

        fun listFromJson(s: String): List<Reminder> =
            runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())

        fun listToJson(list: List<Reminder>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}
