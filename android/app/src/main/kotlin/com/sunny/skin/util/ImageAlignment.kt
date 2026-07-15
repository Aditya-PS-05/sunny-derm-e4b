package com.sunny.skin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A similarity transform (translation + uniform scale + rotation) that warps the
 * "after" photo so the tracked spot lands on top of the "before" photo. Values
 * are resolution-independent: [tx]/[ty] are fractions of the view width/height,
 * [scale] is a multiplier and [rotationDeg] is clockwise degrees, all about the
 * image centre — so the same struct drives both the on-screen `graphicsLayer`
 * and the offline scoring below.
 */
data class AlignTransform(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
) {
    companion object { val Identity = AlignTransform() }
}

enum class FramingQuality { HIGH, MODERATE, LOW }

data class AlignmentResult(
    val transform: AlignTransform = AlignTransform.Identity,
    val score: Float = 0f,
) {
    val quality: FramingQuality
        get() = when {
            score >= 0.55f -> FramingQuality.HIGH
            score >= 0.32f -> FramingQuality.MODERATE
            else -> FramingQuality.LOW
        }

    val isUsable: Boolean get() = quality != FramingQuality.LOW

    /** Strict enough for live camera feedback; this describes framing only. */
    val framingReady: Boolean
        get() = score >= 0.46f &&
            kotlin.math.abs(transform.tx) <= 0.07f &&
            kotlin.math.abs(transform.ty) <= 0.07f &&
            kotlin.math.abs(transform.scale - 1f) <= 0.13f &&
            kotlin.math.abs(transform.rotationDeg) <= 6f
}

/**
 * Intensity-based image registration with no native/OpenCV dependency. Both
 * photos are reduced to a small, per-image contrast-normalised greyscale grid
 * (so lighting differences wash out), then a coarse translation grid followed by
 * a Hooke–Jeeves pattern search maximises normalised cross-correlation over
 * (translation, scale, rotation). Runs on [Dispatchers.Default] in well under a
 * second for the tiny working grid.
 */
object ImageAlignment {
    private const val G = 64 // working grid (G×G)

    suspend fun compute(context: Context, beforePath: String, afterPath: String): AlignTransform =
        computeResult(context, beforePath, afterPath).transform

    suspend fun computeResult(context: Context, beforePath: String, afterPath: String): AlignmentResult =
        withContext(Dispatchers.Default) {
            val before = loadGray(context, beforePath) ?: return@withContext AlignmentResult()
            val after = loadGray(context, afterPath) ?: return@withContext AlignmentResult()
            align(before, after)
        }

    /** Decrypt and prepare a reference once, then reuse it for live camera frames. */
    suspend fun loadReferenceGrid(context: Context, path: String): FloatArray? =
        withContext(Dispatchers.Default) { loadGray(context, path) }

    /** Match a prepared reference against a camera analysis frame without retaining the frame. */
    fun matchPreview(reference: FloatArray, image: ImageProxy): AlignmentResult {
        if (reference.size != G * G) return AlignmentResult()
        val current = imageToGray(image) ?: return AlignmentResult()
        return align(reference, current, coarseSteps = 5, iterations = 24)
    }

    /** Decrypt → decode → G×G greyscale → zero-mean / unit-variance normalise. */
    private fun loadGray(context: Context, path: String): FloatArray? {
        val bytes = runCatching { CryptoManager.decrypt(context, File(path).readBytes()) }.getOrNull()
            ?: return null
        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return null
        val result = bitmapToGray(bmp)
        bmp.recycle()
        return result
    }

