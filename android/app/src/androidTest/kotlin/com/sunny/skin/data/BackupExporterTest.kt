package com.sunny.skin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.SunnyApp
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.ApproximateMeasurement
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = (context.applicationContext as SunnyApp).repository

    @Test
    fun exportContainsManifestNotesAndDecryptedPhoto() = runBlocking {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(180, 120, 90))
        }
        val imagePath = repository.imageStore().save(bitmap)
        bitmap.recycle()
        val scanId = repository.createScan(
            imagePath = imagePath,
            bodyPart = BodyPart.SHOULDER,
            scanType = ScanType.TRACKED,
            analysis = Analysis("Spot", "Brown", "Even", "Visible", "Smooth", "Test summary"),
            modelVersion = "test",
            rawOutput = "test output",
            now = System.currentTimeMillis(),
            measurement = ApproximateMeasurement(20f, 0.2f, 0.08f),
        )
        repository.updateNotes(scanId, "Test private note", System.currentTimeMillis())
        val archive = BackupExporter(context).export("long export password".toCharArray())

        try {
            var manifest: String? = null
            var photoBytes = 0
            BackupCipher.read(archive, "long export password".toCharArray()) { decrypted ->
                ZipInputStream(decrypted).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.name == "manifest.json" -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                            entry.name.startsWith("images/") -> photoBytes += zip.readBytes().size
                            else -> zip.readBytes()
                        }
                    }
                }
            }
            assertNotNull(manifest)
            assertTrue(manifest!!.contains("Test private note"))
            assertTrue(manifest!!.contains("\"approximateSizeMm\": 8"))
            assertTrue(photoBytes > 0)
        } finally {
            repository.allScansOnce().firstOrNull { it.scan.id == scanId }?.let {
                repository.deleteScan(it)
            }
            archive.delete()
        }
    }
}
