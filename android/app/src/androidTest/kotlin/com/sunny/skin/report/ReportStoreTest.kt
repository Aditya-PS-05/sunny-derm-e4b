package com.sunny.skin.report

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.data.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reportRendersEphemerally_andSharesThroughDecryptingProvider() {
        val id = "test-report-${System.nanoTime()}"
        val plain = "%PDF-1.4\nprivate test report".toByteArray()
        val store = ReportStore(ctx)
        store.file(id).writeBytes(CryptoManager.encrypt(ctx, plain))
        try {
            val rendered = store.openDecryptedReport(id)?.let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            assertNotNull(rendered)
            assertArrayEquals(plain, rendered)
            val cached = ctx.cacheDir.resolve("rendered_reports")
            assertFalse("render plaintext cache must not exist", cached.exists())

            val uri = store.shareUri(id)
            assertNotNull(uri)
            val streamed = ctx.contentResolver.openInputStream(uri!!)?.use { it.readBytes() }
            assertNotNull(streamed)
            assertArrayEquals(plain, streamed)
        } finally {
            store.delete(id)
        }
    }
}
