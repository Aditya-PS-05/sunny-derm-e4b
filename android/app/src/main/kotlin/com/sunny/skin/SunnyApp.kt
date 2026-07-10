package com.sunny.skin

import android.app.Application
import com.sunny.skin.data.repo.ScanRepository
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.reminder.ReminderScheduler

/** Holds process-wide singletons (repository). Kept deliberately dependency-free. */
class SunnyApp : Application() {
    val repository: ScanRepository by lazy { ScanRepository(this) }

    companion object {
        lateinit var instance: SunnyApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ModelDownloadManager.init(this)
        ReminderScheduler.ensureChannel(this)
    }
}
