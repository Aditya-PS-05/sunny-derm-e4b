package com.sunny.skin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Transaction
    @Query("SELECT * FROM scans ORDER BY updatedAt DESC")
    fun observeScans(): Flow<List<ScanWithObservations>>

    @Transaction
    @Query("SELECT * FROM scans WHERE id = :scanId")
    fun observeScan(scanId: String): Flow<ScanWithObservations?>

    @Transaction
    @Query("SELECT * FROM scans")
    suspend fun allScansOnce(): List<ScanWithObservations>

    @Upsert
    suspend fun upsertScan(scan: ScanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    @Query("UPDATE scans SET updatedAt = :ts WHERE id = :scanId")
    suspend fun touchScan(scanId: String, ts: Long)

    @Query("UPDATE scans SET scanType = :scanType, updatedAt = :ts WHERE id = :scanId")
    suspend fun updateScanType(scanId: String, scanType: ScanType, ts: Long)

    @Query("UPDATE scans SET name = :name, updatedAt = :ts WHERE id = :scanId")
    suspend fun renameScan(scanId: String, name: String, ts: Long)

    @Query("UPDATE scans SET notes = :notes, updatedAt = :ts WHERE id = :scanId")
    suspend fun updateNotes(scanId: String, notes: String, ts: Long)

    @Query("DELETE FROM scans WHERE id = :scanId")
    suspend fun deleteScan(scanId: String)

    @Query("DELETE FROM scans")
    suspend fun deleteAllScans()

    @Query("DELETE FROM observations WHERE id = :observationId")
    suspend fun deleteObservation(observationId: String)

    @Query("SELECT COUNT(*) FROM observations")
    fun observeObservationCount(): Flow<Int>
}
