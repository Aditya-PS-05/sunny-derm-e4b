package com.sunny.skin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.ApproximateMeasurement
import com.sunny.skin.data.model.CaptureAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingCaptureStoreTest {
    private val store = PendingCaptureStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun pendingCaptureRoundTripsAndClears() {
        store.clear()
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 80, 60))
        }
        store.save(PendingCapture(
            bitmap = bitmap,
            bodyPart = BodyPart.UPPER_BACK,
            scanType = ScanType.TRACKED,
            targetScanId = "scan-1",
            referenceImagePath = "/ref",
            measurement = ApproximateMeasurement(20f, 0.2f, 0.08f),
            alignment = CaptureAlignment(0.7f, 0.01f, -0.02f, 1.04f, 2f),
            checkSessionId = "session-1",
        ))
        bitmap.recycle()

        val restored = store.restore()
        assertNotNull(restored)
        assertEquals(BodyPart.UPPER_BACK, restored!!.bodyPart)
        assertEquals(ScanType.TRACKED, restored.scanType)
        assertEquals("scan-1", restored.targetScanId)
        assertEquals(8f, restored.measurement!!.approximateSizeMm)
        assertEquals(0.7f, restored.alignment!!.score)
        assertEquals("session-1", restored.checkSessionId)
        restored.bitmap.recycle()

        store.clear()
        assertNull(store.restore())
    }
}
