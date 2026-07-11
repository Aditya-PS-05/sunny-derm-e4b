package com.sunny.skin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quantifies how much a tracked spot changed between two dated photos, fully
 * on-device. It reuses [ImageAlignment] to register the two frames, corrects for
 * a uniform lighting shift, then measures the residual difference that remains —
 * i.e. local changes in the spot itself rather than the whole photo getting
 * brighter/darker. The output is a RELATIVE change indicator (0..100) plus a
 * plain-language [Level], blended with the model's own field-diff so both the
 * appearance and the description drive the result.
 *
 * This is a tracking aid, NOT a risk assessment or diagnosis (design.md §8): it
 * says "this looks different, consider showing a clinician", never a verdict.
 *
 * Absolute size (mm) would need a physical reference in-frame (a coin) and robust
 * fiducial detection; that is deliberately out of scope here — the relative
 * indicator covers the "did it change?" question without asking the user to place
 * an object beside every spot.
 */
object ChangeAnalysis {

    private const val N = 96 // working grid (N×N)

    enum class Level { STABLE, MINOR, NOTABLE }

    data class ChangeResult(
        /** 0..100 overall relative change (appearance blended with description). */
        val score: Int,
        /** 0..100 pixel-level visual change after alignment + lighting correction. */
        val visualDelta: Int,
        /** How many of the model's description fields differ. */
        val fieldsChanged: Int,
        val level: Level,
    )

    /**
     * Compare [beforePath] (older) with [afterPath] (newer). [fieldsChanged] is
     * the count of differing model description fields (excluding the summary),
     * supplied by the caller which already computes it. Returns null when the
     * photos can't be read or overlap too little to measure reliably.
     */
    suspend fun compare(
        context: Context,
        beforePath: String,
        afterPath: String,
        fieldsChanged: Int,
    ): ChangeResult? = withContext(Dispatchers.Default) {
        val before = loadGray(context, beforePath) ?: return@withContext null
        val after = loadGray(context, afterPath) ?: return@withContext null

        // Register the two frames (translation + scale + rotation about centre).
        val t = ImageAlignment.compute(context, beforePath, afterPath)
        val rad = Math.toRadians(t.rotationDeg.toDouble())
        val cosT = cos(rad).toFloat(); val sinT = sin(rad).toFloat()
        val invS = 1f / t.scale

        // First pass: paired (before, aligned-after) samples over the overlap, and
        // the mean signed difference — that mean is the uniform lighting shift we
        // then subtract so it doesn't masquerade as a real change.
        val bs = FloatArray(N * N)
        val ds = FloatArray(N * N)
        var count = 0
        var sumSigned = 0f
        var i = 0
        for (y in 0 until N) {
            val cy = (y.toFloat() / (N - 1)) - 0.5f
            for (x in 0 until N) {
                val cx = (x.toFloat() / (N - 1)) - 0.5f
                val ux = cx - t.tx; val uy = cy - t.ty
                val rx = cosT * ux + sinT * uy
                val ry = -sinT * ux + cosT * uy
                val a = sampleGray(after, rx * invS, ry * invS)
                if (a >= 0f) {
                    val b = before[y * N + x]
                    val d = b - a
                    bs[count] = b; ds[count] = d
                    sumSigned += d
                    count++
                }
                i++
            }
        }
        // Need enough overlap to trust the measurement (alignment can push most of
        // one frame off-screen for very different shots).
        if (count < N * N / 4) return@withContext null

        val lightingShift = sumSigned / count
        var sumAbs = 0f
        for (k in 0 until count) sumAbs += abs(ds[k] - lightingShift)
        val meanResidual = (sumAbs / count) / 255f // 0..~1

        // Calibrated so a barely-perceptible difference reads low and an obvious
        // local change saturates: ~0.03 → ~10, ~0.14 → ~50, ~0.28+ → 100.
        val visualDelta = (meanResidual * 360f).toInt().coerceIn(0, 100)

        // Dual signal: appearance (70%) + how much the description moved (30%).
        val fieldFrac = (fieldsChanged / 5f).coerceIn(0f, 1f)
        val score = (visualDelta * 0.7f + fieldFrac * 100f * 0.3f).toInt().coerceIn(0, 100)

        val level = when {
            score >= 45 || fieldsChanged >= 3 -> Level.NOTABLE
            score >= 18 || fieldsChanged >= 1 -> Level.MINOR
            else -> Level.STABLE
        }
        ChangeResult(score, visualDelta, fieldsChanged, level)
    }

    /** Suggested re-check interval (days) for a change level — feeds the reminder. */
    fun recommendedRecheckDays(level: Level): Int = when (level) {
        Level.NOTABLE -> 14
        Level.MINOR -> 30
        Level.STABLE -> 90
    }

    /** Decrypt → decode → centre-crop square → N×N greyscale in 0..255. */
    private fun loadGray(context: Context, path: String): FloatArray? {
        val bytes = runCatching { CryptoManager.decrypt(context, File(path).readBytes()) }.getOrNull()
            ?: return null
        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return null
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, N, N, true)
        val px = IntArray(N * N)
        scaled.getPixels(px, 0, N, 0, 0, N, N)
        val g = FloatArray(N * N)
        for (j in px.indices) {
            val c = px[j]
            val r = (c shr 16) and 0xFF
            val gg = (c shr 8) and 0xFF
            val b = c and 0xFF
            g[j] = 0.299f * r + 0.587f * gg + 0.114f * b
        }
        return g
    }

    /** Bilinear sample at centred-normalised coord [-0.5,0.5]; out of bounds → -1. */
    private fun sampleGray(img: FloatArray, cx: Float, cy: Float): Float {
        val fx = (cx + 0.5f) * (N - 1)
        val fy = (cy + 0.5f) * (N - 1)
        if (fx < 0f || fy < 0f || fx > N - 1f || fy > N - 1f) return -1f
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = (x0 + 1).coerceAtMost(N - 1); val y1 = (y0 + 1).coerceAtMost(N - 1)
        val ax = fx - x0; val ay = fy - y0
        val a = img[y0 * N + x0]; val b = img[y0 * N + x1]
        val c = img[y1 * N + x0]; val d = img[y1 * N + x1]
        return a * (1 - ax) * (1 - ay) + b * ax * (1 - ay) + c * (1 - ax) * ay + d * ax * ay
    }
}
