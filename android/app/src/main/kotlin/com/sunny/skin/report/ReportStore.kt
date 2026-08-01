package com.sunny.skin.report

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import com.sunny.skin.data.crypto.CryptoManager
import java.io.File

/** Encrypted-at-rest report storage with memory-backed rendering descriptors. */
class ReportStore(private val context: Context) {
    private val dir = File(context.filesDir, "reports").apply { mkdirs() }

    init {
        // Remove plaintext cache files left by older builds.
        runCatching { File(context.cacheDir, "shared_reports").deleteRecursively() }
        runCatching { File(context.cacheDir, "rendered_reports").deleteRecursively() }
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

    /**
     * Provide a seekable, memory-backed descriptor for PdfRenderer. No plaintext
     * report is ever written to disk; the backing byte array is zeroed on close.
     */
    fun openDecryptedReport(reportId: String): ParcelFileDescriptor? {
        val enc = file(reportId).takeIf { it.exists() } ?: return null
        val plain = runCatching { CryptoManager.decrypt(context, enc.readBytes()) }.getOrNull()
            ?: return null
        val thread = HandlerThread("sunny-report-render").apply { start() }
        val callback = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = plain.size.toLong()

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                if (offset < 0 || offset >= plain.size) return 0
                val count = minOf(size, plain.size - offset.toInt())
                plain.copyInto(data, destinationOffset = 0, startIndex = offset.toInt(), endIndex = offset.toInt() + count)
                return count
            }

            override fun onRelease() {
                plain.fill(0)
                thread.quitSafely()
            }
        }
        return runCatching {
            context.getSystemService(StorageManager::class.java).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                Handler(thread.looper),
            )
        }.getOrElse {
            plain.fill(0)
            thread.quitSafely()
            null
        }
    }

    fun delete(reportId: String) {
        file(reportId).takeIf { it.exists() }?.delete()
    }

    fun deleteAll() {
        runCatching { dir.deleteRecursively() }
        dir.mkdirs()
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
