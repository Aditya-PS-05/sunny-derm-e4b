package com.sunny.skin

/**
 * Single source of truth for the app's inference mode, driven by the
 * `SUNNY_INFERENCE_API_URL` build flag / environment variable:
 *
 *   empty  -> on-device mode (privacy-preserving; photos never leave the phone)
 *   set    -> INTERIM server mode (scan photos are sent to the inference API)
 *
 * Every privacy-facing string that must stay HONEST about *where* analysis runs
 * is centralised here, so switching on-device ⇄ server changes the wording in
 * exactly one place. Flip the mode by building with (or without) the flag:
 *
 *   ./gradlew :app:assembleDebug -PinferenceApiUrl=https://host:port
 */
object AppMode {

    /** True when a remote inference API is configured (interim server method). */
    val serverMode: Boolean = BuildConfig.SUNNY_INFERENCE_API_URL.isNotBlank()

    /** Shown while a scan is being described. */
    val analysingNote: String = if (serverMode) {
        "Analysing on Sunny's secure server."
    } else {
        "Running on-device. Your photo never leaves this phone."
    }

    /** The privacy promise line on the onboarding screen (icon rendered separately). */
    val photoPrivacyLine: String = if (serverMode) {
        "Encrypted on your phone; photos are sent only to be described, never stored."
    } else {
        "Your photos never leave this phone."
    }

    /** Settings › Privacy & Security subtitle. */
    val dataPrivacySubtitle: String = if (serverMode) {
        "Encrypted on your device. Scan photos go to Sunny's AI server only to be described."
    } else {
        "Your skin health data never leaves your device."
    }

    /** Model / AI description on the setup screen. */
    val aiDescription: String = if (serverMode) {
        "Sunny describes your skin using its AI server to help you track changes — it " +
            "doesn't diagnose. Descriptions are an aid for your own records, not a medical " +
            "test, so see a professional for anything that concerns you."
    } else {
        "This on-device AI describes your skin to help you track changes — it doesn't " +
            "diagnose. Its descriptions are an aid for your own records, not a medical test, " +
            "so see a professional for anything that concerns you."
    }

    /** "Ready" status text on the model setup screen. */
    val modelReadyNote: String = if (serverMode) {
        "Sunny is using its AI server for analysis."
    } else {
        "The AI model is installed and running on-device."
    }
}
