package com.sunny.skin.data.crypto

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer
import java.io.File

/**
 * Coil fetcher that decrypts an [EncryptedImage] file in memory and hands the
 * plaintext bytes to Coil as a source. Paired with a disk-cache-disabled
 * ImageLoader (see SunnyApp) so decrypted image bytes never land on disk.
 */
class EncryptedImageFetcher(
    private val data: EncryptedImage,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val bytes = CryptoManager.decrypt(context, File(data.path).readBytes())
        val source = ImageSource(Buffer().apply { write(bytes) }, context)
        return SourceResult(source = source, mimeType = null, dataSource = DataSource.DISK)
    }

    class Factory(context: Context) : Fetcher.Factory<EncryptedImage> {
        private val appCtx = context.applicationContext
        override fun create(data: EncryptedImage, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, appCtx)
    }
}

/** Cache key = path + last-modified, so a replaced photo invalidates the memory cache. */
class EncryptedImageKeyer : Keyer<EncryptedImage> {
    override fun key(data: EncryptedImage, options: Options): String =
        "${data.path}:${File(data.path).lastModified()}"
}
