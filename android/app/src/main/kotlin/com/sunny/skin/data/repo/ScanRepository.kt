package com.sunny.skin.data.repo

import android.content.Context
import com.sunny.skin.data.ImageStore
import com.sunny.skin.data.db.AnalysisColumns
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.db.ScanEntity
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.db.SunnyDatabase
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.data.model.ApproximateMeasurement
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.CaptureAlignment
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Single source of truth for scans and observations. All reads are reactive
 * Flows so the UI updates as soon as a scan is saved or deleted.
 */
class ScanRepository(context: Context) {
    private val app = context.applicationContext
    private val dao = SunnyDatabase.get(context).scanDao()
    private val images = ImageStore(context)

    val scans: Flow<List<ScanWithObservations>> = dao.observeScans()
    val observationCount: Flow<Int> = dao.observeObservationCount()

    fun scan(scanId: String): Flow<ScanWithObservations?> = dao.observeScan(scanId)

    suspend fun allScansOnce(): List<ScanWithObservations> = dao.allScansOnce()

    /**
     * Persist a brand-new scan from a review screen: writes the photo to
     * private storage, creates the scan and its first observation.
     */
    suspend fun createScan(
        imagePath: String,
        bodyPart: BodyPart,
        scanType: ScanType,
        analysis: Analysis,
        modelVersion: String,
        rawOutput: String,
        now: Long,
        measurement: ApproximateMeasurement? = null,
        alignment: CaptureAlignment? = null,
    ): String {
        val scanId = UUID.randomUUID().toString()
        val name = "${scanType.prefix} - ${bodyPart.label}"
        dao.upsertScan(
            ScanEntity(
                id = scanId,
                name = name,
                bodyPart = bodyPart,
                scanType = scanType,
                createdAt = now,
                updatedAt = now,
            )
        )
        addObservation(
            scanId,
            imagePath,
            analysis,
            modelVersion,
            rawOutput,
            now,
            promoteToTracked = false,
            measurement = measurement,
            alignment = alignment,
        )
        return scanId
    }

    /** Add a re-check capture to an existing tracked scan (the retention loop). */
    suspend fun addObservation(
        scanId: String,
        imagePath: String,
        analysis: Analysis,
        modelVersion: String,
        rawOutput: String,
        now: Long,
        promoteToTracked: Boolean = true,
        measurement: ApproximateMeasurement? = null,
        alignment: CaptureAlignment? = null,
    ) {
        dao.insertObservation(
            ObservationEntity(
                id = UUID.randomUUID().toString(),
                scanId = scanId,
                capturedAt = now,
                imagePath = imagePath,
                analysis = AnalysisColumns.from(analysis),
                modelVersion = modelVersion,
                rawOutput = rawOutput,
                approximateSizeMm = measurement?.takeIf { it.isValid }?.approximateSizeMm,
                sizeReferenceMm = measurement?.takeIf { it.isValid }?.referenceSizeMm,
                sizeReferenceSpan = measurement?.takeIf { it.isValid }?.referenceSpan,
                sizeTargetSpan = measurement?.takeIf { it.isValid }?.targetSpan,
                alignmentScore = alignment?.score,
                alignmentTranslationX = alignment?.translationX,
                alignmentTranslationY = alignment?.translationY,
                alignmentScale = alignment?.scale,
                alignmentRotationDegrees = alignment?.rotationDegrees,
            )
        )
        if (promoteToTracked) {
            dao.updateScanType(scanId, ScanType.TRACKED, now)
        } else {
            dao.touchScan(scanId, now)
        }
    }

    /** Rename a scan (edit flow). */
    suspend fun renameScan(scanId: String, name: String, now: Long) =
        dao.renameScan(scanId, name.trim().ifEmpty { "Scan" }, now)

    suspend fun updateNotes(scanId: String, notes: String, now: Long) =
        dao.updateNotes(scanId, notes.trim().take(2_000), now)

    /**
     * Replace an existing observation's photo and/or analysis in place (edit
     * flow). Keeps the original id and capture date so timeline order is stable;
     * deletes the old image file if the photo changed.
     */
    suspend fun replaceObservation(
        existing: ObservationEntity,
        newImagePath: String?,
        analysis: Analysis,
        modelVersion: String,
        rawOutput: String,
        now: Long,
    ) {
        val finalPath = newImagePath ?: existing.imagePath
        if (newImagePath != null && newImagePath != existing.imagePath) {
            images.delete(existing.imagePath)
        }
        dao.insertObservation(
            existing.copy(
                imagePath = finalPath,
                analysis = AnalysisColumns.from(analysis),
                modelVersion = modelVersion,
                rawOutput = rawOutput,
            )
        )
        dao.touchScan(existing.scanId, now)
    }

    suspend fun deleteScan(scan: ScanWithObservations) {
        scan.observations.forEach { images.delete(it.imagePath) }
        dao.deleteScan(scan.scan.id)
    }

    suspend fun deleteAll() {
        SunnyDatabase.enableSecureDelete(app)
        dao.deleteAllScans()
        images.deleteAll()
        SunnyDatabase.purgeDeletedPages(app)
    }

    fun imageStore(): ImageStore = images
}
