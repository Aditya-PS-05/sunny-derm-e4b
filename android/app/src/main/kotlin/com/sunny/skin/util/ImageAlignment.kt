package com.sunny.skin.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun compute(beforePath: String, afterPath: String): AlignTransform =
        withContext(Dispatchers.Default) {
            val before = loadGray(beforePath) ?: return@withContext AlignTransform.Identity
            val after = loadGray(afterPath) ?: return@withContext AlignTransform.Identity
            align(before, after)
        }

    /** Decode → G×G greyscale → zero-mean / unit-variance normalise. */
    private fun loadGray(path: String): FloatArray? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
            ?: runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            ?: return null
        // Centre-crop to a square first so the working grid matches what the UI
        // shows (every Compare surface renders with ContentScale.Crop into a 1:1
        // box). Scoring on the stretched full frame would misregister non-square
        // photos, worst near the edges.
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
        var mean = 0f
        for (v in g) mean += v
        mean /= g.size
        var variance = 0f
        for (v in g) { val d = v - mean; variance += d * d }
        variance /= g.size
        val std = sqrt(variance).coerceAtLeast(1e-3f)
        for (i in g.indices) g[i] = (g[i] - mean) / std
        return g
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

    private fun align(before: FloatArray, after: FloatArray): AlignTransform {
        var best = AlignTransform.Identity
        var bestScore = score(before, after, best)

        // Coarse translation grid — the dominant misalignment.
        val range = 0.18f; val steps = 9
        for (iy in -steps..steps) for (ix in -steps..steps) {
            val t = AlignTransform(tx = ix * range / steps, ty = iy * range / steps)
            val s = score(before, after, t)
            if (s > bestScore) { bestScore = s; best = t }
        }

        // Pattern search over all four parameters, shrinking steps on stall.
        var stepT = 0.04f; var stepS = 0.06f; var stepR = 4f
        repeat(80) {
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
                if (stepT < 0.004f) return best
            }
        }
        return best
    }
}
