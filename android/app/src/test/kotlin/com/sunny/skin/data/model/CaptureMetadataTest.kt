package com.sunny.skin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMetadataTest {
    @Test
    fun measurementUsesReferenceToTargetRatio() {
        val measurement = ApproximateMeasurement(
            referenceSizeMm = 25f,
            referenceSpan = 0.25f,
            targetSpan = 0.08f,
        )

        assertTrue(measurement.isValid)
        assertEquals(8f, measurement.approximateSizeMm, 0.001f)
        assertEquals("8 mm", measurement.formattedSize())
    }

    @Test
    fun implausibleOrTinyReferencesAreRejected() {
        assertFalse(ApproximateMeasurement(0f, 0.2f, 0.1f).isValid)
        assertFalse(ApproximateMeasurement(20f, 0.01f, 0.1f).isValid)
    }
}
