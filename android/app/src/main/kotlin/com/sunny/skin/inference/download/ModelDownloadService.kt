package com.sunny.skin.inference.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sunny.skin.MainActivity
import com.sunny.skin.R
import com.sunny.skin.inference.ModelProvider
import com.sunny.skin.inference.tier.SunnyAccessPolicy
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.subscription.SubscriptionEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs the ~605 MB Sunny Offline download as a foreground service so it survives the app
 * being backgrounded or killed. Progress is mirrored into
 * [ModelDownloadManager.status] (the UI's source of truth) and an ongoing
 * notification. The underlying [WeightDownloader] resumes partial transfers, so
 * a restart continues rather than restarts.
 */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel(); job = null
            ModelDownloadManager.publishIdleIfDownloading()
            stopSelfSafe()
            return START_NOT_STICKY
        }
        val tier = intent?.getStringExtra(EXTRA_TIER)?.let {
            runCatching { SunnyModelTier.valueOf(it) }.getOrNull()
        }
        if (tier == null) {
            ModelDownloadManager.publishFailed("The requested model pack was not identified.")
            stopSelfSafe()
            return START_NOT_STICKY
        }
        startDownload(tier)
        return START_REDELIVER_INTENT
    }

    private fun startDownload(tier: SunnyModelTier) {
        if (job?.isActive == true) return
        if (!SunnyAccessPolicy.canDownloadLocal(
                tier,
                SubscriptionEntitlements.accessEntitlement(),
                System.currentTimeMillis(),
            )
        ) {
            ModelDownloadManager.publishFailed("Verified Pro access is required to download Sunny Offline.")
            stopSelfSafe()
            return
        }
        val pack = ModelPackCatalog.publishedPack(tier)
        if (pack == null) {
            ModelDownloadManager.publishFailed("${tier.displayName} has not been published yet.")
            stopSelfSafe()
            return
        }
        ensureChannel()
        startForegroundCompat(buildNotification(tier, 0, pack.totalBytes))
        job = scope.launch {
            val downloader = WeightDownloader(
                ModelProvider.modelsDir(applicationContext),
                com.sunny.skin.data.BetaCredentialStore(applicationContext).inferenceToken,
            )
            val total = pack.totalBytes
            var completed = 0L
            for (asset in pack.assets) {
                val base = completed
                val result = downloader.download(tier, asset) { done, _ ->
                    val d = base + done
                    ModelDownloadManager.publishDownloading(tier, d, total)
                    notify(buildNotification(tier, d, total))
                }
                if (result.isFailure) {
                    ModelDownloadManager.publishFailed(
                        result.exceptionOrNull()?.message ?: "download failed",
                    )
                    stopSelfSafe(); return@launch
                }
                completed = base + asset.sizeBytes
            }
            ModelDownloadManager.publishVerifying()
            if (ModelProvider.packPresent(applicationContext, tier)) {
                ModelDownloadManager.activateInstalledModel(tier)
            } else {
                ModelDownloadManager.publishFailed("files missing after download")
            }
            stopSelfSafe()
        }
    }

    private fun stopSelfSafe() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Model download", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Progress of the on-device AI model download." },
            )
        }
    }

    private fun buildNotification(tier: SunnyModelTier, done: Long, total: Long): Notification {
        val pct = if (total == 0L) 0 else (done * 100 / total).toInt()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading ${tier.displayName}")
            .setContentText("${gib(done)} / ${gib(total)}  ·  $pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    private fun gib(b: Long) = "%.1f GB".format(b / 1_000_000_000.0)

    companion object {
        const val ACTION_CANCEL = "com.sunny.skin.DOWNLOAD_CANCEL"
        private const val EXTRA_TIER = "sunny_model_tier"
        private const val CHANNEL = "sunny_model_download"
        private const val NOTIF_ID = 4242

        fun start(context: Context, tier: SunnyModelTier) {
            val i = Intent(context, ModelDownloadService::class.java)
                .putExtra(EXTRA_TIER, tier.name)
            context.startForegroundService(i)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
