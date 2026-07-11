package com.sunny.skin

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.sunny.skin.data.crypto.DataReset
import com.sunny.skin.data.crypto.EncryptedImageFetcher
import com.sunny.skin.data.crypto.EncryptedImageKeyer
import com.sunny.skin.data.repo.ScanRepository
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.reminder.ReminderScheduler

/** Holds process-wide singletons (repository). Kept deliberately dependency-free. */
class SunnyApp : Application(), ImageLoaderFactory {
    val repository: ScanRepository by lazy { ScanRepository(this) }

    /**
     * Coil loader that decrypts [com.sunny.skin.data.crypto.EncryptedImage] photos
     * in memory, with the disk cache disabled so decrypted plaintext never lands
     * in Coil's on-disk cache.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(EncryptedImageKeyer())
                add(EncryptedImageFetcher.Factory(this@SunnyApp))
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

    companion object {
        lateinit var instance: SunnyApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Clean-slate any pre-encryption data before the encrypted DB is opened.
        DataReset.runIfNeeded(this)
        ModelDownloadManager.init(this)
        ReminderScheduler.ensureChannel(this)
    }
}
