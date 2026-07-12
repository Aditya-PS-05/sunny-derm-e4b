package com.sunny.skin.inference

import com.sunny.skin.data.model.Analysis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardrailsTest {
    @Test
    fun acceptsObservableDescription() {
        assertTrue(Guardrails.isClean(cleanAnalysis()))
    }

    @Test
    fun rejectsDiseaseNameInAnyField() {
        assertFalse(Guardrails.isClean(cleanAnalysis().copy(summary = "Possible melanoma.")))
    }

    @Test
    fun rejectsVerdictLanguage() {
        assertFalse(Guardrails.isClean(cleanAnalysis().copy(lesionType = "benign spot")))
        assertFalse(Guardrails.isClean("This lesion is malignant"))
    }

    @Test
    fun reportsAllViolationsForDebugging() {
        val violations = Guardrails.violations("Possible basal cell carcinoma; consider biopsy")

        assertTrue("basal cell" in violations)
        assertTrue("carcinoma" in violations)
        assertTrue("biopsy" in violations)
    }

    private fun cleanAnalysis() = Analysis(
        lesionType = "pigmented macule",
        colour = "light brown",
        symmetry = "roughly symmetric",
        borders = "smooth, well-defined",
        texture = "smooth",
        summary = "A visual description only, not a diagnosis.",
    )
}
