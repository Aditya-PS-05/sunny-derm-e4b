package com.sunny.skin.report

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

/** Lists and shares generated PDF reports from app-private storage. */
class ReportStore(private val context: Context) {
    private val dir = File(context.filesDir, "reports").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun file(reportId: String): File = File(dir, "$reportId.pdf")

    fun delete(reportId: String) { file(reportId).takeIf { it.exists() }?.delete() }

    /** Content URI safe to share externally (only reports are ever shared). */
    fun shareUri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
