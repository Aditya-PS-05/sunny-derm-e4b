package com.sunny.skin.data

import android.content.Context

/**
 * Small preference store for app settings (the app-lock PIN). No skin data here.
 * The PIN gates local access to health data; the data itself never leaves the
 * device regardless.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sunny_settings", Context.MODE_PRIVATE)

    /** The user's app-lock PIN, or null when no lock is set. */
    var pin: String?
        get() = prefs.getString(KEY_PIN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_PIN) else putString(KEY_PIN, value)
        }.apply()

    /** True when an app-lock PIN has been created. */
    val pinLockEnabled: Boolean get() = !pin.isNullOrEmpty()

    fun clearPin() { pin = null }

    /** True once the user has completed the first-run onboarding. */
    var seenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** True once the one-time balloon greeting has played on Overview. */
    var seenGreeting: Boolean
        get() = prefs.getBoolean(KEY_GREETED, false)
        set(v) = prefs.edit().putBoolean(KEY_GREETED, v).apply()

    private companion object {
        const val KEY_PIN = "app_lock_pin"
        const val KEY_ONBOARDED = "seen_onboarding"
        const val KEY_GREETED = "seen_greeting"
    }
}
