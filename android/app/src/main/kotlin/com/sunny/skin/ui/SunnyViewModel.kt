package com.sunny.skin.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunny.skin.SunnyApp
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.inference.DescribeResult
import com.sunny.skin.inference.ModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the in-progress capture → analysis → review flow. */
data class CaptureState(
    val bitmap: Bitmap? = null,
    val bodyPart: BodyPart = BodyPart.SHOULDER,
    val scanType: ScanType = ScanType.SINGLE,
    val analysis: AnalysisState = AnalysisState.Idle,
)

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Running : AnalysisState
    data class Ready(val result: DescribeResult.Success) : AnalysisState
    data object Unreadable : AnalysisState
}

/** Aggregate stats for the Overview cards + body coverage. */
data class OverviewStats(
    val photos: Int = 0,
    val scanned: Int = 0,
    val updates: Int = 0,
    val scannedZones: Set<com.sunny.skin.data.model.BodyZone> = emptySet(),
) {
    val coverage: Float get() =
        scannedZones.size.toFloat() / com.sunny.skin.data.model.BodyZone.entries.size
}

class SunnyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as SunnyApp).repository
    private val describer = ModelProvider.describer(app)
    val settings = SettingsStore(app)

    val scans: StateFlow<List<ScanWithObservations>> =
        repo.scans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<OverviewStats> = repo.scans
        .map { list ->
            OverviewStats(
                photos = list.sumOf { it.observations.size },
                scanned = list.size,
                updates = list.count { it.observations.size > 1 },
                scannedZones = list.map { it.scan.bodyPart.zone }.toSet(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewStats())

    private val _capture = MutableStateFlow(CaptureState())
    val capture: StateFlow<CaptureState> = _capture.asStateFlow()

    // ---- App-lock PIN ----
    private val _pinEnabled = MutableStateFlow(settings.hasPin())
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    fun setPin(pin: String) { settings.setPin(pin); _pinEnabled.value = true }
    fun clearPin() { settings.clearPin(); _pinEnabled.value = false }

    // ---- Reminders ----
    private val appCtx = app.applicationContext
    private val reminderStore = com.sunny.skin.reminder.ReminderStore(appCtx)
    val reminders: StateFlow<List<com.sunny.skin.reminder.Reminder>> = reminderStore.reminders

    private val dayMs = 24L * 60 * 60 * 1000

    /** One-shot "re-check this spot" reminder for a saved scan. */
    fun scheduleScanReminder(scanId: String, bodyLabel: String, delayDays: Int) {
        val r = com.sunny.skin.reminder.Reminder(
            id = java.util.UUID.randomUUID().toString(),
            scanId = scanId,
            title = "Re-check your $bodyLabel spot",
            body = "Open Sunny and photograph it again so you can compare and track any change.",
            triggerAt = System.currentTimeMillis() + delayDays * dayMs,
            intervalDays = 0,
        )
        reminderStore.upsert(r)
        com.sunny.skin.reminder.ReminderScheduler.schedule(appCtx, r)
    }

    /** Enable/replace or disable the recurring "regular skin check" reminder. */
    fun setRecurringReminder(enabled: Boolean, intervalDays: Int) {
        val id = com.sunny.skin.reminder.Reminder.RECURRING_ID
        if (!enabled) {
            reminderStore.remove(id)
            com.sunny.skin.reminder.ReminderScheduler.cancel(appCtx, id)
            return
        }
        val r = com.sunny.skin.reminder.Reminder(
            id = id,
            scanId = null,
            title = "Time for a skin check",
            body = "Take a few minutes to look over your skin and photograph anything new or changed.",
            triggerAt = System.currentTimeMillis() + intervalDays * dayMs,
            intervalDays = intervalDays,
        )
        reminderStore.upsert(r)
        com.sunny.skin.reminder.ReminderScheduler.schedule(appCtx, r)
    }

    fun cancelReminder(id: String) {
        reminderStore.remove(id)
        com.sunny.skin.reminder.ReminderScheduler.cancel(appCtx, id)
    }

    // ---- Re-check: add a follow-up photo to an existing scan ----
    /**
     * Analyse [bitmap] and append it as a new dated observation on [scanId],
     * building the timeline that powers History and Compare. [onResult] gets
     * true on success, false if the image was unreadable.
     */
    fun addRecheck(scanId: String, bitmap: Bitmap, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = describer.describe(bitmap)) {
                is DescribeResult.Success -> {
                    val path = repo.imageStore().save(bitmap)
                    repo.addObservation(
                        scanId = scanId,
                        imagePath = path,
                        analysis = r.analysis,
                        modelVersion = r.modelVersion,
                        rawOutput = r.rawOutput,
                        now = System.currentTimeMillis(),
                    )
                    onResult(true)
                }
                DescribeResult.Unreadable -> onResult(false)
            }
        }
    }

    // ---- Edit an existing scan ----

    /** Run the on-device model on a bitmap (used by the edit "redo diagnosis"). */
    suspend fun runDescribe(bitmap: Bitmap): DescribeResult = describer.describe(bitmap)

    /**
     * Persist edits to a scan: rename it and replace its latest observation's
     * photo/analysis. [newBitmap] non-null means the photo changed.
     */
    fun saveScanEdit(
        scanId: String,
        existing: com.sunny.skin.data.db.ObservationEntity,
        name: String,
        newBitmap: Bitmap?,
        analysis: com.sunny.skin.data.model.Analysis,
        modelVersion: String,
        rawOutput: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.renameScan(scanId, name, now)
            val newPath = newBitmap?.let { repo.imageStore().save(it) }
            repo.replaceObservation(existing, newPath, analysis, modelVersion, rawOutput, now)
            onDone()
        }
    }

    init {
        // Warm the model early so the first real scan doesn't pay the load cost (N-02).
        viewModelScope.launch { runCatching { describer.warmUp() } }
    }

    fun scan(scanId: String) = repo.scan(scanId)

    /** Display name for Settings › About › AI Model. */
    fun modelName(): String =
        if (ModelProvider.usingRealModel) "Sunny-Gemma4-E4B" else "Sunny-Gemma4-E4B (demo)"

    // ---- Capture flow ----

    fun startCapture(bitmap: Bitmap) {
        _capture.value = CaptureState(bitmap = bitmap, analysis = AnalysisState.Running)
        runAnalysis(bitmap)
    }

    private fun runAnalysis(bitmap: Bitmap) {
        viewModelScope.launch {
            val result = describer.describe(bitmap)
            _capture.value = _capture.value.copy(
                analysis = when (result) {
                    is DescribeResult.Success -> AnalysisState.Ready(result)
                    DescribeResult.Unreadable -> AnalysisState.Unreadable
                },
            )
        }
    }

    fun retryAnalysis() {
        val bmp = _capture.value.bitmap ?: return
        _capture.value = _capture.value.copy(analysis = AnalysisState.Running)
        runAnalysis(bmp)
    }

    fun setBodyPart(part: BodyPart) { _capture.value = _capture.value.copy(bodyPart = part) }
    fun setScanType(type: ScanType) { _capture.value = _capture.value.copy(scanType = type) }

    /** Persist the reviewed scan. Returns the new scanId, or null if not ready. */
    fun saveCapture(now: Long, onSaved: (String) -> Unit) {
        val state = _capture.value
        val bitmap = state.bitmap ?: return
        val ready = state.analysis as? AnalysisState.Ready ?: return
        viewModelScope.launch {
            val path = repo.imageStore().save(bitmap)
            val scanId = repo.createScan(
                imagePath = path,
                bodyPart = state.bodyPart,
                scanType = state.scanType,
                analysis = ready.result.analysis,
                modelVersion = ready.result.modelVersion,
                rawOutput = ready.result.rawOutput,
                now = now,
            )
            _capture.value = CaptureState()
            onSaved(scanId)
        }
    }

    fun discardCapture() { _capture.value = CaptureState() }

    fun deleteScan(scan: ScanWithObservations) {
        viewModelScope.launch { repo.deleteScan(scan) }
    }
}
