package com.sunny.skin.data

import com.sunny.skin.data.model.Analysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContributionLabelerTest {
    private val model = Analysis(
        lesionType = "Flat spot",
        colour = "Light brown",
        symmetry = "Roughly symmetric",
        borders = "Defined",
        texture = "Smooth",
        summary = "A flat light-brown spot.",
    )

    private val raw = """
        Lesion Type: ${model.lesionType}
        Colour: ${model.colour}
        Symmetry: ${model.symmetry}
        Borders: ${model.borders}
        Texture: ${model.texture}
        Summary: ${model.summary}
    """.trimIndent()

    @Test
    fun unchangedOutput_isNotMarkedAsCorrection() {
        val labels = ContributionLabeler.from(raw, model)

        assertEquals(model, labels.modelOutput)
        assertNull(labels.correctedOutput)
    }

    @Test
    fun editedOutput_keepsRawModelOutputAsSourceLabel() {
        val corrected = model.copy(colour = "Medium brown")

        val labels = ContributionLabeler.from(raw, corrected)

        assertEquals(model, labels.modelOutput)
        assertEquals(corrected, labels.correctedOutput)
    }
}
