package com.sunny.skin.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File

/**
 * Lists, decrypts and shares generated PDF reports. Reports are AES-GCM encrypted
 * at rest in filesDir/reports; a short-lived plaintext copy is written to
 * cacheDir/shared_reports only for in-app viewing and the user-initiated Share.
 */
class ReportStore(private val context: Context) {
    private val dir = File(context.filesDir, "reports").apply { mkdirs() }
    private val cacheDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** The ENCRYPTED report file. Ids are internal; guard against path traversal. */
    fun file(reportId: String): File {
        require(!reportId.contains('/') && !reportId.contains('\\') && !reportId.contains("..")) {
            "invalid report id"
        }
        return File(dir, "$reportId.pdf")
    }

    /** Decrypt the report into a plaintext cache copy for PdfRenderer / sharing. */
    fun decryptToCache(reportId: String): File? {
        val enc = file(reportId).takeIf { it.exists() } ?: return null
        return runCatching {
            val plain = CryptoManager.decrypt(context, enc.readBytes())
            File(cacheDir, "$reportId.pdf").apply { writeBytes(plain) }
        }.getOrNull()
    }

    fun delete(reportId: String) {
        file(reportId).takeIf { it.exists() }?.delete()
        File(cacheDir, "$reportId.pdf").takeIf { it.exists() }?.delete()
    }

    /** Content URI over a decrypted cache copy, safe to share externally. */
    fun shareUri(reportId: String): Uri? {
        val plain = decryptToCache(reportId) ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", plain)
    }
}
