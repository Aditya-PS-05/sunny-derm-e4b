package com.sunny.skin.reminder

import android.content.Context
import com.sunny.skin.data.crypto.EncryptedPreferenceValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the small set of reminders in SharedPreferences (JSON). Kept separate
 * from the scans database so it never risks the user's scan history and needs no
 * schema migration. Exposes a reactive [reminders] flow for the UI.
 */
class ReminderStore(context: Context) {
    private val appCtx = context.applicationContext
    private val prefs = appCtx
        .getSharedPreferences("sunny_reminders", Context.MODE_PRIVATE)

    private val _reminders = MutableStateFlow(load())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private fun load(): List<Reminder> {
        val stored = prefs.getString(KEY, null) ?: return emptyList()
        val plain = EncryptedPreferenceValue.decode(appCtx, stored) ?: return emptyList()
        if (!EncryptedPreferenceValue.isEncrypted(stored)) {
            prefs.edit().putString(KEY, EncryptedPreferenceValue.encode(appCtx, plain)).apply()
        }
        return Reminder.listFromJson(plain).sortedBy { it.triggerAt }
    }

    private fun persist(list: List<Reminder>) {
        prefs.edit().putString(
            KEY,
            EncryptedPreferenceValue.encode(appCtx, Reminder.listToJson(list)),
        ).apply()
        _reminders.value = list.sortedBy { it.triggerAt }
    }

    fun all(): List<Reminder> = _reminders.value

    fun get(id: String): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** Insert or replace by id. */
    fun upsert(r: Reminder) {
        persist(_reminders.value.filterNot { it.id == r.id } + r)
    }

    fun remove(id: String) {
        persist(_reminders.value.filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().clear().apply()
        _reminders.value = emptyList()
    }

    private companion object {
        const val KEY = "reminders_json"
    }
}
