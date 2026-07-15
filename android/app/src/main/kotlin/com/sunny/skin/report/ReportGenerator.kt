package com.sunny.skin.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.sunny.skin.data.ImageStore
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.util.Format
import com.sunny.skin.util.AlignmentResult
import com.sunny.skin.util.AlignTransform
import com.sunny.skin.util.FramingQuality
import com.sunny.skin.util.ImageAlignment
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders selected scans into an on-device PDF report (F-16), shaped as a
 * doctor-visit handoff: a summary cover page listing which tracked spots changed,
 * then one page per spot showing the first vs latest photo side by side and what
 * the description flagged as different. The report itself is generated locally;
 * the copy makes clear it is "not a medical diagnosis". Files land encrypted in
 * filesDir/reports and are stream-decrypted only after the user taps Share.
 */
data class ReportOptions(val visitNote: String = "")

class ReportGenerator(private val context: Context) {

    private val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
    private val images = ImageStore(context)

    suspend fun generate(
        scans: List<ScanWithObservations>,
        createdAt: Long,
        options: ReportOptions = ReportOptions(),
    ): File {
        val id = Format.reportId(createdAt)
        val doc = PdfDocument()

        // Cover / summary page — oriented for a clinician skimming before the visit.
        val cover = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawCover(cover.canvas, scans, id, createdAt, options)
        doc.finishPage(cover)

        var pageNo = 2
        scans.forEach { scan ->
            val first = oldest(scan)
            val latest = scan.latest
            val alignment = if (first != null && latest != null && first.id != latest.id) {
                ImageAlignment.computeResult(context, first.imagePath, latest.imagePath)
            } else {
                AlignmentResult()
            }
            val summaryPage = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
            )
            drawScanSummary(summaryPage.canvas, scan, id, alignment)
            doc.finishPage(summaryPage)

            latest?.let {
                val descriptionPage = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
                )
                drawDescription(descriptionPage.canvas, scan, it, id)
                doc.finishPage(descriptionPage)
            }

