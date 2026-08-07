package com.sunny.skin.inference

import android.os.Build
/** Device-specific guardrails for native inference paths validated on real hardware. */
object DeviceInferenceCapabilities {
    fun supportsReliableOnDeviceInference(): Boolean =
        supportsReliableOnDeviceInference(currentSocModel())

    internal fun supportsReliableOnDeviceInference(
        @Suppress("UNUSED_PARAMETER") socModel: String,
    ): Boolean = true

    fun unavailableReason(): String =
        "Sunny Offline is unavailable on this device. Use Cloud analysis instead."

    private fun currentSocModel(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.orEmpty()
    } else {
        ""
    }
}
