package com.sunny.skin.data.model

/**
 * The six-field structured description the model produces (USING_THE_MODEL.md §2).
 * Fixed order, fixed field names — the UI renders these verbatim and never
 * paraphrases (design.md §4).
 */
data class Analysis(
    val lesionType: String,
    val colour: String,
    val symmetry: String,
    val borders: String,
    val texture: String,
    val summary: String,
) {
    /** Ordered (label, value) rows for the Sunny Analysis card. */
    fun rows(): List<Pair<String, String>> = listOf(
        "Lesion Type" to lesionType,
        "Colour" to colour,
        "Symmetry" to symmetry,
        "Borders" to borders,
        "Texture" to texture,
        "Summary" to summary,
    )

    companion object {
        /** Field labels in the exact order the model emits them. */
        val FIELDS = listOf("Lesion Type", "Colour", "Symmetry", "Borders", "Texture", "Summary")
    }
}
