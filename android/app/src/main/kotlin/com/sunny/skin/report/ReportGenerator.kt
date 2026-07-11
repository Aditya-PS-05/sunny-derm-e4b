package com.sunny.skin.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.sunny.skin.data.ImageStore
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.util.Format
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders selected scans into an on-device PDF report (F-16). Generated locally,
 * never uploaded; the confirmation copy makes clear it is "not a medical
 * diagnosis". Files land in filesDir/reports and are only shareable via the
 * FileProvider when the user taps Share.
 */
class ReportGenerator(private val context: Context) {

    private val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }

    fun generate(scans: List<ScanWithObservations>, createdAt: Long): File {
        val id = Format.reportId(createdAt)
        val doc = PdfDocument()
        var pageNo = 1
        scans.forEach { scan ->
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
            )
            drawScan(page.canvas, scan, id, createdAt)
            doc.finishPage(page)
        }
        if (scans.isEmpty()) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            header(page.canvas, id)
            doc.finishPage(page)
        }

        // Encrypt the PDF at rest; it is decrypted to a short-lived cache copy
        // only for viewing/sharing (see ReportStore).
        val bytes = ByteArrayOutputStream().use { doc.writeTo(it); it.toByteArray() }
        doc.close()
        val file = File(reportsDir, "$id.pdf")
        file.writeBytes(CryptoManager.encrypt(context, bytes))
        return file
    }

    private fun drawScan(c: android.graphics.Canvas, scan: ScanWithObservations, id: String, ts: Long) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)

        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 22f
        c.drawText("${scan.scan.bodyPart.locationLine}   ${Format.date(ts)}", MARGIN, y, mutedPaint)
        y += 30f
        c.drawText("SUNNY ANALYSIS", MARGIN, y, labelPaint)
        y += 8f

        scan.latest?.analysis?.toAnalysis()?.rows()?.forEach { (label, value) ->
            y += 22f
            c.drawText(label, MARGIN, y, fieldLabelPaint)
            y += 18f
            y = drawWrapped(c, value, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
        }

        // Photo (decrypted from app-private storage)
        scan.latest?.let { obs ->
            val bmp = ImageStore(context).decryptToBitmap(obs.imagePath)
            if (bmp != null) {
                y += 24f
                val w = (PAGE_W - 2 * MARGIN).toInt()
                val h = (w * bmp.height / bmp.width).coerceAtMost(300)
                c.drawBitmap(bmp, null, Rect(MARGIN.toInt(), y.toInt(), MARGIN.toInt() + w, y.toInt() + h), null)
            }
        }

        // Footer disclaimer (S-02)
        c.drawText("This report is a visual description for tracking only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    private fun header(c: android.graphics.Canvas, id: String): Float {
        c.drawText("☀ Sunny", MARGIN, MARGIN, brandPaint)
        c.drawText("SKIN EXAMINATION REPORT", PAGE_W - MARGIN, MARGIN, rightLabelPaint)
        c.drawText(id, PAGE_W - MARGIN, MARGIN + 16f, rightMutedPaint)
        c.drawLine(MARGIN, MARGIN + 24f, PAGE_W - MARGIN, MARGIN + 24f, dividerPaint)
        return MARGIN + 24f
    }

    private fun drawWrapped(
        c: android.graphics.Canvas, text: String, x: Float, startY: Float, maxW: Float, paint: Paint,
    ): Float {
        var y = startY
        val words = text.split(" ")
        val line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxW) {
                c.drawText(line.toString(), x, y, paint)
                y += 16f
                line.clear().append(word)
            } else line.clear().append(candidate)
        }
        if (line.isNotEmpty()) { c.drawText(line.toString(), x, y, paint); y += 16f }
        return y
    }

    private companion object {
        const val PAGE_W = 595   // A4 @ 72dpi
        const val PAGE_H = 842
        const val MARGIN = 40f

        val brandPaint = Paint().apply { color = Color.parseColor("#FF7A00"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val titlePaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val labelPaint = Paint().apply { color = Color.parseColor("#B4AEA5"); textSize = 9f; letterSpacing = 0.08f; isAntiAlias = true }
        val fieldLabelPaint = Paint().apply { color = Color.parseColor("#8A857E"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 11f; isAntiAlias = true }
        val mutedPaint = Paint().apply { color = Color.parseColor("#8A857E"); textSize = 10f; isAntiAlias = true }
        val footerPaint = Paint().apply { color = Color.parseColor("#B4AEA5"); textSize = 8f; isAntiAlias = true }
        val dividerPaint = Paint().apply { color = Color.parseColor("#EDE7DD"); strokeWidth = 1f }
        val rightLabelPaint = Paint(labelPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightMutedPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
    }
}
