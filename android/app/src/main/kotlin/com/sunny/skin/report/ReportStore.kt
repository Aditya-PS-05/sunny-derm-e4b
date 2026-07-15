package com.sunny.skin.report

import android.content.Context
import android.net.Uri
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File

/** Encrypted-at-rest report storage with ephemeral internal rendering files. */
class ReportStore(private val context: Context) {
    private val dir = File(context.filesDir, "reports").apply { mkdirs() }
    private val renderDir = File(context.cacheDir, "rendered_reports")

    init {
        // Remove plaintext cache files left by older builds or interrupted renders.
        runCatching { File(context.cacheDir, "shared_reports").deleteRecursively() }
        runCatching { renderDir.deleteRecursively() }
        renderDir.mkdirs()
    }

    fun list(): List<File> =
        dir.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** The encrypted report file. IDs are internal; guard against path traversal. */
    fun file(reportId: String): File {
        require(!reportId.contains('/') && !reportId.contains('\\') && !reportId.contains("..")) {
            "invalid report id"
        }
        return File(dir, "$reportId.pdf")
    }

    /** Decrypt for a synchronous renderer and delete the plaintext in all outcomes. */
    fun <T> withDecryptedReport(reportId: String, block: (File) -> T): T? {
        val enc = file(reportId).takeIf { it.exists() } ?: return null
        val plain = File.createTempFile("report-", ".pdf", renderDir)
        return try {
            plain.writeBytes(CryptoManager.decrypt(context, enc.readBytes()))
            block(plain)
        } catch (_: Exception) {
            null
        } finally {
            plain.delete()
        }
    }

    fun delete(reportId: String) {
        file(reportId).takeIf { it.exists() }?.delete()
    }

    fun deleteAll() {
        runCatching { dir.deleteRecursively() }
        dir.mkdirs()
        runCatching { renderDir.deleteRecursively() }
        renderDir.mkdirs()
    }

    /** URI served by [EncryptedReportProvider]; no decrypted share file is written. */
    fun shareUri(reportId: String): Uri? = file(reportId).takeIf { it.exists() }?.let {
        Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.reports")
            .appendPath("reports")
            .appendPath("$reportId.pdf")
            .build()
    }
}
