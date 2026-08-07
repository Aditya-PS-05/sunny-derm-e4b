package com.sunny.skin.inference.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.ModelProvider
import com.sunny.skin.inference.tier.SunnyAccessPolicy
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.subscription.SubscriptionEntitlements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Coarse state of the on-device model preparation, surfaced to the setup UI. */
sealed interface ModelStatus {
    data object NotConfigured : ModelStatus          // ModelSource.baseUrl not set
    data object Idle : ModelStatus                    // configured, nothing downloaded yet
    data class Downloading(
        val tier: SunnyModelTier,
        val done: Long,
        val total: Long,
        val bytesPerSecond: Long = 0L,
        val etaSeconds: Long? = null,
    ) : ModelStatus {
        val fraction: Float get() = if (total == 0L) 0f else done.toFloat() / total
    }
    data object Verifying : ModelStatus
    data object Ready : ModelStatus                   // complete pack and native runtime available
    data class Failed(val message: String) : ModelStatus
}

/**
 * Process-wide manager for the install-time model pack. Play delivers the pack
 * with the app; this manager verifies/materializes it and resets [ModelProvider]
 * after successful preparation. Legacy download states remain for old installs.
 */
object ModelDownloadManager {
    private lateinit var appContext: Context
    private val progressEstimator = DownloadProgressEstimator()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var preparingBundledPack = false

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        ModelProvider.purgeLegacyLocalPacks(appContext)
        _status.value = currentStatus()
        prepareBundledPackIfNeeded()
    }

    /** Re-check the weight folders (e.g. after an adb push) and refresh status. */
    fun refresh() {
        if (!::appContext.isInitialized) return
        _status.value = currentStatus()
        prepareBundledPackIfNeeded()
    }

    /** Retained for compatibility with the retired network downloader. */
    fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Any validated internet transport, including cellular/mobile data. */
    fun hasInternetConnection(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Prepare the Play-installed model after a storage/RAM preflight. Failures
     * surface as [ModelStatus.Failed] with a user-facing reason.
     */
    fun start(
        tier: SunnyModelTier,
        allowMetered: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!SunnyAccessPolicy.canDownloadLocal(tier, SubscriptionEntitlements.accessEntitlement(), nowMillis)) {
            _status.value = ModelStatus.Failed(
                when (tier) {
                    SunnyModelTier.SUNNY_MOE -> "Sunny Offline requires verified Pro access."
                    SunnyModelTier.PRO_CLOUD -> "Sunny Pro is server-only and cannot be downloaded."
                },
            )
            return
        }
        val pack = ModelPackCatalog.publishedPack(tier)
        if (pack == null) {
            _status.value = ModelStatus.Failed(
                "${tier.displayName} weights have not been published in this build yet.",
            )
            return
        }
        if (BundledModelInstaller.contains(appContext, pack)) {
            prepareBundledPack(pack)
            return
        }
        startPack(pack, allowMetered)
    }

    /** Keeps developer model setup usable without weakening release access checks. */
    fun startCurrentPackForDebug(allowMetered: Boolean = true) {
        check(BuildConfig.DEBUG) { "The development download bypass is debug-only." }
        startPack(ModelPackCatalog.sunnyMoe, allowMetered)
    }

    private fun startPack(pack: ModelPack, allowMetered: Boolean) {
        @Suppress("UNUSED_VARIABLE") val ignored = allowMetered
        _status.value = ModelStatus.Failed(
            "This APK does not contain the Play install-time model pack. " +
                "Install Sunny from its Google Play App Bundle, or use the developer adb-push script.",
        )
    }

    fun cancel() = publishIdleIfDownloading()

    // --- Called by ModelDownloadService to mirror progress into the UI flow ---
    fun publishDownloading(tier: SunnyModelTier, done: Long, total: Long) {
        val estimate = progressEstimator.update(done, total, SystemClock.elapsedRealtime())
        _status.value = ModelStatus.Downloading(
            tier = tier,
            done = done,
            total = total,
            bytesPerSecond = estimate.bytesPerSecond,
            etaSeconds = estimate.etaSeconds,
        )
    }
    fun publishVerifying() { _status.value = ModelStatus.Verifying }
    fun activateInstalledModel(@Suppress("UNUSED_PARAMETER") tier: SunnyModelTier) {
        ModelProvider.reset()
        _status.value = currentStatus()
    }
    fun publishFailed(message: String) { _status.value = ModelStatus.Failed(message) }
    fun publishIdleIfDownloading() {
        if (_status.value is ModelStatus.Downloading) _status.value = ModelStatus.Idle
    }

    private fun prepareBundledPackIfNeeded() {
        val pack = ModelPackCatalog.sunnyMoe
        if (!ModelProvider.packPresent(appContext, pack.tier) &&
            BundledModelInstaller.contains(appContext, pack)
        ) {
            prepareBundledPack(pack)
        }
    }

    private fun prepareBundledPack(pack: ModelPack) {
        if (preparingBundledPack || ModelProvider.packPresent(appContext, pack.tier)) return
        when (val preflight = DownloadPreflight.check(appContext, pack)) {
            is Preflight.Blocked -> {
                _status.value = ModelStatus.Failed(preflight.reason)
                return
            }
            Preflight.Ok -> Unit
        }
        preparingBundledPack = true
        _status.value = ModelStatus.Verifying
        scope.launch {
            val result = BundledModelInstaller.install(appContext, pack)
            preparingBundledPack = false
            if (result.isSuccess && ModelProvider.packPresent(appContext, pack.tier)) {
                activateInstalledModel(pack.tier)
            } else {
                publishFailed(
                    result.exceptionOrNull()?.message
                        ?: "The bundled offline model could not be prepared.",
                )
            }
        }
    }

    private fun currentStatus(): ModelStatus = when {
        ModelProvider.weightsPresent(appContext) && ModelProvider.nativeRuntimeAvailable() ->
            ModelStatus.Ready
        ModelProvider.weightsPresent(appContext) -> ModelStatus.Failed(
            "The Sunny Offline pack is installed, but its native runtime is unavailable.",
        )
        preparingBundledPack -> ModelStatus.Verifying
        !ModelSource.isConfigured -> ModelStatus.NotConfigured
        else -> ModelStatus.Idle
    }

    private fun formatBytes(bytes: Long): String = if (bytes < 100_000_000L) {
        "%.1f MB".format(bytes / 1_000_000.0)
    } else {
        "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
