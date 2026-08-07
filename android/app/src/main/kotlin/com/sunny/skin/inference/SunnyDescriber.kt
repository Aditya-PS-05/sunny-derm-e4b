package com.sunny.skin.inference

import android.graphics.Bitmap
import android.util.Log
import com.sunny.skin.data.model.Analysis
import java.util.UUID

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
 *   4. if parsing fails or a banned word appears, fail closed as Unreadable.
 *      Decoding is greedy, so repeating the same image and prompt would produce
 *      the same rejected output while doubling mobile latency.
 */
class SunnyDescriber(private val model: SunnyModel) {

    suspend fun describe(bitmap: Bitmap): DescribeResult {
        val analysisId = UUID.randomUUID().toString()
        val raw = model.describeRaw(bitmap, analysisId)
        // Check the raw response before normalization so controlled wording can
        // never hide a diagnosis, verdict, or leaked model token.
        if (!Guardrails.isClean(raw)) return DescribeResult.Unreadable
        val analysis = SchemaParser.parse(raw)
        if (analysis != null && Guardrails.isClean(analysis)) {
            return DescribeResult.Success(analysis, raw, model.version)
        }
        Log.w(
            "SunnyAnalysis",
            "Rejected model output: schemaValid=${analysis != null}, outputLength=${raw.length}",
        )
        return DescribeResult.Unreadable
    }

    suspend fun warmUp() = model.warmUp()
    val isReady: Boolean get() = model.isReady
    fun close() = model.close()
}
