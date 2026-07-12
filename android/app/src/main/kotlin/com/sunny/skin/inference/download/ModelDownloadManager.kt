package com.sunny.skin.inference.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sunny.skin.inference.ModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coarse state of the on-device model install, surfaced to the setup UI. */
sealed interface ModelStatus {
    data object NotConfigured : ModelStatus          // ModelSource.baseUrl not set
    data object Idle : ModelStatus                    // configured, nothing downloaded yet
    data class Downloading(val done: Long, val total: Long) : ModelStatus {
        val fraction: Float get() = if (total == 0L) 0f else done.toFloat() / total
    }
    data object Verifying : ModelStatus
    data object Ready : ModelStatus                   // both files present & verified
    data class Failed(val message: String) : ModelStatus
}

/**
 * Process-wide manager for the first-run model download. Survives navigation
 * (its own scope), downloads both assets sequentially with a combined progress
 * bar, and on success resets [ModelProvider] so the next request creates a fresh
 * real llama.cpp session.
 */
object ModelDownloadManager {
    private lateinit var appContext: Context

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
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

    /**
     * Kick off the download in a foreground service after gating on connectivity
     * (Wi-Fi unless [allowMetered]) and a storage/RAM preflight. Failures surface
     * as [ModelStatus.Failed] with a user-facing reason.
     */
    fun start(allowMetered: Boolean = false) {
        if (!ModelSource.isConfigured) { _status.value = ModelStatus.NotConfigured; return }
        if (_status.value is ModelStatus.Downloading) return
        if (!allowMetered && !isUnmetered()) {
            _status.value = ModelStatus.Failed("Connect to Wi-Fi to download the ~6 GB model.")
            return
        }
        when (val p = DownloadPreflight.check(appContext)) {
            is Preflight.Blocked -> { _status.value = ModelStatus.Failed(p.reason); return }
            Preflight.Ok -> {}
        }
        _status.value = ModelStatus.Downloading(0, ModelAsset.totalBytes)
        ModelDownloadService.start(appContext)
    }

    fun cancel() = ModelDownloadService.cancel(appContext)

    // --- Called by ModelDownloadService to mirror progress into the UI flow ---
    fun publishDownloading(done: Long, total: Long) {
        _status.value = ModelStatus.Downloading(done, total)
    }
    fun publishVerifying() { _status.value = ModelStatus.Verifying }
    fun activateInstalledModel() {
        ModelProvider.reset()
        _status.value = currentStatus()
    }
    fun publishFailed(message: String) { _status.value = ModelStatus.Failed(message) }
    fun publishIdleIfDownloading() {
        if (_status.value is ModelStatus.Downloading) _status.value = ModelStatus.Idle
    }

    private fun currentStatus(): ModelStatus = when {
        ModelProvider.realModelAvailable(appContext) -> ModelStatus.Ready
        ModelProvider.weightsPresent(appContext) -> ModelStatus.Failed(
            "Model files were found, but this app build does not include a working native AI runtime.",
        )
        !ModelSource.isConfigured -> ModelStatus.NotConfigured
        else -> ModelStatus.Idle
    }
}
