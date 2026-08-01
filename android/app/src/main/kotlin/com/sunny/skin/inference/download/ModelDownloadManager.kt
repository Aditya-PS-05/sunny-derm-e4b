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
import kotlinx.coroutines.launch

/** Coarse state of the on-device model install, surfaced to the setup UI. */
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
 * Process-wide manager for local model-pack downloads. Access is checked before
 * starting the service. The manager survives navigation, downloads each asset sequentially
 * and resets [ModelProvider] after successful verification.
 */
object ModelDownloadManager {
    private lateinit var appContext: Context
    private val progressEstimator = DownloadProgressEstimator()

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        ModelProvider.purgeLegacyLocalPacks(appContext)
        // Weights present locally (adb push / prior download) win regardless of
        // whether a download URL is configured — no download is needed then.
        _status.value = currentStatus()
    }

    /** Re-check the weight folders (e.g. after an adb push) and refresh status. */
    fun refresh() {
        if (!::appContext.isInitialized) return
        _status.value = currentStatus()
    }

    /** True on unmetered (Wi-Fi/ethernet) connectivity — gate large downloads. */
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
     * Kick off the download in a foreground service after gating on connectivity
     * (Wi-Fi or cellular by default) and a storage/RAM preflight. Failures surface
     * as [ModelStatus.Failed] with a user-facing reason.
     */
    fun start(
        tier: SunnyModelTier,
        allowMetered: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!SunnyAccessPolicy.canDownloadLocal(tier, SubscriptionEntitlements.accessEntitlement(), nowMillis)) {
            _status.value = ModelStatus.Failed(
                when (tier) {
                    SunnyModelTier.SUNNY_MOE -> "Sunny MoE download requires verified Pro access."
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
        startPack(pack, allowMetered)
    }

    /** Keeps adb/developer model setup usable without weakening release access checks. */
    fun startCurrentPackForDebug(allowMetered: Boolean = true) {
        check(BuildConfig.DEBUG) { "The development download bypass is debug-only." }
        startPack(ModelPackCatalog.sunnyMoe, allowMetered)
    }

    private fun startPack(pack: ModelPack, allowMetered: Boolean) {
        if (!ModelSource.canAccess(pack.tier)) {
            _status.value = if (!ModelSource.isConfigured) {
                ModelStatus.NotConfigured
            } else {
                ModelStatus.Failed("The Sunny MoE download source is unavailable.")
            }
            return
        }
        if (
            ModelSource.privateBetaOriginConfigured &&
            SubscriptionEntitlements.modelDownloads.value?.isValid() != true &&
            com.sunny.skin.data.BetaCredentialStore(appContext).inferenceToken.isBlank()
        ) {
            _status.value = ModelStatus.Failed(
                "Enter the private beta access token in Settings before downloading Sunny MoE.",
            )
            return
        }
        if (_status.value is ModelStatus.Downloading) return
        if (!hasInternetConnection()) {
            _status.value = ModelStatus.Failed(
                "Connect to Wi-Fi or mobile data to download ${pack.tier.displayName}.",
            )
            return
        }
        if (!allowMetered && !isUnmetered()) {
            _status.value = ModelStatus.Failed(
                "Connect to Wi-Fi to download ${pack.tier.displayName} (${formatBytes(pack.totalBytes)}).",
            )
            return
        }
        when (val p = DownloadPreflight.check(appContext, pack)) {
            is Preflight.Blocked -> { _status.value = ModelStatus.Failed(p.reason); return }
            Preflight.Ok -> {}
        }
        progressEstimator.reset()
        _status.value = ModelStatus.Downloading(pack.tier, 0, pack.totalBytes)
        ModelDownloadService.start(appContext, pack.tier)
    }

    fun cancel() = ModelDownloadService.cancel(appContext)

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

    private fun currentStatus(): ModelStatus = when {
        ModelProvider.localModelAvailable(appContext) -> ModelStatus.Ready
        ModelProvider.packPresent(appContext, SunnyModelTier.SUNNY_MOE) &&
            !SunnyAccessPolicy.canUse(
                SunnyModelTier.SUNNY_MOE,
                SubscriptionEntitlements.accessEntitlement(),
                System.currentTimeMillis(),
            ) -> ModelStatus.Failed("Sunny MoE is installed, but Pro access is required to use it.")
        ModelProvider.weightsPresent(appContext) -> ModelStatus.Failed(
            "The Sunny MoE pack is installed, but its native runtime is unavailable.",
        )
        !ModelSource.isConfigured -> ModelStatus.NotConfigured
        else -> ModelStatus.Idle
    }

    private fun formatBytes(bytes: Long): String = if (bytes < 100_000_000L) {
        "%.1f MB".format(bytes / 1_000_000.0)
    } else {
        "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
