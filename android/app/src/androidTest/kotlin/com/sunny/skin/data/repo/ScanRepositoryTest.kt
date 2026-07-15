package com.sunny.skin.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.data.model.BodyPart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = ScanRepository(context)
    private val analysis = Analysis(
        lesionType = "Description",
        colour = "Brown",
        symmetry = "Not described",
        borders = "Not described",
        texture = "Not described",
        summary = "Tracking description",
    )

    @Test
    fun addingFollowUpPromotesSingleScanToTracked() = runBlocking {
        val now = System.currentTimeMillis()
        val scanId = repository.createScan(
            imagePath = "/private/first.enc",
            bodyPart = BodyPart.SHOULDER,
            scanType = ScanType.SINGLE,
            analysis = analysis,
            modelVersion = "test",
            rawOutput = "test",
            now = now,
        )

        try {
            assertEquals(
                ScanType.SINGLE,
                repository.allScansOnce().first { it.scan.id == scanId }.scan.scanType,
            )

            repository.addObservation(
                scanId = scanId,
                imagePath = "/private/follow-up.enc",
                analysis = analysis,
                modelVersion = "test",
                rawOutput = "test",
                now = now + 1,
            )

            assertEquals(
                ScanType.TRACKED,
                repository.allScansOnce().first { it.scan.id == scanId }.scan.scanType,
            )
        } finally {
            repository.allScansOnce().firstOrNull { it.scan.id == scanId }?.let {
                repository.deleteScan(it)
            }
        }
    }

    @Test
    fun privateNotesRoundTripAndAreTrimmed() = runBlocking {
        val now = System.currentTimeMillis()
        val scanId = repository.createScan(
            imagePath = "/private/note-test.enc",
            bodyPart = BodyPart.UPPER_BACK,
            scanType = ScanType.TRACKED,
            analysis = analysis,
            modelVersion = "test",
            rawOutput = "test",
            now = now,
        )

        try {
            repository.updateNotes(scanId, "  Bring up at next appointment.  ", now + 1)

            assertEquals(
                "Bring up at next appointment.",
                repository.allScansOnce().first { it.scan.id == scanId }.scan.notes,
            )
        } finally {
            repository.allScansOnce().firstOrNull { it.scan.id == scanId }?.let {
                repository.deleteScan(it)
            }
        }
    }
}
