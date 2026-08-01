package com.sunny.skin.inference.download

import kotlin.math.roundToLong

data class DownloadEstimate(
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null,
)

/** Smooths noisy network callbacks into a stable speed and remaining-time estimate. */
class DownloadProgressEstimator(
    private val minimumSampleMillis: Long = 500L,
    private val smoothing: Double = 0.25,
) {
    private var previousBytes: Long? = null
    private var previousMillis: Long = 0L
    private var smoothedBytesPerSecond: Double = 0.0

    fun reset() {
        previousBytes = null
        previousMillis = 0L
        smoothedBytesPerSecond = 0.0
    }

    fun update(done: Long, total: Long, nowMillis: Long): DownloadEstimate {
        val safeDone = done.coerceAtLeast(0L)
        val previous = previousBytes
        if (previous == null || safeDone < previous || nowMillis < previousMillis) {
            previousBytes = safeDone
            previousMillis = nowMillis
            smoothedBytesPerSecond = 0.0
            return DownloadEstimate()
        }

        val elapsed = nowMillis - previousMillis
        val transferred = safeDone - previous
        if (elapsed >= minimumSampleMillis && transferred > 0L) {
            val sample = transferred * 1_000.0 / elapsed
            smoothedBytesPerSecond = if (smoothedBytesPerSecond == 0.0) {
                sample
            } else {
                smoothing * sample + (1.0 - smoothing) * smoothedBytesPerSecond
            }
            previousBytes = safeDone
            previousMillis = nowMillis
        }

        val speed = smoothedBytesPerSecond.roundToLong().coerceAtLeast(0L)
        val remaining = (total - safeDone).coerceAtLeast(0L)
        val eta = if (speed > 0L && remaining > 0L) {
            ((remaining + speed - 1L) / speed).coerceAtLeast(1L)
        } else {
            null
        }
        return DownloadEstimate(speed, eta)
    }
}
