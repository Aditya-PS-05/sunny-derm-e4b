package com.sunny.skin.inference

import android.graphics.Bitmap
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A schema-faithful stand-in used until the ~6 GB GGUF weights are bundled and
 * the native runtime is wired (see [LlamaCppSunnyModel]). It emits exactly the
 * six-field format the real model produces — the training-time controlled
 * vocabulary from USING_THE_MODEL.md §2 — so the entire UI, parser, guardrail
 * and storage pipeline can be built and demoed end-to-end on any device.
 *
 * Output is deterministic per image (seeded by pixel content) to mirror the
 * greedy-decoding guarantee (N-22): the same photo yields the same description.
 */
class MockSunnyModel : SunnyModel {
    override val version = "mock-gemma4-e4b-1.0"
    override var isReady = false
        private set

    override suspend fun warmUp() {
        if (isReady) return
        delay(400)          // simulate first-load cost; real model keeps warm after
        isReady = true
    }

    override suspend fun describeRaw(bitmap: Bitmap): String {
        if (!isReady) warmUp()
        delay(700)          // simulate ~1x on-device inference latency (N-01)
        val seed = sampleSeed(bitmap)
        val colour = COLOURS[seed % COLOURS.size]
        val symmetry = SYMMETRY[(seed / 3) % SYMMETRY.size]
        val borders = BORDERS[(seed / 7) % BORDERS.size]
        val texture = TEXTURE[(seed / 11) % TEXTURE.size]
        val type = TYPES[(seed / 13) % TYPES.size]
        val summary = "A $colour $type that appears $symmetry with $borders and a " +
            "$texture. This is a visual description only, not a diagnosis — " +
            "see a clinician for any concern."
        return buildString {
            append("Lesion Type: ").append(type).append('\n')
            append("Colour: ").append(colour).append('\n')
            append("Symmetry: ").append(symmetry).append('\n')
            append("Borders: ").append(borders).append('\n')
            append("Texture: ").append(texture).append('\n')
            append("Summary: ").append(summary)
        }
    }

    override fun close() { isReady = false }

    /** Cheap deterministic hash of a downsampled version of the image. */
    private fun sampleSeed(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        var acc = 0
        for (y in 0 until small.height) for (x in 0 until small.width) {
            acc = (acc * 31 + small.getPixel(x, y)) and 0x7fffffff
        }
        if (small != bitmap) small.recycle()
        return abs(acc)
    }

    private companion object {
        val TYPES = listOf(
            "pigmented lesion", "raised papule", "flat macule",
            "small, raised area", "well-defined spot",
        )
        val COLOURS = listOf(
            "light brown", "multiple colours (light brown, dark brown, red)",
            "uniform reddish-pink", "tan and brown", "dark brown",
        )
        // Controlled vocabularies the model learned (USING_THE_MODEL.md §2).
        val SYMMETRY = listOf("roughly symmetric", "mildly asymmetric", "notably asymmetric")
        val BORDERS = listOf(
            "smooth, well-defined borders",
            "somewhat irregular borders",
            "ragged, poorly-defined borders",
        )
        val TEXTURE = listOf(
            "smooth, even surface",
            "slightly uneven surface",
            "rough or structurally varied surface",
        )
    }
}
