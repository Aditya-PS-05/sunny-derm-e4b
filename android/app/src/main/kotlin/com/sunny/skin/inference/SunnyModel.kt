package com.sunny.skin.inference

import android.graphics.Bitmap

/**
 * The on-device vision-language model. Implementations wrap a concrete runtime
 * (Sunny Offline locally or Sunny AI Cloud remotely). The contract is deliberately tiny: one image
 * in, raw model text out — parsing, guardrails and re-runs live in
 * [SunnyDescriber] so every runtime enforces the same safety boundary.
 *
 * Implementations MUST:
 *  - load the model once and keep the session warm (N-02),
 *  - send the image FIRST then [Prompt.SCHEMA_PROMPT] verbatim (F-03),
 *  - decode greedily with max_new_tokens = [Prompt.MAX_NEW_TOKENS] (F-04).
 */
interface SunnyModel {
    val version: String
    val isReady: Boolean

    /** Load weights into memory. Safe to call repeatedly; loads at most once. */
    suspend fun warmUp()

    /** Run one image through the model and return its raw six-line text. */
    suspend fun describeRaw(bitmap: Bitmap, analysisId: String): String

    fun close()
}
