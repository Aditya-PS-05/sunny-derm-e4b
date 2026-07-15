package com.sunny.skin.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.SunnyApp
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.BodyPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureWorkflowTest {

    @Test
    fun recheckRetakeKeepsTargetAndFreshCaptureClearsIt() {
        val vm = SunnyViewModel(ApplicationProvider.getApplicationContext<SunnyApp>())

        vm.beginRecheckCapture(
            scanId = "scan-1",
            bodyPart = BodyPart.SHOULDER,
            referenceImagePath = "/private/reference.enc",
        )
        vm.prepareRetake()

        assertEquals("scan-1", vm.capture.value.targetScanId)
        assertEquals("/private/reference.enc", vm.capture.value.referenceImagePath)
        assertEquals(ScanType.TRACKED, vm.capture.value.scanType)

        vm.beginNewCapture()

        assertNull(vm.capture.value.targetScanId)
        assertNull(vm.capture.value.referenceImagePath)
        assertEquals(ScanType.SINGLE, vm.capture.value.scanType)
    }
}
