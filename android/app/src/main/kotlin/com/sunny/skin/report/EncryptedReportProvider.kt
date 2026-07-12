package com.sunny.skin.report

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.sunny.skin.data.crypto.CryptoManager
import java.io.FileNotFoundException

/** Read-only provider that decrypts a shared PDF directly into an OS pipe. */
class EncryptedReportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/pdf"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("reports are read-only")
        val ctx = context ?: throw FileNotFoundException("provider unavailable")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("missing report")
        val reportId = name.removeSuffix(".pdf")
        val encrypted = runCatching { ReportStore(ctx).file(reportId) }.getOrNull()
            ?.takeIf { it.isFile }
            ?: throw FileNotFoundException("report not found")
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                runCatching {
                    output.write(CryptoManager.decrypt(ctx, encrypted.readBytes()))
                }
            }
        }, "sunny-report-share").start()
        return pipe[0]
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> uri.lastPathSegment ?: "sunny-report.pdf"
                OpenableColumns.SIZE -> null
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
}