    private fun bitmapToGray(bmp: Bitmap): FloatArray {
        // Centre-crop to a square first so the working grid matches the UI.
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, G, G, true)
        val px = IntArray(G * G)
        scaled.getPixels(px, 0, G, 0, 0, G, G)
        val g = FloatArray(G * G)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val gg = (c shr 8) and 0xFF
            val b = c and 0xFF
            g[i] = 0.299f * r + 0.587f * gg + 0.114f * b
        }
        if (scaled !== square) scaled.recycle()
        if (square !== bmp) square.recycle()
        return normalize(g)
    }

    /** Upright, centre-cropped luminance grid matching PreviewView's fill-centre framing. */
    private fun imageToGray(image: ImageProxy): FloatArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rawW = image.width
        val rawH = image.height
        val rotation = image.imageInfo.rotationDegrees.mod(360)
        val uprightW = if (rotation == 90 || rotation == 270) rawH else rawW
        val uprightH = if (rotation == 90 || rotation == 270) rawW else rawH
        val side = minOf(uprightW, uprightH).toFloat()
        val left = (uprightW - side) / 2f
        val top = (uprightH - side) / 2f
        val grid = FloatArray(G * G)

        for (gy in 0 until G) {
            val uy = top + (gy + 0.5f) * side / G
            for (gx in 0 until G) {
                val ux = left + (gx + 0.5f) * side / G
                val (rawX, rawY) = when (rotation) {
                    90 -> uy to (rawH - 1f - ux)
                    180 -> (rawW - 1f - ux) to (rawH - 1f - uy)
                    270 -> (rawW - 1f - uy) to ux
                    else -> ux to uy
                }
                val x = rawX.toInt().coerceIn(0, rawW - 1)
                val y = rawY.toInt().coerceIn(0, rawH - 1)
                val index = y * plane.rowStride + x * plane.pixelStride
                grid[gy * G + gx] = if (index < buffer.limit()) {
                    (buffer.get(index).toInt() and 0xff).toFloat()
                } else {
                    0f
                }
            }
        }
        return normalize(grid)
    }

    private fun normalize(values: FloatArray): FloatArray {
        var mean = 0f
        for (value in values) mean += value
        mean /= values.size
        var variance = 0f
        for (value in values) {
            val delta = value - mean
            variance += delta * delta
        }
        variance /= values.size
        val std = sqrt(variance).coerceAtLeast(1e-3f)
        for (index in values.indices) values[index] = (values[index] - mean) / std
        return values
    }

    /** Bilinear sample at centred-normalised coord [-0.5,0.5]; out of bounds → 0. */
    private fun sample(img: FloatArray, cx: Float, cy: Float): Float {
        val fx = (cx + 0.5f) * (G - 1)
        val fy = (cy + 0.5f) * (G - 1)
        if (fx < 0f || fy < 0f || fx > G - 1f || fy > G - 1f) return 0f
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = (x0 + 1).coerceAtMost(G - 1); val y1 = (y0 + 1).coerceAtMost(G - 1)
        val ax = fx - x0; val ay = fy - y0
        val a = img[y0 * G + x0]; val b = img[y0 * G + x1]
        val c = img[y1 * G + x0]; val d = img[y1 * G + x1]
        return a * (1 - ax) * (1 - ay) + b * ax * (1 - ay) + c * (1 - ax) * ay + d * ax * ay
    }

    /** Correlation of [before] with the [after] warped by [t] (higher = better). */
    private fun score(before: FloatArray, after: FloatArray, t: AlignTransform): Float {
        val rad = Math.toRadians(t.rotationDeg.toDouble())
        val cosT = cos(rad).toFloat(); val sinT = sin(rad).toFloat()
        val invS = 1f / t.scale
        var sum = 0f
        var i = 0
        for (y in 0 until G) {
            val cy = (y.toFloat() / (G - 1)) - 0.5f
            for (x in 0 until G) {
                val cx = (x.toFloat() / (G - 1)) - 0.5f
                // Invert the display transform: undo translate, rotate, scale.
                val ux = cx - t.tx; val uy = cy - t.ty
                val rx = cosT * ux + sinT * uy
                val ry = -sinT * ux + cosT * uy
                sum += before[i] * sample(after, rx * invS, ry * invS)
                i++
            }
        }
        return sum / (G * G)
    }

    private fun align(
        before: FloatArray,
        after: FloatArray,
        coarseSteps: Int = 9,
        iterations: Int = 80,
    ): AlignmentResult {
        var best = AlignTransform.Identity
        var bestScore = score(before, after, best)

        // Coarse translation grid — the dominant misalignment.
        val range = 0.18f; val steps = coarseSteps
        for (iy in -steps..steps) for (ix in -steps..steps) {
            val t = AlignTransform(tx = ix * range / steps, ty = iy * range / steps)
            val s = score(before, after, t)
            if (s > bestScore) { bestScore = s; best = t }
        }

        // Pattern search over all four parameters, shrinking steps on stall.
        var stepT = 0.04f; var stepS = 0.06f; var stepR = 4f
        repeat(iterations) {
            val cands = listOf(
                best.copy(tx = best.tx + stepT), best.copy(tx = best.tx - stepT),
                best.copy(ty = best.ty + stepT), best.copy(ty = best.ty - stepT),
                best.copy(scale = (best.scale + stepS).coerceIn(0.6f, 1.7f)),
                best.copy(scale = (best.scale - stepS).coerceIn(0.6f, 1.7f)),
                best.copy(rotationDeg = best.rotationDeg + stepR),
                best.copy(rotationDeg = best.rotationDeg - stepR),
            )
            var improved = false
            for (c in cands) {
                val s = score(before, after, c)
                if (s > bestScore) { bestScore = s; best = c; improved = true }
            }
            if (!improved) {
                stepT *= 0.5f; stepS *= 0.5f; stepR *= 0.5f
                if (stepT < 0.004f) return AlignmentResult(best, bestScore.coerceIn(-1f, 1f))
            }
        }
        return AlignmentResult(best, bestScore.coerceIn(-1f, 1f))
    }
}
