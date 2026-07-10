package com.sunny.skin.inference

/**
 * The prompt contract. This string is BYTE-IDENTICAL to USING_THE_MODEL.md §2
 * and scripts/generate_labels.py::USER_PROMPT. Do NOT paraphrase, shorten, or
 * "improve" it — matching the training prompt is the single biggest lever on
 * output quality. The image is sent FIRST, then this text, exactly one image
 * per request (F-03).
 */
object Prompt {
    const val SCHEMA_PROMPT: String =
        "You are a dermatology description assistant. Look at this skin lesion photo and describe what you see. Do NOT diagnose or name a disease. Report only observable features in this exact format:\n" +
        "Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>\n" +
        "Colour: <colours present>\n" +
        "Symmetry: <symmetric / asymmetric>\n" +
        "Borders: <smooth / irregular / well- or poorly-defined>\n" +
        "Texture: <smooth / rough / raised / scaly>\n" +
        "Summary: <one plain-language sentence describing the lesion's appearance and reminding the user this is not a diagnosis>"

    /** Decoding contract (F-04): greedy, capped generation for reproducibility. */
    const val MAX_NEW_TOKENS = 180
    const val TEMPERATURE = 0.0f   // greedy; the schema is not a creative task
}
