package com.sunny.skin.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.data.model.BodyPart

/**
 * A tracked entity (a "scan" in the UI, a "lesion" in design.md). A SINGLE scan
 * has one observation; a TRACKED scan accumulates observations over time so the
 * timeline/diff can show change (F-11..F-13).
 */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey val id: String,
    val name: String,               // e.g. "Scan - Upper Back" / "Single Scan - Middle Toe"
    val bodyPart: BodyPart,
    val scanType: ScanType,
    val createdAt: Long,
    val updatedAt: Long,
    val notes: String = "",
)

enum class ScanType(val prefix: String) {
    SINGLE("Single Scan"),
    TRACKED("Scan"),
}

/**
 * One dated capture: the photo plus the model's six-field description and the
 * model version that produced it (N-11). [rawOutput] is retained for audit
 * (design.md §5) but never shown as a partial result.
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("scanId")],
)
data class ObservationEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val capturedAt: Long,
    val imagePath: String,          // app-private file path; never leaves device (P-01)
    @Embedded(prefix = "field_") val analysis: AnalysisColumns,
    val modelVersion: String,
    val rawOutput: String,
    val approximateSizeMm: Float? = null,
    val sizeReferenceMm: Float? = null,
    val sizeReferenceSpan: Float? = null,
    val sizeTargetSpan: Float? = null,
    val alignmentScore: Float? = null,
    val alignmentTranslationX: Float? = null,
    val alignmentTranslationY: Float? = null,
    val alignmentScale: Float? = null,
    val alignmentRotationDegrees: Float? = null,
)

/** Flattened columns for [Analysis] so Room can embed it in a row. */
data class AnalysisColumns(
    val lesionType: String,
    val colour: String,
    val symmetry: String,
    val borders: String,
    val texture: String,
    val summary: String,
) {
    fun toAnalysis() = Analysis(lesionType, colour, symmetry, borders, texture, summary)

    companion object {
        fun from(a: Analysis) =
            AnalysisColumns(a.lesionType, a.colour, a.symmetry, a.borders, a.texture, a.summary)
    }
}

/** A scan with all its observations, newest first — the timeline projection. */
data class ScanWithObservations(
    @Embedded val scan: ScanEntity,
    @Relation(parentColumn = "id", entityColumn = "scanId")
    val observations: List<ObservationEntity>,
) {
    val latest: ObservationEntity? get() = observations.maxByOrNull { it.capturedAt }
    val timeline: List<ObservationEntity> get() = observations.sortedByDescending { it.capturedAt }
}
