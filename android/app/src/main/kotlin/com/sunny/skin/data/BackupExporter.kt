package com.sunny.skin.data

import android.content.Context
import com.sunny.skin.BuildConfig
import com.sunny.skin.SunnyApp
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.report.ReportStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Writes a portable password-encrypted archive without plaintext temp files. */
class BackupExporter(context: Context) {
    private val app = context.applicationContext as SunnyApp
    private val abcde = AbcdeStore(app)
    private val reminders = com.sunny.skin.reminder.ReminderStore(app)
    private val reports = ReportStore(app)
    private val backups = BackupStore(app)

    suspend fun export(password: CharArray): File = withContext(Dispatchers.IO) {
        require(password.size >= 10) { "Use at least 10 characters." }
        val scans = app.repository.allScansOnce()
        val createdAt = System.currentTimeMillis()
        val name = "sunny-${utcStamp(createdAt)}.sunnybackup"
        val output = backups.create(name)
        try {
            runCatching {
                BackupCipher.write(output, password) { encrypted ->
                    ZipOutputStream(encrypted).use { zip ->
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(manifest(scans, createdAt).toString(2).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        scans.flatMap { it.observations }.forEach { observation ->
                            zip.putNextEntry(ZipEntry("images/${observation.id}.jpg"))
                            File(observation.imagePath).inputStream().use {
                                CryptoManager.decryptTo(app, it, zip)
                            }
                            zip.closeEntry()
                        }
                        reports.list().forEach { report ->
                            zip.putNextEntry(ZipEntry("reports/${report.name}"))
                            report.inputStream().use { CryptoManager.decryptTo(app, it, zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }.onFailure { output.delete() }.getOrThrow()
            output
        } finally {
            password.fill('\u0000')
        }
    }

    private fun manifest(
        scans: List<com.sunny.skin.data.db.ScanWithObservations>,
        createdAt: Long,
    ) = JSONObject().apply {
        put("format", "sunny-encrypted-backup")
        put("schemaVersion", 2)
        put("createdAt", createdAt)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("scans", JSONArray().apply {
            scans.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.scan.id)
                    put("name", item.scan.name)
                    put("bodyPart", item.scan.bodyPart.name)
                    put("scanType", item.scan.scanType.name)
                    put("createdAt", item.scan.createdAt)
                    put("updatedAt", item.scan.updatedAt)
                    put("notes", item.scan.notes)
                    put("abcde", JSONObject().apply {
                        abcde.get(item.scan.id).forEach { (key, value) -> put(key.name, value.name) }
                    })
                    put("observations", JSONArray().apply {
                        item.timeline.forEach { obs ->
                            put(JSONObject().apply {
                                put("id", obs.id)
                                put("capturedAt", obs.capturedAt)
                                put("image", "images/${obs.id}.jpg")
                                put("modelVersion", obs.modelVersion)
                                put("rawOutput", obs.rawOutput)
                                put("approximateSizeMm", obs.approximateSizeMm ?: JSONObject.NULL)
                                put("measurement", JSONObject().apply {
                                    put("referenceMm", obs.sizeReferenceMm ?: JSONObject.NULL)
                                    put("referenceSpan", obs.sizeReferenceSpan ?: JSONObject.NULL)
                                    put("targetSpan", obs.sizeTargetSpan ?: JSONObject.NULL)
                                })
                                put("captureAlignment", JSONObject().apply {
                                    put("score", obs.alignmentScore ?: JSONObject.NULL)
                                    put("translationX", obs.alignmentTranslationX ?: JSONObject.NULL)
                                    put("translationY", obs.alignmentTranslationY ?: JSONObject.NULL)
                                    put("scale", obs.alignmentScale ?: JSONObject.NULL)
                                    put("rotationDegrees", obs.alignmentRotationDegrees ?: JSONObject.NULL)
                                })
                                put("analysis", JSONObject().apply {
                                    put("lesionType", obs.analysis.lesionType)
                                    put("colour", obs.analysis.colour)
                                    put("symmetry", obs.analysis.symmetry)
                                    put("borders", obs.analysis.borders)
                                    put("texture", obs.analysis.texture)
                                    put("summary", obs.analysis.summary)
                                })
                            })
                        }
                    })
                })
            }
        })
        put("reminders", JSONArray().apply { reminders.all().forEach { put(it.toJson()) } })
        put("reports", JSONArray().apply { reports.list().forEach { put("reports/${it.name}") } })
    }

    private fun utcStamp(timestamp: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestamp))

}
