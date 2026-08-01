package com.sunny.skin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File
import kotlin.math.max

/**
 * Loads and downscales images to ~[TARGET_PX] on the long edge before they hit
 * the model (android_integration.md §4: "optional crop+downscale to ~768px").
 * Keeps memory bounded and inference fast on-device.
 */
object BitmapLoader {
    const val TARGET_PX = 768

    fun fromUri(context: Context, uri: Uri): Bitmap? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                validateDimensions(info.size.width, info.size.height)
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > TARGET_PX) {
                    val ratio = TARGET_PX.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                decoder.isMutableRequired = true
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            validateDimensions(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > TARGET_PX * 2) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Could not decode selected image.")
        }
        downscale(bitmap)
    }.getOrNull()

    fun fromFile(context: Context, path: String): Bitmap? {
        val bytes = runCatching { CryptoManager.decrypt(context, File(path).readBytes()) }.getOrNull()
            ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return downscale(bmp)
    }

    fun downscale(src: Bitmap, target: Int = TARGET_PX): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= target) return src
        val scale = target.toFloat() / longEdge
        val out = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt(), (src.height * scale).toInt(), true,
        )
        if (out !== src) src.recycle()
        return out
    }

    private fun validateDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Image dimensions are invalid." }
        require(width <= MAX_SOURCE_EDGE && height <= MAX_SOURCE_EDGE) { "Image is too large." }
        require(width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS) { "Image is too large." }
    }

    private const val MAX_SOURCE_EDGE = 32_768
    private const val MAX_SOURCE_PIXELS = 100_000_000L
}
