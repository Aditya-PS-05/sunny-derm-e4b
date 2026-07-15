package com.sunny.skin.data

import android.content.Context
import com.sunny.skin.data.crypto.EncryptedPreferenceValue
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class CheckSessionStatus { PENDING, COMPLETED, SKIPPED }

data class CheckSessionItem(
    val scanId: String,
    val status: CheckSessionStatus = CheckSessionStatus.PENDING,
)

data class CheckSession(
    val id: String,
    val startedAt: Long,
    val items: List<CheckSessionItem>,
) {
    val resolvedCount: Int get() = items.count { it.status != CheckSessionStatus.PENDING }
    val nextPending: CheckSessionItem? get() = items.firstOrNull { it.status == CheckSessionStatus.PENDING }
}

/** One encrypted, resumable photo-check checklist. Completed sessions are not retained. */
class CheckSessionStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sunny_check_session", Context.MODE_PRIVATE)
    private val _active = MutableStateFlow(load())
    val active: StateFlow<CheckSession?> = _active.asStateFlow()

    fun start(scanIds: List<String>): CheckSession? {
        val unique = scanIds.distinct().filter(String::isNotBlank)
        if (unique.isEmpty()) return null
        return CheckSession(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis(),
            items = unique.map(::CheckSessionItem),
        ).also(::persist)
    }

    fun setStatus(scanId: String, status: CheckSessionStatus) {
        val session = _active.value ?: return
        if (session.items.none { it.scanId == scanId }) return
        persist(session.copy(items = session.items.map {
            if (it.scanId == scanId) it.copy(status = status) else it
        }))
    }

    fun removeScan(scanId: String) {
        val session = _active.value ?: return
        val remaining = session.items.filterNot { it.scanId == scanId }
        if (remaining.isEmpty()) clear() else persist(session.copy(items = remaining))
    }

    fun clear() {
        prefs.edit().clear().apply()
        _active.value = null
    }

    private fun persist(session: CheckSession) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("startedAt", session.startedAt)
            put("items", JSONArray().apply {
                session.items.forEach { item ->
                    put(JSONObject().apply {
                        put("scanId", item.scanId)
                        put("status", item.status.name)
                    })
                }
            })
        }.toString()
        prefs.edit().putString(KEY, EncryptedPreferenceValue.encode(app, json)).apply()
        _active.value = session
    }

    private fun load(): CheckSession? = runCatching {
        val stored = prefs.getString(KEY, null) ?: return null
        val plain = EncryptedPreferenceValue.decode(app, stored) ?: return null
        val json = JSONObject(plain)
        val itemsJson = json.getJSONArray("items")
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val item = itemsJson.getJSONObject(index)
                add(CheckSessionItem(
                    scanId = item.getString("scanId"),
                    status = CheckSessionStatus.valueOf(item.getString("status")),
                ))
            }
        }
        CheckSession(
            id = json.getString("id"),
            startedAt = json.getLong("startedAt"),
            items = items,
        ).takeIf { it.items.isNotEmpty() }
    }.getOrNull()

    private companion object { const val KEY = "active_session" }
}
