package com.sunny.skin

import android.content.Context
import com.sunny.skin.inference.ModelProvider

/**
 * Centralises copy and behaviour that differ between the on-device model and the
 * interim beta inference server. There are two distinct notions here:
 *
 *  - [serverMode] / [insecureBetaTransport] are COMPILE-TIME capability flags:
 *    is a server URL baked into this (beta) build at all? They decide whether the
 *    runtime "analysis source" toggle and the beta disclosures are shown. Release
 *    builds have a blank URL, so both are false and none of this surfaces.
 *
 *  - The functions taking `server: Boolean` are RUNTIME copy: they reflect the
 *    engine actually selected right now (the Settings toggle), so scan-time text
 *    stays HONEST about *where* a photo goes — not merely what the build can do.
 *    Callers pass [serverActive] (or the collected toggle state).
 *
 * Flip the build capability with the inference flag; flip the runtime engine with
 * the in-app toggle (see [ModelProvider.useServer]).
 */
object AppMode {

    /** True only for the store-facing release build type. */
    val publicRelease: Boolean = BuildConfig.SUNNY_PUBLIC_RELEASE

    /** True when a remote inference API is baked into this build (beta capability). */
    val serverMode: Boolean = !publicRelease && BuildConfig.SUNNY_INFERENCE_API_URL.isNotBlank()

    /** True only for the explicitly allowed cleartext development beta. */
    val insecureBetaTransport: Boolean = serverMode &&
        BuildConfig.SUNNY_INFERENCE_API_URL.startsWith("http://")

    /** The engine actually selected right now: server toggle on AND configured. */
    fun serverActive(context: Context): Boolean = ModelProvider.useServer(context)

    // ---- First-run disclosures: shown whenever the build CAN use a server ----
    // (Onboarding happens before the toggle is reachable and the toggle defaults
    //  to server, so these disclose the server path conservatively by capability.)

    /** The privacy promise line on the onboarding screen (icon rendered separately). */
    val photoPrivacyLine: String = if (serverMode) {
        "Scan photos are sent to Sunny's beta server for visual description."
    } else {
        "Scan photos are processed and stored on this phone."
    }

    /** Explicit first-run acknowledgement, adjusted to the build capability. */
    val onboardingAcknowledgement: String = if (serverMode) {
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. " +
            "My scan photos will be sent to Sunny's beta inference server for processing."
    } else {
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. " +
            "I will seek professional care for concerns."
    }

    // ---- Runtime, engine-dependent copy: reflects the current toggle ----

    /** Shown while a scan is being described. */
    fun analysingNote(server: Boolean): String = if (server) {
        "Analysing on Sunny's beta inference server."
    } else {
        "Running on-device. Your photo never leaves this phone."
    }

    /** Settings › Privacy & Security subtitle. */
    fun dataPrivacySubtitle(server: Boolean): String = if (server) {
        "Saved data is encrypted on this device. Scan photos are sent to Sunny's beta server."
    } else {
        "Scans stay on this device unless you explicitly share an export."
    }

    /** Model / AI description on the setup screen. */
    fun aiDescription(server: Boolean): String = if (server) {
        "Sunny describes your skin using its AI server to help you track changes — it " +
            "doesn't diagnose. Descriptions are an aid for your own records, not a medical " +
            "test, so see a professional for anything that concerns you."
    } else {
        "This on-device AI describes your skin to help you track changes — it doesn't " +
            "diagnose. Its descriptions are an aid for your own records, not a medical test, " +
            "so see a professional for anything that concerns you."
    }

    /** "Ready" status text on the model setup screen. */
    fun modelReadyNote(server: Boolean): String = if (server) {
        "Sunny is using its AI server for analysis."
    } else {
        "The AI model is installed and running on-device."
    }

    /** Short model/service summary on the setup screen. */
    fun modelSummary(server: Boolean): String = if (server) {
        "Temporary beta inference service. Scan photos are sent to the configured server and " +
            "the structured visual description is returned."
    } else {
        "On-device dermatology describer. ~6.0 GB (5.0 GB language model + 990 MB vision). " +
            "Runs fully offline once installed."
    }

    /** Shown when the selected engine cannot produce a result. */
    fun unavailableMessage(server: Boolean): String = if (server) {
        "Sunny's beta inference server is unavailable. Check your connection and try again."
    } else {
        "The installed AI model could not start. Install it from Settings before scanning."
    }
}
