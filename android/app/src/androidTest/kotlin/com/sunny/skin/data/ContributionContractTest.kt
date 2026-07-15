package com.sunny.skin.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.data.model.Analysis
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContributionContractTest {
    private val model = Analysis("Spot", "Brown", "Symmetric", "Defined", "Smooth", "Summary")

    @Test
    fun unchangedContribution_omitsCorrection() {
        val payload = JSONObject(
            ContributionContract.payload("abc", model, null, "TORSO", "Pixel", "1.0")
                .toString(Charsets.UTF_8),
        )

        assertEquals("abc", payload.getString("image_b64"))
        assertEquals("Brown", payload.getJSONObject("model_output").getString("colour"))
        assertEquals("TORSO", payload.getString("body_zone"))
        assertFalse(payload.has("corrected_output"))
        assertFalse(payload.has("corrected"))
    }

    @Test
    fun correctedContribution_includesBothLabels() {
        val corrected = model.copy(colour = "Dark brown")
        val payload = JSONObject(
            ContributionContract.payload("abc", model, corrected, "TORSO", "Pixel", "1.0")
                .toString(Charsets.UTF_8),
        )

        assertEquals("Brown", payload.getJSONObject("model_output").getString("colour"))
        assertEquals("Dark brown", payload.getJSONObject("corrected_output").getString("colour"))
        assertTrue(payload.getBoolean("corrected"))
    }
}
