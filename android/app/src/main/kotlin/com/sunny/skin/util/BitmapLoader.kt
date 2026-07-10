package com.sunny.skin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlin.math.max

/**
 * Loads and downscales images to ~[TARGET_PX] on the long edge before they hit
 * the model (android_integration.md §4: "optional crop+downscale to ~768px").
 * Keeps memory bounded and inference fast on-device.
 */
object BitmapLoader {
    const val TARGET_PX = 768

    fun fromUri(context: Context, uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return downscale(bitmap)
    }

    fun fromFile(path: String): Bitmap? {
        val bmp = BitmapFactory.decodeFile(path) ?: return null
        return downscale(bmp)
    }

    fun downscale(src: Bitmap, target: Int = TARGET_PX): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= target) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = target.toFloat() / longEdge
        val out = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt(), (src.height * scale).toInt(), true,
        )
        return out
    }
}
