package com.sunny.skin.util

import android.graphics.Bitmap
import kotlin.math.sqrt

enum class PhotoQualityIssue(val userMessage: String) {
    TOO_SMALL("Move closer and use a larger photo so the spot fills more of the frame."),
    TOO_DARK("The photo is too dark. Add even lighting and avoid heavy shadows."),
    TOO_BRIGHT("The photo is overexposed. Reduce glare or move away from direct flash."),
    LOW_CONTRAST("The photo has very little visible detail. Refocus and use even lighting."),
}

/** Conservative photographic checks only; this never interprets skin or lesions. */
object PhotoQuality {
    fun assess(bitmap: Bitmap): PhotoQualityIssue? {
        if (bitmap.width < MIN_DIMENSION || bitmap.height < MIN_DIMENSION) {
            return PhotoQualityIssue.TOO_SMALL
        }
        val sampleWidth = minOf(SAMPLE_SIDE, bitmap.width)
        val sampleHeight = minOf(SAMPLE_SIDE, bitmap.height)
        val sampled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
        return try {
            val pixels = IntArray(sampleWidth * sampleHeight)
            sampled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            assessLuma(
                pixels.map { colour ->
                    val red = colour shr 16 and 0xff
                    val green = colour shr 8 and 0xff
                    val blue = colour and 0xff
                    (red * 299 + green * 587 + blue * 114) / 1000
                },
            )
        } finally {
            if (sampled !== bitmap) sampled.recycle()
        }
    }

    internal fun assessLuma(samples: List<Int>): PhotoQualityIssue? {
        if (samples.isEmpty()) return PhotoQualityIssue.LOW_CONTRAST
        val mean = samples.average()
        if (mean < MIN_MEAN_LUMA) return PhotoQualityIssue.TOO_DARK
        if (mean > MAX_MEAN_LUMA) return PhotoQualityIssue.TOO_BRIGHT
        val variance = samples.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / samples.size
        return if (sqrt(variance) < MIN_LUMA_DEVIATION) {
            PhotoQualityIssue.LOW_CONTRAST
        } else {
            null
        }
    }

    private const val MIN_DIMENSION = 320
    private const val SAMPLE_SIDE = 32
    private const val MIN_MEAN_LUMA = 32.0
    private const val MAX_MEAN_LUMA = 238.0
    private const val MIN_LUMA_DEVIATION = 8.0
}
