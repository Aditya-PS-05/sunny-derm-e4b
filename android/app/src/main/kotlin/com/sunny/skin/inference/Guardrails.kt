package com.sunny.skin.inference

import com.sunny.skin.data.model.Analysis

/**
 * App-layer safety enforcement (S-03, and USING_THE_MODEL.md §6). The fine-tune
 * scored 100% on this in eval, but a health app must hard-enforce it and never
 * rely on the model alone: if any banned disease/verdict word appears in the
 * output, we suppress and re-run.
 */
object Guardrails {

    // Disease names and benign/malignant verdict language that must NEVER surface.
    private val BANNED = listOf(
        "cancer", "melanoma", "carcinoma", "basal cell", "squamous",
        "benign", "malignant", "biopsy", "tumour", "tumor",
        "precancerous", "pre-cancerous", "metasta", "lesion is dangerous",
        "keratosis", "nevus", "nevi", "dermatofibroma",
    )

    /** True if the text is clean (no banned terms). */
    fun isClean(text: String): Boolean {
        val lower = text.lowercase()
        return BANNED.none { lower.contains(it) }
    }

    fun isClean(analysis: Analysis): Boolean =
        analysis.rows().all { (_, value) -> isClean(value) }

    /** The banned terms present, for logging/debug (never shown to the user). */
    fun violations(text: String): List<String> {
        val lower = text.lowercase()
        return BANNED.filter { lower.contains(it) }
    }
}
