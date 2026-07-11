package com.sunny.skin.data

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Small preference store for app settings. The app-lock PIN is stored ONLY as a
 * salted PBKDF2 hash (never plaintext), and repeated wrong attempts are rate-
 * limited with an escalating lockout. No skin data lives here; the data itself
 * is separately encrypted at rest.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sunny_settings", Context.MODE_PRIVATE)

    // ---- App-lock PIN (salted hash + throttling) ----

    /** True when an app-lock PIN has been created. */
    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    /** Create/replace the PIN: store a fresh random salt + PBKDF2 hash. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKOUT, 0L)
            .apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).remove(KEY_PIN)
            .putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT, 0L)
            .apply()
    }

    /**
     * Verify [pin]. Returns true on match (and resets the failure counter). A
     * wrong PIN records a failure and may start a lockout; verification during a
     * lockout returns false without checking.
     */
    fun verifyPin(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val stored = prefs.getString(KEY_PIN_HASH, null)
        val ok = salt != null && stored != null && hash(pin, salt) == stored
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT, 0L).apply()
        } else {
            noteFailure()
        }
        return ok
    }

    /** Milliseconds remaining on the current lockout, or 0 if unlocked. */
    fun lockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCKOUT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun noteFailure() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        // 1–4: no delay; 5+: escalating lockout (30s, 60s, 120s, … capped 15m).
        val lockMs = if (fails < 5) 0L else
            (30_000L shl (fails - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
        prefs.edit()
            .putInt(KEY_FAILS, fails)
            .putLong(KEY_LOCKOUT, if (lockMs > 0) System.currentTimeMillis() + lockMs else 0L)
            .apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ---- First-run flags ----

    var seenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    var seenGreeting: Boolean
        get() = prefs.getBoolean(KEY_GREETED, false)
        set(v) = prefs.edit().putBoolean(KEY_GREETED, v).apply()

    private companion object {
        const val KEY_PIN = "app_lock_pin"       // legacy plaintext key (cleared on migration)
        const val KEY_PIN_HASH = "app_lock_pin_hash"
        const val KEY_PIN_SALT = "app_lock_pin_salt"
        const val KEY_FAILS = "pin_failed_attempts"
        const val KEY_LOCKOUT = "pin_lockout_until"
        const val KEY_ONBOARDED = "seen_onboarding"
        const val KEY_GREETED = "seen_greeting"
    }
}
