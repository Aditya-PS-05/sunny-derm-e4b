package com.sunny.skin.data.crypto

import android.content.Context
import java.io.File

/**
 * One-time clean slate when a device upgrades from a pre-encryption build to the
 * encrypted one. SQLCipher cannot open the old plaintext `sunny.db`, so rather
 * than migrate we wipe the unencrypted DB, photos and reports once and let the
 * encrypted store start fresh. Runs before any DB/repository access.
 */
object DataReset {
    private const val PREFS = "sunny_crypto"
    private const val KEY_INIT = "reset_done"

    fun runIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INIT, false)) return

        val legacyDb = context.getDatabasePath("sunny.db")
        if (legacyDb.exists()) {
            listOf("sunny.db", "sunny.db-wal", "sunny.db-shm").forEach {
                runCatching { context.getDatabasePath(it).delete() }
            }
            runCatching { File(context.filesDir, "scans").deleteRecursively() }
            runCatching { File(context.filesDir, "reports").deleteRecursively() }
            // The legacy plaintext PIN can't carry over to the hashed scheme.
            runCatching {
                context.getSharedPreferences("sunny_settings", Context.MODE_PRIVATE)
                    .edit().remove("app_lock_pin").apply()
            }
        }
        prefs.edit().putBoolean(KEY_INIT, true).apply()
    }
}
