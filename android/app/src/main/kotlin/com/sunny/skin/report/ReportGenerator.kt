package com.sunny.skin.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.sunny.skin.data.ImageStore
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.util.Format
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders selected scans into an on-device PDF report (F-16), shaped as a
 * doctor-visit handoff: a summary cover page listing which tracked spots changed,
 * then one page per spot showing the first vs latest photo side by side and what
 * the description flagged as different. Generated locally, never uploaded; the
 * copy makes clear it is "not a medical diagnosis". Files land in filesDir/reports
 * encrypted and are only shareable via the FileProvider when the user taps Share.
 */
class ReportGenerator(private val context: Context) {

    private val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
    private val images = ImageStore(context)

    fun generate(scans: List<ScanWithObservations>, createdAt: Long): File {
        val id = Format.reportId(createdAt)
        val doc = PdfDocument()

        // Cover / summary page — oriented for a clinician skimming before the visit.
        val cover = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawCover(cover.canvas, scans, id, createdAt)
        doc.finishPage(cover)

        var pageNo = 2
        scans.forEach { scan ->
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
            )
            drawScan(page.canvas, scan, id)
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

    // ---- Cover page ----

    private fun drawCover(c: android.graphics.Canvas, scans: List<ScanWithObservations>, id: String, ts: Long) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)

        y += 46f
        c.drawText("Skin Tracking Summary", MARGIN, y, coverTitlePaint)
        y += 22f
        c.drawText("Prepared for a clinical visit · ${Format.date(ts)}", MARGIN, y, mutedPaint)

        y += 34f
        val changed = scans.filter { changedLabels(it).isNotEmpty() }
        c.drawText(
            "${scans.size} spot${plural(scans.size)} tracked        " +
                "${changed.size} with noted change${plural(changed.size)}",
            MARGIN, y, bodyPaint,
        )

        y += 32f
        c.drawText("SPOTS WITH CHANGES SINCE FIRST PHOTO", MARGIN, y, labelPaint)
        y += 4f
        if (changed.isEmpty()) {
            y += 22f
            y = drawWrapped(
                c,
                "No described changes across the spots in this report. Photos are included " +
                    "for your clinician to review.",
                MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint,
            )
        } else {
            changed.forEach { s ->
                y += 22f
                val labels = changedLabels(s).joinToString(", ")
                val since = oldest(s)?.let { Format.date(it.capturedAt) } ?: ""
                y = drawWrapped(
                    c, "•  ${s.scan.name} — $labels (since $since)",
                    MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint,
                )
            }
        }

        c.drawText("Generated on-device by Sunny. Photos and notes never left this phone.",
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("This summary is a visual description for tracking only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    // ---- Per-spot page ----

    private fun drawScan(c: android.graphics.Canvas, scan: ScanWithObservations, id: String) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)

        val latest = scan.latest
        val first = oldest(scan)

        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 22f
        val dateLine = latest?.let { Format.date(it.capturedAt) } ?: ""
        c.drawText("${scan.scan.bodyPart.locationLine}   $dateLine", MARGIN, y, mutedPaint)

        // Before/after timeline when there are 2+ dated photos.
        if (scan.observations.size > 1 && first != null && latest != null) {
            y += 28f
            c.drawText("TIMELINE", MARGIN, y, labelPaint)
            y += 14f
            val gap = 16f
            val halfW = (PAGE_W - 2 * MARGIN - gap) / 2f
            val boxH = 200f
            drawPhoto(c, first.imagePath, MARGIN, y, halfW, boxH)
            drawPhoto(c, latest.imagePath, MARGIN + halfW + gap, y, halfW, boxH)
            c.drawText("First · ${Format.date(first.capturedAt)}", MARGIN, y + boxH + 14f, mutedPaint)
            c.drawText("Latest · ${Format.date(latest.capturedAt)}", MARGIN + halfW + gap, y + boxH + 14f, mutedPaint)
            y += boxH + 30f
            val labels = changedLabels(scan)
            val note = if (labels.isNotEmpty())
                "What changed since ${Format.date(first.capturedAt)}: ${labels.joinToString(", ")}."
            else "No described change since ${Format.date(first.capturedAt)}."
            y = drawWrapped(c, note, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
        } else if (latest != null) {
            val bmp = images.decryptToBitmap(latest.imagePath)
            if (bmp != null) {
                y += 20f
                val w = (PAGE_W - 2 * MARGIN)
                val h = (w * bmp.height / bmp.width).coerceAtMost(300f)
                c.drawBitmap(bmp, null, Rect(MARGIN.toInt(), y.toInt(), (MARGIN + w).toInt(), (y + h).toInt()), null)
                y += h
            }
        }

        // Latest structured description.
        y += 30f
        c.drawText("SUNNY ANALYSIS", MARGIN, y, labelPaint)
        y += 8f
        latest?.analysis?.toAnalysis()?.rows()?.forEach { (label, value) ->
            y += 22f
            c.drawText(label, MARGIN, y, fieldLabelPaint)
            y += 18f
            y = drawWrapped(c, value, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
        }

        c.drawText("This report is a visual description for tracking only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    /** Decrypt + draw a photo into a (x,y,w,maxH) box, preserving aspect. */
    private fun drawPhoto(c: android.graphics.Canvas, path: String, x: Float, y: Float, w: Float, maxH: Float) {
        val bmp = images.decryptToBitmap(path) ?: return
        val h = (w * bmp.height / bmp.width).coerceAtMost(maxH)
        c.drawBitmap(bmp, null, Rect(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt()), null)
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

    private fun oldest(s: ScanWithObservations): ObservationEntity? =
        s.observations.minByOrNull { it.capturedAt }

    /** Description fields (excluding the summary rollup) that differ first→latest. */
    private fun changedLabels(s: ScanWithObservations): List<String> {
        if (s.observations.size < 2) return emptyList()
        val first = oldest(s)?.analysis?.toAnalysis() ?: return emptyList()
        val last = s.latest?.analysis?.toAnalysis() ?: return emptyList()
        return Analysis.FIELDS.filterIndexed { i, label ->
            label != "Summary" && first.rows()[i].second.trim() != last.rows()[i].second.trim()
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private companion object {
        const val PAGE_W = 595   // A4 @ 72dpi
        const val PAGE_H = 842
        const val MARGIN = 40f

        val brandPaint = Paint().apply { color = Color.parseColor("#FF7A00"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val coverTitlePaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 24f; isFakeBoldText = true; isAntiAlias = true }
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
