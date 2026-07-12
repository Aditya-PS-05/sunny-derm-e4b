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
        assertEquals("pigmented macule", parsed!!.lesionType)
        assertEquals("light brown", parsed.colour)
        assertEquals("roughly symmetric", parsed.symmetry)
        assertEquals("smooth, well-defined", parsed.borders)
        assertEquals("smooth", parsed.texture)
        assertEquals("A visual description only, not a diagnosis.", parsed.summary)
    }

    @Test
    fun acceptsAmericanColorLabel() {
        val parsed = SchemaParser.parse(VALID.replace("Colour:", "Color:"))

        assertEquals("light brown", parsed?.colour)
    }

    @Test
    fun rejectsAnyMissingField() {
        assertNull(SchemaParser.parse(VALID.replace("Texture: smooth\n", "")))
    }

    @Test
    fun rejectsEmptyField() {
        assertNull(SchemaParser.parse(VALID.replace("Borders: smooth, well-defined", "Borders:   ")))
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
