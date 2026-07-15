package com.sunny.skin.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunny.skin.SunnyApp
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.data.CheckSessionStatus
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.ApproximateMeasurement
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.CaptureAlignment
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sunny.skin.util.AlignmentResult

/** UI state for the in-progress capture → analysis → review flow. */
data class CaptureState(
    val bitmap: Bitmap? = null,
    val bodyPart: BodyPart = BodyPart.SHOULDER,
    val scanType: ScanType = ScanType.SINGLE,
    val analysis: AnalysisState = AnalysisState.Idle,
    val targetScanId: String? = null,
    val referenceImagePath: String? = null,
    val measurement: ApproximateMeasurement? = null,
    val alignment: CaptureAlignment? = null,
    val checkSessionId: String? = null,
)

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val phase: AnalysisPhase) : AnalysisState
    data class Ready(val result: DescribeResult.Success) : AnalysisState
    data object Unreadable : AnalysisState
    data object ModelUnavailable : AnalysisState
    data object Cancelled : AnalysisState
    data class PoorQuality(val issue: com.sunny.skin.util.PhotoQualityIssue) : AnalysisState
}

enum class AnalysisPhase { PREPARING, REMOTE, ON_DEVICE }

enum class RecheckResult { SAVED, UNREADABLE, MODEL_UNAVAILABLE }
enum class ContributionStatus { IDLE, UPLOADING, SENT, FAILED }

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

    private val appCtx: android.content.Context
        get() = getApplication<Application>().applicationContext
    private val repo = (app as SunnyApp).repository
    val settings = SettingsStore(app)

    val modelAvailable: StateFlow<Boolean> = ModelDownloadManager.status
        .map { it is ModelStatus.Ready || ModelProvider.realModelAvailable(appCtx) }
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
    private val pendingCaptureStore = com.sunny.skin.data.PendingCaptureStore(appCtx)
    private var analysisJob: Job? = null

    // ---- Guided full-body capture ----
    private val _capturePreset = MutableStateFlow<CapturePreset?>(null)
    val capturePreset: StateFlow<CapturePreset?> = _capturePreset.asStateFlow()

    /** Begin capturing a specific body zone with a framing hint (guided flow). */
    fun beginGuidedCapture(bodyPart: BodyPart, poseHint: String) {
        clearPendingCapture()
        _capture.value = CaptureState(bodyPart = bodyPart)
        _capturePreset.value = CapturePreset(bodyPart, poseHint)
    }

    /** Prepare a follow-up capture while keeping the previous encrypted photo as a camera guide. */
    fun beginRecheckCapture(
        scanId: String,
        bodyPart: BodyPart,
        referenceImagePath: String,
        checkSessionId: String? = null,
    ) {
        clearPendingCapture()
        _capture.value = CaptureState(
            bodyPart = bodyPart,
            scanType = ScanType.TRACKED,
            targetScanId = scanId,
            referenceImagePath = referenceImagePath,
            checkSessionId = checkSessionId,
        )
        _capturePreset.value = CapturePreset(
            bodyPart = bodyPart,
            poseHint = "Match the previous photo's distance and angle.",
        )
    }

    // ---- App-lock PIN ----
    private val _pinEnabled = MutableStateFlow(settings.hasPin())
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    fun setPin(pin: String) { settings.setPin(pin); _pinEnabled.value = true }
    fun clearPin() { settings.clearPin(); _pinEnabled.value = false }

    // ---- Beta "improve Sunny" opt-in (contribute scans + corrections) ----
    private val _improveSunny = MutableStateFlow(settings.improveSunny)
    val improveSunny: StateFlow<Boolean> = _improveSunny.asStateFlow()
    fun setImproveSunny(v: Boolean) { settings.improveSunny = v; _improveSunny.value = v }
    private val _contributionStatus = MutableStateFlow(ContributionStatus.IDLE)
    val contributionStatus: StateFlow<ContributionStatus> = _contributionStatus.asStateFlow()

    // ---- Beta "analysis source" switch (server vs on-device) ----
    /** True only in a beta build that has a server URL baked in; hides the row otherwise. */
    val serverModeAvailable: Boolean = ModelProvider.serverApiUrl.isNotBlank()
    private val _useServerInference = MutableStateFlow(settings.useServerInference)
    val useServerInference: StateFlow<Boolean> = _useServerInference.asStateFlow()

    /** Switch analysis engine at runtime; the next scan resolves the new source. */
    fun setUseServerInference(v: Boolean) {
        settings.useServerInference = v
        _useServerInference.value = v
        ModelProvider.reset()          // drop the cached describer so the next scan re-resolves
        ModelDownloadManager.refresh() // re-emit status so modelAvailable re-evaluates
    }

    /** Upload one contribution off the UI thread — only when the user opted in. */
    private fun contribute(
        bitmap: Bitmap,
        modelOutput: com.sunny.skin.data.model.Analysis,
        corrected: com.sunny.skin.data.model.Analysis?,
        bodyZone: String,
    ) {
        if (!settings.improveSunny) return
        viewModelScope.launch {
            _contributionStatus.value = ContributionStatus.UPLOADING
            val sent = com.sunny.skin.data.ContributionUploader.submit(
                bitmap,
                modelOutput,
                corrected,
                bodyZone,
            )
            _contributionStatus.value = if (sent) ContributionStatus.SENT else ContributionStatus.FAILED
        }
    }

    // ---- Reminders ----
    private val reminderStore = com.sunny.skin.reminder.ReminderStore(appCtx)
    val reminders: StateFlow<List<com.sunny.skin.reminder.Reminder>> = reminderStore.reminders

    // ---- Resumable photo-check session ----
    private val checkSessionStore = com.sunny.skin.data.CheckSessionStore(appCtx)
    val checkSession: StateFlow<com.sunny.skin.data.CheckSession?> = checkSessionStore.active

    fun startCheckSession(): Boolean {
        val scanIds = scans.value
            .filter { it.latest != null }
            .sortedBy { it.scan.bodyPart.ordinal }
            .map { it.scan.id }
        val existing = checkSession.value
        if (existing != null && existing.items.any { it.scanId in scanIds }) return true
        if (existing != null) checkSessionStore.clear()
        return checkSessionStore.start(scanIds) != null
    }

    fun skipCheckSessionItem(scanId: String) =
        checkSessionStore.setStatus(scanId, CheckSessionStatus.SKIPPED)

    fun finishCheckSession() = checkSessionStore.clear()

    fun beginCheckSessionRecheck(scanId: String): Boolean {
        val scan = scans.value.firstOrNull { it.scan.id == scanId } ?: return false
        val latest = scan.latest ?: return false
        val sessionId = checkSession.value?.id ?: return false
        beginRecheckCapture(
            scanId = scanId,
            bodyPart = scan.scan.bodyPart,
            referenceImagePath = latest.imagePath,
            checkSessionId = sessionId,
        )
        return true
    }

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
    private val hourMs = 60L * 60 * 1000

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
    fun setRecurringReminder(enabled: Boolean, intervalHours: Int) {
        val id = com.sunny.skin.reminder.Reminder.RECURRING_ID
        if (!enabled) {
            reminderStore.remove(id)
            com.sunny.skin.reminder.ReminderScheduler.cancel(appCtx, id)
            return
        }
        val safeHours = intervalHours.coerceIn(1, 24 * 365)
        val r = com.sunny.skin.reminder.Reminder(
            id = id,
            scanId = null,
            title = "Time for a skin check",
            body = "Take a few minutes to look over your skin and photograph anything new or changed.",
            triggerAt = System.currentTimeMillis() + safeHours * hourMs,
            intervalDays = safeHours / 24,
            intervalHours = safeHours,
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

    /** Run the configured inference provider, failing closed if it cannot start. */
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
        bodyZone: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.renameScan(scanId, name, now)
            val newPath = newBitmap?.let { repo.imageStore().save(it) }
            repo.replaceObservation(existing, newPath, analysis, modelVersion, rawOutput, now)
            // An edit is a correction signal: the model's original output vs the
            // user's saved fields, on a real photo — the highest-value training data.
            if (settings.improveSunny) {
                val bmp = newBitmap ?: repo.imageStore().decryptToBitmap(existing.imagePath)
                if (bmp != null) {
                    val labels = com.sunny.skin.data.ContributionLabeler.from(rawOutput, analysis)
                    contribute(
                        bmp,
                        labels.modelOutput,
                        corrected = labels.correctedOutput,
                        bodyZone = bodyZone,
                    )
                }
            }
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
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { pendingCaptureStore.restore() }
                ?: return@launch
            if (_capture.value.bitmap != null) return@launch
            val targetExists = restored.targetScanId == null ||
                repo.allScansOnce().any { it.scan.id == restored.targetScanId }
            val qualityIssue = com.sunny.skin.util.PhotoQuality.assess(restored.bitmap)
            _capture.value = CaptureState(
                bitmap = restored.bitmap,
                bodyPart = restored.bodyPart,
                scanType = restored.scanType,
                analysis = when {
                    qualityIssue != null -> AnalysisState.PoorQuality(qualityIssue)
                    modelAvailable.value -> AnalysisState.Running(AnalysisPhase.PREPARING)
                    else -> AnalysisState.ModelUnavailable
                },
                targetScanId = restored.targetScanId.takeIf { targetExists },
                referenceImagePath = restored.referenceImagePath.takeIf { targetExists },
                measurement = restored.measurement,
                alignment = restored.alignment,
                checkSessionId = restored.checkSessionId,
            )
            if (_capture.value.analysis is AnalysisState.Running) runAnalysis(restored.bitmap)
        }
    }

    fun scan(scanId: String) = repo.scan(scanId)

    fun setScanNotes(scanId: String, notes: String) {
        viewModelScope.launch { repo.updateNotes(scanId, notes, System.currentTimeMillis()) }
    }

    /** Display name for Settings › About › AI Model. */
    fun modelName(): String =
        if (modelAvailable.value) "Sunny-Gemma4-E4B" else "Not installed"

    // ---- Capture flow ----

    /** Start the ordinary capture chooser without carrying a cancelled re-check target forward. */
    fun beginNewCapture() {
        clearPendingCapture()
        _capture.value = CaptureState()
        _capturePreset.value = null
    }

    fun startCapture(bitmap: Bitmap, alignmentResult: AlignmentResult? = null) {
        val preset = _capturePreset.value
        val pending = _capture.value
        val qualityIssue = com.sunny.skin.util.PhotoQuality.assess(bitmap)
        val initialAnalysis = if (qualityIssue != null) {
            AnalysisState.PoorQuality(qualityIssue)
        } else if (modelAvailable.value) {
            AnalysisState.Running(AnalysisPhase.PREPARING)
        } else {
            AnalysisState.ModelUnavailable
        }
        _capture.value = CaptureState(
            bitmap = bitmap,
            bodyPart = preset?.bodyPart ?: pending.bodyPart,
            scanType = pending.scanType,
            analysis = initialAnalysis,
            targetScanId = pending.targetScanId,
            referenceImagePath = pending.referenceImagePath,
            measurement = pending.measurement,
            alignment = alignmentResult?.let {
                CaptureAlignment(
                    score = it.score,
                    translationX = it.transform.tx,
                    translationY = it.transform.ty,
                    scale = it.transform.scale,
                    rotationDegrees = it.transform.rotationDeg,
                )
            } ?: pending.alignment,
            checkSessionId = pending.checkSessionId,
        )
        runCatching { pendingCaptureStore.save(
            com.sunny.skin.data.PendingCapture(
                bitmap = bitmap,
                bodyPart = _capture.value.bodyPart,
                scanType = _capture.value.scanType,
                targetScanId = _capture.value.targetScanId,
                referenceImagePath = _capture.value.referenceImagePath,
                measurement = _capture.value.measurement,
                alignment = _capture.value.alignment,
                checkSessionId = _capture.value.checkSessionId,
            ),
        ) }
        if (initialAnalysis is AnalysisState.Running) runAnalysis(bitmap)
    }

    private fun runAnalysis(bitmap: Bitmap) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _capture.value = _capture.value.copy(
                analysis = AnalysisState.Running(
                    if (ModelProvider.useServer(appCtx)) AnalysisPhase.REMOTE else AnalysisPhase.ON_DEVICE,
                ),
            )
            val result = describe(bitmap)
            currentCoroutineContext().ensureActive()
            _capture.value = _capture.value.copy(
                analysis = when (result) {
                    is DescribeResult.Success -> AnalysisState.Ready(result)
                    DescribeResult.Unreadable -> AnalysisState.Unreadable
                    DescribeResult.ModelUnavailable -> AnalysisState.ModelUnavailable
                },
            )
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        if (_capture.value.analysis is AnalysisState.Running) {
            _capture.value = _capture.value.copy(analysis = AnalysisState.Cancelled)
        }
    }

    fun retryAnalysis() {
        val bmp = _capture.value.bitmap ?: return
        if (!modelAvailable.value) {
            _capture.value = _capture.value.copy(analysis = AnalysisState.ModelUnavailable)
            return
        }
        _capture.value = _capture.value.copy(analysis = AnalysisState.Running(AnalysisPhase.PREPARING))
        runAnalysis(bmp)
    }

    /** Clear only the captured frame so camera re-entry retains re-check alignment context. */
    fun prepareRetake() {
        clearPendingCapture()
        _capture.value = _capture.value.copy(
            bitmap = null,
            analysis = AnalysisState.Idle,
            measurement = null,
            alignment = null,
        )
    }

    fun setBodyPart(part: BodyPart) {
        _capture.value = _capture.value.copy(bodyPart = part)
        persistPendingCapture()
    }
    fun setScanType(type: ScanType) {
        _capture.value = _capture.value.copy(scanType = type)
        persistPendingCapture()
    }

    fun setApproximateMeasurement(measurement: ApproximateMeasurement?) {
        _capture.value = _capture.value.copy(measurement = measurement?.takeIf { it.isValid })
        persistPendingCapture()
    }

    /** Persist the reviewed photo, creating a scan or appending to its re-check target. */
    fun saveCapture(
        now: Long,
        onSaved: (scanId: String, wasRecheck: Boolean, checkSessionId: String?) -> Unit,
    ) {
        val state = _capture.value
        val bitmap = state.bitmap ?: return
        val ready = state.analysis as? AnalysisState.Ready ?: return
        viewModelScope.launch {
            val path = repo.imageStore().save(bitmap)
            val targetScanId = state.targetScanId
            val scanId = if (targetScanId != null) {
                repo.addObservation(
                    scanId = targetScanId,
                    imagePath = path,
                    analysis = ready.result.analysis,
                    modelVersion = ready.result.modelVersion,
                    rawOutput = ready.result.rawOutput,
                    now = now,
                    measurement = state.measurement,
                    alignment = state.alignment,
                )
                targetScanId
            } else {
                repo.createScan(
                    imagePath = path,
                    bodyPart = state.bodyPart,
                    scanType = state.scanType,
                    analysis = ready.result.analysis,
                    modelVersion = ready.result.modelVersion,
                    rawOutput = ready.result.rawOutput,
                    now = now,
                    measurement = state.measurement,
                    alignment = state.alignment,
                )
            }
            contribute(bitmap, ready.result.analysis, corrected = null,
                bodyZone = state.bodyPart.zone.name)
            state.checkSessionId?.let { sessionId ->
                if (checkSession.value?.id == sessionId) {
                    checkSessionStore.setStatus(scanId, CheckSessionStatus.COMPLETED)
                }
            }
            _capture.value = CaptureState()
            _capturePreset.value = null
            clearPendingCapture()
            onSaved(scanId, targetScanId != null, state.checkSessionId)
        }
    }

    fun discardCapture() {
        clearPendingCapture()
        _capture.value = CaptureState()
        _capturePreset.value = null
    }

    fun deleteScan(scan: ScanWithObservations) {
        reminderStore.all().filter { it.scanId == scan.scan.id }.forEach {
            reminderStore.remove(it.id)
            com.sunny.skin.reminder.ReminderScheduler.cancel(appCtx, it.id)
        }
        viewModelScope.launch { repo.deleteScan(scan) }
        checkSessionStore.removeScan(scan.scan.id)
        abcdeStore.clear(scan.scan.id)
    }

    fun exportEncryptedBackup(password: CharArray, onResult: (android.net.Uri?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { com.sunny.skin.data.BackupExporter(appCtx).export(password) }
                .onSuccess { file ->
                    onResult(com.sunny.skin.data.BackupStore(appCtx).shareUri(file), null)
                }
                .onFailure { onResult(null, it.message ?: "Backup could not be created.") }
        }
    }

    fun deleteAllLocalData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            reminderStore.all().forEach {
                com.sunny.skin.reminder.ReminderScheduler.cancel(appCtx, it.id)
            }
            repo.deleteAll()
            abcdeStore.clearAll()
            reminderStore.clear()
            checkSessionStore.clear()
            com.sunny.skin.report.ReportStore(appCtx).deleteAll()
            com.sunny.skin.data.BackupStore(appCtx).deleteAll()
            _capture.value = CaptureState()
            _capturePreset.value = null
            clearPendingCapture()
            onDone()
        }
    }

    private fun persistPendingCapture() {
        val state = _capture.value
        val bitmap = state.bitmap ?: return
        runCatching { pendingCaptureStore.save(
            com.sunny.skin.data.PendingCapture(
                bitmap, state.bodyPart, state.scanType, state.targetScanId, state.referenceImagePath,
                state.measurement, state.alignment, state.checkSessionId,
            ),
        ) }
    }

    private fun clearPendingCapture() {
        analysisJob?.cancel()
        analysisJob = null
        runCatching { pendingCaptureStore.clear() }
    }

    private suspend fun describe(bitmap: Bitmap): DescribeResult {
        val current = runCatching { ModelProvider.describer(appCtx) }
            .getOrElse {
                markModelUnavailable()
                return DescribeResult.ModelUnavailable
            }
            ?: return DescribeResult.ModelUnavailable
        return runCatching { current.describe(bitmap) }
            .getOrElse {
                markModelUnavailable()
                DescribeResult.ModelUnavailable
            }
    }

    private fun markModelUnavailable() {
        val server = ModelProvider.useServer(appCtx)
        ModelProvider.reset()
        ModelDownloadManager.publishFailed(
            com.sunny.skin.AppMode.unavailableMessage(server),
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