            if (scan.scan.notes.isNotBlank()) {
                val notesPage = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
                )
                drawNotesPage(notesPage.canvas, scan, id)
                doc.finishPage(notesPage)
            }

            val timeline = scan.timeline.sortedBy { it.capturedAt }
            timeline.chunked(PHOTOS_PER_PAGE).forEachIndexed { index, chunk ->
                val timelinePage = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create(),
                )
                drawTimelinePage(
                    timelinePage.canvas,
                    scan,
                    chunk,
                    index + 1,
                    (timeline.size + PHOTOS_PER_PAGE - 1) / PHOTOS_PER_PAGE,
                    id,
                )
                doc.finishPage(timelinePage)
            }
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

    private fun drawCover(
        c: android.graphics.Canvas,
        scans: List<ScanWithObservations>,
        id: String,
        ts: Long,
        options: ReportOptions,
    ) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)

        y += 46f
        c.drawText("Visual Tracking Summary", MARGIN, y, coverTitlePaint)
        y += 22f
        c.drawText("Prepared for a clinical visit · ${Format.date(ts)}", MARGIN, y, mutedPaint)

        y += 34f
        val changed = scans.filter { changedLabels(it).isNotEmpty() }
        c.drawText(
            "${scans.size} spot${plural(scans.size)} tracked        " +
                "${changed.size} with description difference${plural(changed.size)}",
            MARGIN, y, bodyPaint,
        )

        y += 32f
        c.drawText("AUTOMATED DESCRIPTION DIFFERENCES", MARGIN, y, labelPaint)
        y += 4f
        if (changed.isEmpty()) {
            y += 22f
            y = drawWrapped(
                c,
                "The automated descriptions use the same wording across the selected first and " +
                    "latest photos. This does not establish that the skin is unchanged.",
                MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint,
            )
        } else {
            changed.take(MAX_COVER_ITEMS).forEach { s ->
                y += 22f
                val labels = changedLabels(s).joinToString(", ")
                val since = oldest(s)?.let { Format.date(it.capturedAt) } ?: ""
                y = drawWrapped(
                    c, "•  ${s.scan.name} — $labels (compared with $since)",
                    MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint,
                )
            }
            if (changed.size > MAX_COVER_ITEMS) {
                y += 22f
                c.drawText(
                    "+ ${changed.size - MAX_COVER_ITEMS} more listed in the following pages",
                    MARGIN,
                    y,
                    mutedPaint,
                )
            }
        }

        if (options.visitNote.isNotBlank()) {
            y += 30f
            c.drawText("REASON FOR SHARING", MARGIN, y, labelPaint)
            y += 18f
            drawWrapped(
                c,
                options.visitNote.trim().take(500),
                MARGIN,
                y,
                PAGE_W - 2 * MARGIN,
                bodyPaint,
            )
        }

        val includesServerAnalysis = scans.any { scan ->
            scan.observations.any { it.modelVersion.contains("(server)", ignoreCase = true) }
        }
        val privacyFooter = if (includesServerAnalysis) {
            "Generated locally; beta scan analysis used Sunny's configured server."
        } else {
            "Generated on-device by Sunny. Photos and notes never left this phone."
        }
        c.drawText(privacyFooter,
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("Automated descriptions and measurements are for tracking only — not a diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    // ---- Per-spot page ----

    private fun drawScanSummary(
        c: android.graphics.Canvas,
        scan: ScanWithObservations,
        id: String,
        alignment: AlignmentResult,
    ) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)

        val latest = scan.latest
        val first = oldest(scan)

        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 22f
        val dateLine = latest?.let { Format.date(it.capturedAt) } ?: ""
        c.drawText("${scan.scan.bodyPart.locationLine}   $dateLine", MARGIN, y, mutedPaint)

        // Registration is used only for visual overlap; originals appear in the appendix.
        if (scan.observations.size > 1 && first != null && latest != null) {
            y += 28f
            c.drawText("FIRST AND LATEST", MARGIN, y, labelPaint)
            y += 14f
            val gap = 16f
            val halfW = (PAGE_W - 2 * MARGIN - gap) / 2f
            val boxH = 200f
            drawComparisonPhoto(c, first.imagePath, MARGIN, y, halfW, boxH, AlignTransform.Identity)
            drawComparisonPhoto(
                c,
                latest.imagePath,
                MARGIN + halfW + gap,
                y,
                halfW,
                boxH,
                if (alignment.isUsable) alignment.transform else AlignTransform.Identity,
            )
            c.drawText("First · ${Format.date(first.capturedAt)}", MARGIN, y + boxH + 14f, mutedPaint)
            c.drawText("Latest · ${Format.date(latest.capturedAt)}", MARGIN + halfW + gap, y + boxH + 14f, mutedPaint)
            y += boxH + 30f
            val alignmentText = if (alignment.isUsable) {
                "Latest photo aligned for comparison · ${when (alignment.quality) {
                    FramingQuality.HIGH -> "high"
                    FramingQuality.MODERATE -> "moderate"
                    FramingQuality.LOW -> "low"
                }} technical framing match."
            } else {
                "Raw photos shown because automatic alignment was not reliable."
            }
            y = drawWrapped(c, alignmentText, MARGIN, y, PAGE_W - 2 * MARGIN, mutedPaint)
            y += 4f
            val labels = changedLabels(scan)
            val note = if (labels.isNotEmpty())
                "Automated description differences: ${labels.joinToString(", ")}."
            else "No wording difference in the automated descriptions; this is not evidence of stability."
            y = drawWrapped(c, note, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
        } else if (latest != null) {
            y += 20f
            drawPhotoFit(c, latest.imagePath, MARGIN, y, PAGE_W - 2 * MARGIN, 300f)
            y += 300f
        }

        val measurements = listOfNotNull(
            first?.approximateSizeMm?.let { "First: ${formatMm(it)}" },
            latest?.approximateSizeMm?.let { "Latest: ${formatMm(it)}" },
        )
        if (measurements.isNotEmpty()) {
            y += 24f
            c.drawText("REFERENCE-BASED SIZE ESTIMATES", MARGIN, y, labelPaint)
            y += 18f
            c.drawText(measurements.joinToString("    "), MARGIN, y, bodyPaint)
            y += 18f
            y = drawWrapped(
                c,
                "Approximate only; values depend on reference and endpoint placement.",
                MARGIN,
                y,
                PAGE_W - 2 * MARGIN,
                mutedPaint,
            )
        }

        if (scan.scan.notes.isNotBlank()) {
            y += 26f
            c.drawText("PRIVATE NOTE", MARGIN, y, labelPaint)
            y += 16f
            c.drawText("Included in full on the following note page.", MARGIN, y, bodyPaint)
        }

        c.drawText("Original uncropped photos and automated description follow on separate pages.",
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("This report supports visual tracking only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    private fun drawDescription(
        c: android.graphics.Canvas,
        scan: ScanWithObservations,
        latest: ObservationEntity,
        id: String,
    ) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)
        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 20f
        c.drawText("AUTOMATED DESCRIPTION · ${Format.date(latest.capturedAt)}", MARGIN, y, labelPaint)
        y += 20f
        latest.analysis.toAnalysis().rows().forEach { (label, value) ->
            c.drawText(label, MARGIN, y, fieldLabelPaint)
            y += 17f
            y = drawWrapped(c, value, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
            y += 12f
        }
        y += 8f
        c.drawText("MODEL PROVENANCE", MARGIN, y, labelPaint)
        y += 18f
        y = drawWrapped(c, latest.modelVersion, MARGIN, y, PAGE_W - 2 * MARGIN, mutedPaint)
        c.drawText("Automated text may be incomplete or incorrect; review the photos directly.",
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("No diagnosis, risk score, urgency assessment or treatment advice is provided.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    private fun drawNotesPage(
        c: android.graphics.Canvas,
        scan: ScanWithObservations,
        id: String,
    ) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)
        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 22f
        c.drawText("USER-PROVIDED NOTE", MARGIN, y, labelPaint)
        y += 20f
        drawWrapped(c, scan.scan.notes, MARGIN, y, PAGE_W - 2 * MARGIN, bodyPaint)
        c.drawText("This text was entered by the user and was not interpreted by Sunny.",
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("Visual tracking record only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    private fun drawTimelinePage(
        c: android.graphics.Canvas,
        scan: ScanWithObservations,
        observations: List<ObservationEntity>,
        page: Int,
        pages: Int,
        id: String,
    ) {
        c.drawColor(Color.WHITE)
        var y = header(c, id)
        y += 30f
        c.drawText(scan.scan.name, MARGIN, y, titlePaint)
        y += 20f
        c.drawText("ORIGINAL PHOTO TIMELINE · $page OF $pages", MARGIN, y, labelPaint)
        y += 16f

        val gap = 16f
        val cellW = (PAGE_W - 2 * MARGIN - gap) / 2f
        val cellH = 320f
        observations.forEachIndexed { index, observation ->
            val column = index % 2
            val row = index / 2
            val x = MARGIN + column * (cellW + gap)
            val top = y + row * cellH
            drawPhotoFit(c, observation.imagePath, x, top, cellW, 250f)
            c.drawText(Format.date(observation.capturedAt), x, top + 268f, bodyPaint)
            observation.approximateSizeMm?.let {
                c.drawText("Approx. ${formatMm(it)}", x, top + 284f, mutedPaint)
            }
        }
        c.drawText("Photos are shown uncropped and without automatic alignment.",
            MARGIN, PAGE_H - 46f, footerPaint)
        c.drawText("Visual tracking record only — not a medical diagnosis.",
            MARGIN, PAGE_H - 30f, footerPaint)
    }

    /** Draw an uncropped original inside a fixed box with neutral letterboxing. */
    private fun drawPhotoFit(
        c: android.graphics.Canvas,
        path: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) {
        val bmp = images.decryptToBitmap(path) ?: return
        c.drawRect(x, y, x + w, y + h, photoBackgroundPaint)
        val scale = minOf(w / bmp.width, h / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = x + (w - drawW) / 2f
        val top = y + (h - drawH) / 2f
        c.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), null)
        bmp.recycle()
    }

    /** Centre-cropped comparison rendering with the same transform used by Compare. */
    private fun drawComparisonPhoto(
        c: android.graphics.Canvas,
        path: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        transform: AlignTransform,
    ) {
        val bmp = images.decryptToBitmap(path) ?: return
        c.drawRect(x, y, x + w, y + h, photoBackgroundPaint)
        val sourceAspect = bmp.width.toFloat() / bmp.height
        val targetAspect = w / h
        val source = if (sourceAspect > targetAspect) {
            val sourceW = (bmp.height * targetAspect).toInt()
            val left = (bmp.width - sourceW) / 2
            Rect(left, 0, left + sourceW, bmp.height)
        } else {
            val sourceH = (bmp.width / targetAspect).toInt()
            val top = (bmp.height - sourceH) / 2
            Rect(0, top, bmp.width, top + sourceH)
        }
        c.save()
        c.clipRect(x, y, x + w, y + h)
        c.translate(
            x + w / 2f + transform.tx * w,
            y + h / 2f + transform.ty * h,
        )
        c.rotate(transform.rotationDeg)
        c.scale(transform.scale, transform.scale)
        c.drawBitmap(bmp, source, RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), null)
        c.restore()
        bmp.recycle()
    }

    private fun header(c: android.graphics.Canvas, id: String): Float {
        c.drawText("Sunny", MARGIN, MARGIN, brandPaint)
        c.drawText("VISUAL TRACKING REPORT", PAGE_W - MARGIN, MARGIN, rightLabelPaint)
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

    private fun formatMm(value: Float): String {
        val rounded = kotlin.math.round(value * 10f) / 10f
        return if (rounded % 1f == 0f) "${rounded.toInt()} mm" else "$rounded mm"
    }

    private companion object {
        const val PAGE_W = 595   // A4 @ 72dpi
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val PHOTOS_PER_PAGE = 4
        const val MAX_COVER_ITEMS = 12

        val brandPaint = Paint().apply { color = Color.parseColor("#FF7A00"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val coverTitlePaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 24f; isFakeBoldText = true; isAntiAlias = true }
        val titlePaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val labelPaint = Paint().apply { color = Color.parseColor("#B4AEA5"); textSize = 9f; letterSpacing = 0.08f; isAntiAlias = true }
        val fieldLabelPaint = Paint().apply { color = Color.parseColor("#8A857E"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.parseColor("#1C1B1A"); textSize = 11f; isAntiAlias = true }
        val mutedPaint = Paint().apply { color = Color.parseColor("#8A857E"); textSize = 10f; isAntiAlias = true }
        val footerPaint = Paint().apply { color = Color.parseColor("#B4AEA5"); textSize = 8f; isAntiAlias = true }
        val dividerPaint = Paint().apply { color = Color.parseColor("#EDE7DD"); strokeWidth = 1f }
        val photoBackgroundPaint = Paint().apply { color = Color.parseColor("#F2F2F4") }
        val rightLabelPaint = Paint(labelPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightMutedPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
    }
}
