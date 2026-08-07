package com.sunny.skin.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SchemaParserTest {
    @Test
    fun parsesCompleteSchema() {
        val parsed = SchemaParser.parse(VALID)

        assertNotNull(parsed)
        assertEquals("Flat spot", parsed!!.lesionType)
        assertEquals("Light brown", parsed.colour)
        assertEquals("Symmetric", parsed.symmetry)
        assertEquals("Smooth and well defined", parsed.borders)
        assertEquals("Smooth", parsed.texture)
        assertEquals(
            "This image shows a light brown flat spot. Its shape appears symmetric. " +
                "The border appears smooth and well defined. The surface appears smooth.",
            parsed.summary,
        )
    }

    @Test
    fun acceptsAmericanColorLabel() {
        val parsed = SchemaParser.parse(VALID.replace("Colour:", "Color:"))

        assertEquals("Light brown", parsed?.colour)
    }

    @Test
    fun rejectsAnyMissingField() {
        assertNull(SchemaParser.parse(VALID.replace("Texture: smooth\n", "")))
    }

    @Test
    fun rejectsEmptyField() {
        assertNull(SchemaParser.parse(VALID.replace("Borders: smooth, well-defined", "Borders:   ")))
    }

    @Test
    fun rejectsOversizedField() {
        assertNull(SchemaParser.parse(VALID.replace(
            "Summary: A visual description only, not a diagnosis.",
            "Summary: ${"x".repeat(601)}",
        )))
    }

    private companion object {
        const val VALID = """Lesion Type: pigmented macule
Colour: light brown
Symmetry: roughly symmetric
Borders: smooth, well-defined
Texture: smooth
Summary: A visual description only, not a diagnosis."""
    }
}
