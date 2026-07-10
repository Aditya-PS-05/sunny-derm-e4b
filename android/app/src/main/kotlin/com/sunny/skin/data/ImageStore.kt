package com.sunny.skin.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stores captured photos in app-private internal storage only (P-01). Files
 * never leave the device; they are deleted with their scan.
 */
class ImageStore(context: Context) {
    private val dir = File(context.filesDir, "scans").apply { mkdirs() }

    fun save(bitmap: Bitmap): String {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
