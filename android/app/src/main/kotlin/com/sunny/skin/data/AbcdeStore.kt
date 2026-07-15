package com.sunny.skin.data

import android.content.Context
import com.sunny.skin.data.crypto.EncryptedPreferenceValue
import org.json.JSONObject

/**
 * The five things a person can be taught to look for on a mole — the classic
 * dermatology "ABCDE" mnemonic. Sunny presents this as an EDUCATIONAL self-check
 * so users learn what to notice and what to raise with a clinician. It is never a
 * diagnosis and the app draws no conclusion from the answers (design.md §8).
 */
enum class AbcdeItem(
    val letter: String,
    val title: String,
    val question: String,
    val info: String,
) {
    ASYMMETRY(
        "A", "Asymmetry",
        "Does one half look different from the other half?",
        "Common moles are usually symmetric. If you imagine a line through the middle, " +
            "the two halves of a harmless mole tend to match.",
    ),
    BORDER(
        "B", "Border",
        "Are the edges irregular, ragged, notched, or blurred?",
        "Ordinary moles tend to have smooth, even edges. Uneven or poorly-defined " +
            "borders are something dermatologists pay attention to.",
    ),
    COLOUR(
        "C", "Colour",
        "Is there more than one colour, or uneven shades?",
        "A single, even colour is typical. Several colours in one spot — brown, black, " +
            "red, white, or blue — are worth mentioning to a clinician.",
    ),
    DIAMETER(
        "D", "Diameter",
        "Is it larger than about 6 mm (a pencil eraser)?",
        "Larger spots deserve a closer look, though size alone doesn't determine " +
            "anything — small spots can matter and large ones can be harmless.",
    ),
    EVOLVING(
        "E", "Evolving",
        "Has it changed in size, shape, colour, or how it feels?",
        "Change over time is the single most important thing to notice — which is " +
            "exactly what Sunny's photo tracking helps you see.",
    );
}

/** A user's answer to one ABCDE prompt. Sunny stores it; it never interprets it. */
enum class AbcdeAnswer { UNSET, NO, YES, UNSURE }

/**
 * Per-scan ABCDE answers, persisted in SharedPreferences (JSON keyed by scanId).
 * Kept out of the scans database so it needs no schema migration and never risks
 * the photo history — same rationale as [com.sunny.skin.reminder.ReminderStore].
 */
class AbcdeStore(context: Context) {
    private val appCtx = context.applicationContext
    private val prefs = appCtx
        .getSharedPreferences("sunny_abcde", Context.MODE_PRIVATE)

    private fun read(scanId: String): String {
        val stored = prefs.getString(scanId, null) ?: return "{}"
        val plain = EncryptedPreferenceValue.decode(appCtx, stored) ?: return "{}"
        if (!EncryptedPreferenceValue.isEncrypted(stored)) {
            prefs.edit().putString(scanId, EncryptedPreferenceValue.encode(appCtx, plain)).apply()
        }
        return plain
    }

    /** All answers for [scanId]; items with no recorded answer are [AbcdeAnswer.UNSET]. */
    fun get(scanId: String): Map<AbcdeItem, AbcdeAnswer> {
        val obj = runCatching { JSONObject(read(scanId)) }
            .getOrDefault(JSONObject())
        return AbcdeItem.entries.associateWith { item ->
            runCatching { AbcdeAnswer.valueOf(obj.optString(item.name, "UNSET")) }
                .getOrDefault(AbcdeAnswer.UNSET)
        }
    }

    /** Record [answer] for [item] on [scanId]. */
    fun set(scanId: String, item: AbcdeItem, answer: AbcdeAnswer) {
        val obj = runCatching { JSONObject(read(scanId)) }
            .getOrDefault(JSONObject())
        obj.put(item.name, answer.name)
        prefs.edit().putString(
            scanId,
            EncryptedPreferenceValue.encode(appCtx, obj.toString()),
        ).apply()
    }

    /** Drop all answers for a scan (called when the scan is deleted). */
    fun clear(scanId: String) {
        prefs.edit().remove(scanId).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
