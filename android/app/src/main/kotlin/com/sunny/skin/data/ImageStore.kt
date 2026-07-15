package com.sunny.skin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sunny.skin.data.crypto.CryptoManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Stores captured photos in app-private internal storage, AES-GCM-encrypted at
 * rest (P-01). Stored files remain app-private and are deleted with their scan;
 * server beta mode may transmit an in-memory encoded copy after explicit disclosure.
 * The on-disk bytes are ciphertext; readers decrypt via [decryptBytes] or the
 * Coil `EncryptedImage` fetcher.
 */
class ImageStore(context: Context) {
    private val appCtx = context.applicationContext
    private val dir = File(appCtx.filesDir, "scans").apply { mkdirs() }

    fun save(bitmap: Bitmap): String {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val jpeg = ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            bos.toByteArray()
        }
        file.writeBytes(CryptoManager.encrypt(appCtx, jpeg))
        return file.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Decrypted JPEG bytes for a stored photo, or null if unreadable. */
    fun decryptBytes(path: String): ByteArray? = runCatching {
        CryptoManager.decrypt(appCtx, File(path).readBytes())
    }.getOrNull()

    /** Decrypt and decode a stored photo to a bitmap (non-Coil readers). */
    fun decryptToBitmap(path: String): Bitmap? =
        decryptBytes(path)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
}
