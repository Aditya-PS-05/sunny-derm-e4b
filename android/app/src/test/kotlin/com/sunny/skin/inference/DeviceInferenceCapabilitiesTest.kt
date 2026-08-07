package com.sunny.skin.inference

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInferenceCapabilitiesTest {
    @Test
    fun permitsSnapdragon695WithValidatedCpuFallback() {
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference("SM6375"))
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference(" sm6375 "))
    }

    @Test
    fun permitsDimensity920WithCpuFallback() {
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference("MT6877V/TZA"))
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference("mt6877"))
    }

    @Test
    fun doesNotBlockUnrelatedSocModels() {
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference("SM8550"))
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference("Tensor G3"))
        assertTrue(DeviceInferenceCapabilities.supportsReliableOnDeviceInference(""))
    }
}
