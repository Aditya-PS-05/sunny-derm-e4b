package com.sunny.skin.inference.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressEstimatorTest {
    @Test
    fun calculatesSpeedAndRemainingTimeFromMonotonicSamples() {
        val estimator = DownloadProgressEstimator(minimumSampleMillis = 500L, smoothing = 1.0)

        assertEquals(DownloadEstimate(), estimator.update(done = 0L, total = 10_000L, nowMillis = 1_000L))
        val estimate = estimator.update(done = 2_000L, total = 10_000L, nowMillis = 2_000L)

        assertEquals(2_000L, estimate.bytesPerSecond)
        assertEquals(4L, estimate.etaSeconds)
    }

    @Test
    fun waitsForAUsefulSampleAndResetsWhenProgressRestarts() {
        val estimator = DownloadProgressEstimator(minimumSampleMillis = 500L, smoothing = 1.0)
        estimator.update(1_000L, 10_000L, 1_000L)

        val early = estimator.update(1_200L, 10_000L, 1_200L)
        assertEquals(0L, early.bytesPerSecond)
        assertNull(early.etaSeconds)

        estimator.update(3_000L, 10_000L, 2_000L)
        val restarted = estimator.update(100L, 10_000L, 2_500L)
        assertEquals(0L, restarted.bytesPerSecond)
        assertNull(restarted.etaSeconds)
    }

    @Test
    fun smoothingKeepsTheEstimatePositiveAcrossChangingSamples() {
        val estimator = DownloadProgressEstimator(minimumSampleMillis = 500L, smoothing = 0.25)
        estimator.update(0L, 20_000L, 0L)
        estimator.update(4_000L, 20_000L, 1_000L)
        val estimate = estimator.update(6_000L, 20_000L, 2_000L)

        assertTrue(estimate.bytesPerSecond in 3_400L..3_600L)
        assertEquals(4L, estimate.etaSeconds)
    }
}
