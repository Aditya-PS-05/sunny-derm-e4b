package com.sunny.skin.inference

import android.graphics.Bitmap
import com.sunny.skin.data.model.Analysis

/**
 * Result of a describe request. [Success] carries the parsed six fields plus the
 * raw output and model version for storage (N-11). [Unreadable] is the safe
 * failure state (F-06) shown as "couldn't read this image".
 */
sealed interface DescribeResult {
    data class Success(
        val analysis: Analysis,
        val rawOutput: String,
        val modelVersion: String,
    ) : DescribeResult

    data object Unreadable : DescribeResult
    data object ModelUnavailable : DescribeResult
}

/**
 * Orchestrates a single describe request end-to-end, enforcing the app's
 * hard rules independently of the model:
 *
 *   1. call the model (image first, verbatim prompt, greedy) — [SunnyModel]
 *   2. parse the six fields — [SchemaParser]
 *   3. reject banned disease/verdict language — [Guardrails] (S-03)
 *   4. if parse fails OR a banned word appears, re-run ONCE (F-06); greedy is
 *      deterministic so we nudge with a fresh call, then fall back to Unreadable.
 */
class SunnyDescriber(private val model: SunnyModel) {

    suspend fun describe(bitmap: Bitmap): DescribeResult {
        repeat(MAX_ATTEMPTS) {
            val raw = model.describeRaw(bitmap)
            val analysis = SchemaParser.parse(raw)
            if (analysis != null && Guardrails.isClean(analysis)) {
                return DescribeResult.Success(analysis, raw, model.version)
            }
            // else: truncated/partial parse or banned word -> suppress + retry
        }
        return DescribeResult.Unreadable
    }

    suspend fun warmUp() = model.warmUp()
    val isReady: Boolean get() = model.isReady
    fun close() = model.close()

    companion object {
        private const val MAX_ATTEMPTS = 2   // initial + one re-run (F-06)
    }
}
