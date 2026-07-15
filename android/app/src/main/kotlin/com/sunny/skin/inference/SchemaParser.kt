package com.sunny.skin.inference

import com.sunny.skin.data.model.Analysis

/**
 * Parses raw model text into the six fields (F-05). Returns null if ANY field
 * is missing — the caller must never show a partial result as final (F-06):
 * it re-runs once (greedy is deterministic, so a missing field means a
 * truncated generation) then shows a "couldn't read this image" state.
 */
object SchemaParser {

    private fun field(text: String, label: String): String? =
        Regex(
            "^${Regex.escape(label)}:[\\t ]*([^\\r\\n]+)[\\t ]*$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
            .find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_FIELD_CHARS }

    fun parse(text: String): Analysis? {
        val lesionType = field(text, "Lesion Type") ?: return null
        val colour = field(text, "Colour") ?: field(text, "Color") ?: return null
        val symmetry = field(text, "Symmetry") ?: return null
        val borders = field(text, "Borders") ?: return null
        val texture = field(text, "Texture") ?: return null
        val summary = field(text, "Summary") ?: return null
        return Analysis(lesionType, colour, symmetry, borders, texture, summary)
    }

    private const val MAX_FIELD_CHARS = 600
}
