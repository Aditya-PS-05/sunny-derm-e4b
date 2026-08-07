package com.sunny.skin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisVocabularyTest {
    @Test
    fun normalizesFreeFormFieldsAndResolvesTextureContradiction() {
        val result = AnalysisVocabulary.normalize(
            Analysis(
                lesionType = "rough scaly patch",
                colour = "multiple colours (skin-toned, tan)",
                symmetry = "roughly even surface",
                borders = "somewhat ragged borders",
                texture = "smooth, even surface",
                summary = "Contradictory generated sentence.",
            ),
        )

        assertEquals("Patch", result.lesionType)
        assertEquals("Skin-coloured and Tan", result.colour)
        assertEquals("Symmetric", result.symmetry)
        assertEquals("Irregular", result.borders)
        assertEquals("Texture unclear", result.texture)
        assertEquals(
            "This image shows a skin-coloured and tan patch. Its shape appears symmetric. " +
                "The border appears irregular. The surface texture is unclear.",
            result.summary,
        )
    }

    @Test
    fun controlledOutputIsIdempotent() {
        val first = unknownAnalysis().normalized()

        assertEquals(first, first.normalized())
        assertEquals("Visible skin mark", first.lesionType)
        assertEquals("Colour unclear", first.colour)
        assertEquals("Symmetry unclear", first.symmetry)
        assertEquals("Border detail unclear", first.borders)
        assertEquals("Texture unclear", first.texture)
        assertFalse("normal" in first.summary.lowercase())
    }

    @Test
    fun contradictoryShapeAndBorderTermsFailToUnclear() {
        val result = unknownAnalysis().copy(
            symmetry = "symmetric in one view but asymmetric in another",
            borders = "smooth in places and ragged elsewhere",
        ).normalized()

        assertEquals("Symmetry unclear", result.symmetry)
        assertEquals("Border detail unclear", result.borders)
    }

    @Test
    fun comparisonShowsControlledBeforeAndAfterValuesOnly() {
        val previous = unknownAnalysis().copy(colour = "light brown", borders = "smooth")
        val current = unknownAnalysis().copy(colour = "dark brown", borders = "ragged")

        val changes = AnalysisComparison.changes(previous, current)

        assertEquals(listOf("Colour", "Borders"), changes.map { it.label })
        assertEquals("Light brown", changes.first().previous)
        assertEquals("Dark brown", changes.first().current)
        assertTrue(changes.none { it.label == "Summary" })
    }

    private fun unknownAnalysis() = Analysis(
        lesionType = "unclassified",
        colour = "not clear",
        symmetry = "normal",
        borders = "plain",
        texture = "not visible",
        summary = "Generated text is ignored.",
    )
}
