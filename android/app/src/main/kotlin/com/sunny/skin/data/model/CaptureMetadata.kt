package com.sunny.skin.data.model

import kotlin.math.roundToInt

/** Technical metadata about how closely a follow-up frame matched its reference photo. */
data class CaptureAlignment(
    val score: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotationDegrees: Float,
)

/**
 * A user-positioned reference-to-area ratio. This is deliberately approximate:
 * both spans are measured in the same square preview, so their ratio is stable
 * without pretending the phone camera is a clinical measuring instrument.
 */
data class ApproximateMeasurement(
    val referenceSizeMm: Float,
    val referenceSpan: Float,
    val targetSpan: Float,
) {
    val approximateSizeMm: Float
        get() = referenceSizeMm * targetSpan / referenceSpan

    val isValid: Boolean
        get() = referenceSizeMm in 1f..100f && referenceSpan >= 0.04f && targetSpan >= 0.02f

    fun formattedSize(): String {
        val tenths = (approximateSizeMm * 10f).roundToInt() / 10f
        return if (tenths % 1f == 0f) "${tenths.toInt()} mm" else "$tenths mm"
    }
}
