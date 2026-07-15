package com.sunny.skin.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoQualityTest {
    @Test
    fun rejectsVeryDarkSamples() {
        assertEquals(PhotoQualityIssue.TOO_DARK, PhotoQuality.assessLuma(List(64) { 10 }))
    }

    @Test
    fun rejectsOverexposedSamples() {
        assertEquals(PhotoQualityIssue.TOO_BRIGHT, PhotoQuality.assessLuma(List(64) { 250 }))
    }

    @Test
    fun rejectsLowContrastSamples() {
        assertEquals(PhotoQualityIssue.LOW_CONTRAST, PhotoQuality.assessLuma(List(64) { 120 }))
    }

    @Test
    fun acceptsWellExposedDetailedSamples() {
        val samples = List(64) { index -> if (index % 2 == 0) 70 else 180 }
        assertNull(PhotoQuality.assessLuma(samples))
    }

    @Test
    fun rejectsMissingPreviewSamples() {
        assertEquals(PhotoQualityIssue.LOW_CONTRAST, PhotoQuality.assessLuma(emptyList()))
    }
}
