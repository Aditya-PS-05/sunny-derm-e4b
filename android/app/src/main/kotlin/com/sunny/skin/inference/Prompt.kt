package com.sunny.skin.inference

/**
 * The production prompt contract, mirrored in USING_THE_MODEL.md. It preserves
 * the training prompt and adds an explicit grammar-compatible safety ending.
 * The image is sent FIRST, then this text, exactly one image per request (F-03).
 */
object Prompt {
    const val SCHEMA_PROMPT: String =
        "You are a dermatology description assistant. Look at this skin lesion photo and describe what you see. Do NOT diagnose or name a disease. Report only observable features in this exact format:\n" +
        "Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>\n" +
        "Colour: <colours present>\n" +
        "Symmetry: <symmetric / asymmetric>\n" +
        "Borders: <smooth / irregular / well- or poorly-defined>\n" +
        "Texture: <smooth / rough / raised / scaly>\n" +
        "Summary: <one plain-language sentence describing the lesion's appearance>\n" +
        "Safety: This is a visual description only, not a diagnosis — see a clinician for any concern."

    /** Decoding contract (F-04): greedy, capped generation for reproducibility. */
    // The grammar permits detailed observable fields, which can exceed 160
    // model tokens. The native loop still stops as soon as all six fields and
    // the mandatory safety ending are complete.
    const val MAX_NEW_TOKENS = 256
    const val TEMPERATURE = 0.0f   // greedy; the schema is not a creative task

    const val DEVICE_MAX_NEW_TOKENS = 128
}
