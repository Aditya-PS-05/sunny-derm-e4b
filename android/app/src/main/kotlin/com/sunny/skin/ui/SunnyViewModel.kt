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
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.inference.download.ModelStatus
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
    data object ModelUnavailable : AnalysisState
}

enum class RecheckResult { SAVED, UNREADABLE, MODEL_UNAVAILABLE }

/** Seeds the next capture from the guided full-body flow: preset body part + a
 *  framing hint shown over the camera so photos stay consistent for alignment. */
data class CapturePreset(val bodyPart: BodyPart, val poseHint: String)

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

/**
 * Habit/retention signal derived purely from the observation timestamps — no
 * extra storage. A "week" is a rolling 7-day window back from now; the streak is
 * the run of consecutive weeks with at least one photo, ending at this week (or
 * last week, so it isn't broken the instant a new week starts).
 */
data class HabitStats(
    val currentStreakWeeks: Int = 0,
    val activeThisWeek: Boolean = false,
    val weeklyCounts: List<Int> = List(WEEKS) { 0 }, // oldest → newest (this week last)
    val totalObservations: Int = 0,
) {
    companion object { const val WEEKS = 10 }
}

class SunnyViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app.applicationContext
    private val repo = (app as SunnyApp).repository
    val settings = SettingsStore(app)

    val modelAvailable: StateFlow<Boolean> = ModelDownloadManager.status
        .map { it is ModelStatus.Ready }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ModelProvider.realModelAvailable(appCtx),
        )

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

    val habit: StateFlow<HabitStats> = repo.scans
        .map { list -> computeHabit(list.flatMap { s -> s.observations.map { it.capturedAt } }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitStats())

    private val _capture = MutableStateFlow(CaptureState())
    val capture: StateFlow<CaptureState> = _capture.asStateFlow()

    // ---- Guided full-body capture ----
    private val _capturePreset = MutableStateFlow<CapturePreset?>(null)
    val capturePreset: StateFlow<CapturePreset?> = _capturePreset.asStateFlow()

    /** Begin capturing a specific body zone with a framing hint (guided flow). */
    fun beginGuidedCapture(bodyPart: BodyPart, poseHint: String) {
        _capturePreset.value = CapturePreset(bodyPart, poseHint)
    }

    // ---- App-lock PIN ----
    private val _pinEnabled = MutableStateFlow(settings.hasPin())
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    fun setPin(pin: String) { settings.setPin(pin); _pinEnabled.value = true }
    fun clearPin() { settings.clearPin(); _pinEnabled.value = false }

    // ---- Reminders ----
    private val reminderStore = com.sunny.skin.reminder.ReminderStore(appCtx)
    val reminders: StateFlow<List<com.sunny.skin.reminder.Reminder>> = reminderStore.reminders

    // ---- ABCDE self-check (per scan; educational, no interpretation) ----
    private val abcdeStore = com.sunny.skin.data.AbcdeStore(appCtx)

    fun abcde(scanId: String): Map<com.sunny.skin.data.AbcdeItem, com.sunny.skin.data.AbcdeAnswer> =
        abcdeStore.get(scanId)

    fun setAbcde(
        scanId: String,
        item: com.sunny.skin.data.AbcdeItem,
        answer: com.sunny.skin.data.AbcdeAnswer,
    ) = abcdeStore.set(scanId, item, answer)

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
     * building the timeline that powers History and Compare.
     */
    fun addRecheck(scanId: String, bitmap: Bitmap, onResult: (RecheckResult) -> Unit) {
        viewModelScope.launch {
            when (val r = describe(bitmap)) {
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
                    onResult(RecheckResult.SAVED)
                }
                DescribeResult.Unreadable -> onResult(RecheckResult.UNREADABLE)
                DescribeResult.ModelUnavailable -> onResult(RecheckResult.MODEL_UNAVAILABLE)
            }
        }
    }

    // ---- Edit an existing scan ----

    /** Run the installed on-device model, failing closed if it cannot start. */
    suspend fun runDescribe(bitmap: Bitmap): DescribeResult = describe(bitmap)

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
        // React to a completed install even when this ViewModel existed beforehand.
        viewModelScope.launch {
            ModelDownloadManager.status.collect { status ->
                if (status is ModelStatus.Ready) {
                    runCatching { ModelProvider.describer(appCtx)?.warmUp() }
                        .onFailure { markModelUnavailable() }
                }
            }
        }
    }

    fun scan(scanId: String) = repo.scan(scanId)

    /** Display name for Settings › About › AI Model. */
    fun modelName(): String =
        if (modelAvailable.value) "Sunny-Gemma4-E4B" else "Not installed"

    // ---- Capture flow ----

    fun startCapture(bitmap: Bitmap) {
        val preset = _capturePreset.value
        val initialAnalysis = if (modelAvailable.value) {
            AnalysisState.Running
        } else {
            AnalysisState.ModelUnavailable
        }
        _capture.value = CaptureState(
            bitmap = bitmap,
            bodyPart = preset?.bodyPart ?: CaptureState().bodyPart,
            analysis = initialAnalysis,
        )
        if (initialAnalysis is AnalysisState.Running) runAnalysis(bitmap)
    }

    private fun runAnalysis(bitmap: Bitmap) {
        viewModelScope.launch {
            val result = describe(bitmap)
            _capture.value = _capture.value.copy(
                analysis = when (result) {
                    is DescribeResult.Success -> AnalysisState.Ready(result)
                    DescribeResult.Unreadable -> AnalysisState.Unreadable
                    DescribeResult.ModelUnavailable -> AnalysisState.ModelUnavailable
                },
            )
        }
    }

    fun retryAnalysis() {
        val bmp = _capture.value.bitmap ?: return
        if (!modelAvailable.value) {
            _capture.value = _capture.value.copy(analysis = AnalysisState.ModelUnavailable)
            return
        }
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
            _capturePreset.value = null
            onSaved(scanId)
        }
    }

    fun discardCapture() { _capture.value = CaptureState(); _capturePreset.value = null }

    fun deleteScan(scan: ScanWithObservations) {
        viewModelScope.launch { repo.deleteScan(scan) }
        abcdeStore.clear(scan.scan.id)
    }

    private suspend fun describe(bitmap: Bitmap): DescribeResult {
        val current = ModelProvider.describer(appCtx) ?: return DescribeResult.ModelUnavailable
        return runCatching { current.describe(bitmap) }
            .getOrElse {
                markModelUnavailable()
                DescribeResult.ModelUnavailable
            }
    }

    private fun markModelUnavailable() {
        ModelProvider.reset()
        ModelDownloadManager.publishFailed(
            "The installed AI model could not start. Reinstall it before scanning.",
        )
    }

    /** Bucket capture times into rolling 7-day windows and derive the streak. */
    private fun computeHabit(times: List<Long>): HabitStats {
        val now = System.currentTimeMillis()
        val counts = IntArray(HabitStats.WEEKS)
        val active = BooleanArray(160) // weeks-ago activity flags (plenty of history)
        times.forEach { t ->
            if (t in 0..now) {
                val w = ((now - t) / WEEK_MS).toInt()
                if (w < HabitStats.WEEKS) counts[HabitStats.WEEKS - 1 - w]++
                if (w < active.size) active[w] = true
            }
        }
        // Start counting from this week if it's active, otherwise from last week, so
        // the streak survives the first days of a fresh week before the next photo.
        val start = if (active[0]) 0 else 1
        var streak = 0
        var w = start
        while (w < active.size && active[w]) { streak++; w++ }
        return HabitStats(
            currentStreakWeeks = streak,
            activeThisWeek = active[0],
            weeklyCounts = counts.toList(),
            totalObservations = times.size,
        )
    }

    private companion object { const val WEEK_MS = 7L * 24 * 60 * 60 * 1000 }
}
