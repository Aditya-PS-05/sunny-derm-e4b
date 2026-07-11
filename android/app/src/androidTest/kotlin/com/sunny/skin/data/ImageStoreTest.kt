package com.sunny.skin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photos at rest: [ImageStore.save] must write ciphertext (never a plaintext
 * JPEG) and [ImageStore.decryptToBitmap] must recover the image.
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val store = ImageStore(ctx)

    @Test
    fun save_writesCiphertext_andDecryptsBack() {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(180, 90, 90))
        }
        val path = store.save(bmp)
        try {
            val raw = File(path).readBytes()
            // A real JPEG starts with the SOI marker 0xFFD8. Encrypted bytes must not.
            val looksLikeJpeg = raw.size >= 2 &&
                raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()
            assertFalse("stored photo must be ciphertext, not a JPEG", looksLikeJpeg)
            assertTrue("ciphertext should be non-trivial in size", raw.size > 32)

            val out = store.decryptToBitmap(path)
            assertNotNull("decrypt must recover a bitmap", out)
            assertEquals(64, out!!.width)
            assertEquals(48, out.height)
        } finally {
            store.delete(path)
        }
    }

    @Test
    fun decryptBytes_missingFile_returnsNull() {
        assertEquals(null, store.decryptBytes("/does/not/exist.jpg"))
    }
}
