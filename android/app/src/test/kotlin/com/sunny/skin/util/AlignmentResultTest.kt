package com.sunny.skin.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentResultTest {
    @Test
    fun framingReadyRequiresSimilarityAndTightTransform() {
        assertTrue(AlignmentResult(AlignTransform(tx = 0.02f, scale = 1.04f, rotationDeg = 2f), 0.7f).framingReady)
        assertFalse(AlignmentResult(AlignTransform(tx = 0.12f), 0.7f).framingReady)
        assertFalse(AlignmentResult(AlignTransform.Identity, 0.2f).framingReady)
    }
}
