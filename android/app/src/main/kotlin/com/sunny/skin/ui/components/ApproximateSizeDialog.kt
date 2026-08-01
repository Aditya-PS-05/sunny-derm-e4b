package com.sunny.skin.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sunny.skin.data.model.ApproximateMeasurement
import com.sunny.skin.ui.theme.SunnyColors
import kotlin.math.sqrt

private enum class MeasurementLine { REFERENCE, AREA }
private data class NormalizedPoint(val x: Float, val y: Float)

@Composable
fun ApproximateSizeDialog(
    bitmap: Bitmap,
    initial: ApproximateMeasurement?,
    onSave: (ApproximateMeasurement) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var line by remember { mutableStateOf(MeasurementLine.REFERENCE) }
    var referenceText by remember(initial) {
        mutableStateOf(initial?.referenceSizeMm?.toString()?.removeSuffix(".0") ?: "")
    }
    val initialReferenceSpan = initial?.referenceSpan?.coerceIn(0.08f, 0.7f) ?: 0.22f
    val initialTargetSpan = initial?.targetSpan?.coerceIn(0.06f, 0.7f) ?: 0.18f
    var referenceStart by remember { mutableStateOf(NormalizedPoint(0.5f - initialReferenceSpan / 2f, 0.74f)) }
    var referenceEnd by remember { mutableStateOf(NormalizedPoint(0.5f + initialReferenceSpan / 2f, 0.74f)) }
    var targetStart by remember { mutableStateOf(NormalizedPoint(0.5f - initialTargetSpan / 2f, 0.46f)) }
    var targetEnd by remember { mutableStateOf(NormalizedPoint(0.5f + initialTargetSpan / 2f, 0.46f)) }
    var referenceAdjusted by remember { mutableStateOf(initial != null) }
    var targetAdjusted by remember { mutableStateOf(initial != null) }

    val referenceMm = referenceText.replace(',', '.').toFloatOrNull()
    val measurement = if (referenceMm != null) {
        ApproximateMeasurement(
            referenceSizeMm = referenceMm,
            referenceSpan = distance(referenceStart, referenceEnd),
            targetSpan = distance(targetStart, targetEnd),
        )
    } else null
    val valid = measurement?.isValid == true && referenceAdjusted && targetAdjusted

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LiquidGlassDialog(
        onDismiss = {
            val action = pendingAction
            pendingAction = null
            if (action != null) action() else onDismiss()
        },
    ) { requestDismiss ->
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Straighten,
                contentDescription = null,
                tint = SunnyColors.Orange,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Approximate size",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(14.dp))

            Row(
                Modifier.fillMaxWidth().background(
                    SunnyColors.SurfaceMuted,
                    RoundedCornerShape(8.dp),
                ).padding(3.dp),
            ) {
                MeasurementLine.entries.forEach { option ->
                    val selected = line == option
                    Box(
                        Modifier.weight(1f).background(
                            if (selected) SunnyColors.Surface else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        ).clickable { line = option }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (option == MeasurementLine.REFERENCE) "Reference object" else "Visible area",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))

            MeasurementEditor(
                bitmap = bitmap,
                active = line,
                referenceStart = referenceStart,
                referenceEnd = referenceEnd,
                targetStart = targetStart,
                targetEnd = targetEnd,
                onReferenceStart = { referenceStart = it; referenceAdjusted = true },
                onReferenceEnd = { referenceEnd = it; referenceAdjusted = true },
                onTargetStart = { targetStart = it; targetAdjusted = true },
                onTargetEnd = { targetEnd = it; targetAdjusted = true },
            )
            Spacer(Modifier.size(12.dp))

            OutlinedTextField(
                value = referenceText,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isDigit() || it == '.' || it == ',' }) {
                        referenceText = value
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reference width in millimetres") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(8.dp),
            )

            Spacer(Modifier.size(10.dp))
            Text(
                if (valid) "Approx. ${measurement!!.formattedSize()}" else "Position both lines and enter the reference width.",
                style = MaterialTheme.typography.titleMedium,
                color = if (valid) SunnyColors.OrangeText else SunnyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "For consistent personal tracking only; not a clinical measurement.",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextTertiary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.size(14.dp))
            Button(
                onClick = {
                    measurement?.takeIf { it.isValid }?.let { savedMeasurement ->
                        pendingAction = { onSave(savedMeasurement) }
                        requestDismiss()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
            ) {
                Text("Save measurement", fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (initial != null) {
                    TextButton(onClick = {
                        pendingAction = onRemove
                        requestDismiss()
                    }) {
                        Text("Remove", color = SunnyColors.Danger)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                TextButton(onClick = requestDismiss) {
                    Text("Cancel", color = SunnyColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MeasurementEditor(
    bitmap: Bitmap,
    active: MeasurementLine,
    referenceStart: NormalizedPoint,
    referenceEnd: NormalizedPoint,
    targetStart: NormalizedPoint,
    targetEnd: NormalizedPoint,
    onReferenceStart: (NormalizedPoint) -> Unit,
    onReferenceEnd: (NormalizedPoint) -> Unit,
    onTargetStart: (NormalizedPoint) -> Unit,
    onTargetEnd: (NormalizedPoint) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var draggedHandle by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = if (active == MeasurementLine.REFERENCE) {
                    "Reference object measurement markers"
                } else {
                    "Visible area measurement markers"
                }
            }
            .pointerInput(active, size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val points = if (active == MeasurementLine.REFERENCE) {
                            listOf(referenceStart, referenceEnd)
                        } else {
                            listOf(targetStart, targetEnd)
                        }
                        draggedHandle = points.indices.minBy { index ->
                            val point = points[index].toOffset(size)
                            (point - offset).getDistanceSquared()
                        }
                    },
                ) { change, _ ->
                    change.consume()
                    val point = NormalizedPoint(
                        x = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                        y = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                    )
                    when (active) {
                        MeasurementLine.REFERENCE -> if (draggedHandle == 0) onReferenceStart(point) else onReferenceEnd(point)
                        MeasurementLine.AREA -> if (draggedHandle == 0) onTargetStart(point) else onTargetEnd(point)
                    }
                }
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(Modifier.fillMaxSize()) {
            fun drawMeasurement(start: NormalizedPoint, end: NormalizedPoint, color: Color, selected: Boolean) {
                val a = Offset(start.x * size.width, start.y * size.height)
                val b = Offset(end.x * size.width, end.y * size.height)
                drawLine(Color.Black.copy(alpha = 0.72f), a, b, 7.dp.toPx(), StrokeCap.Round)
                drawLine(color, a, b, 3.dp.toPx(), StrokeCap.Round)
                listOf(a, b).forEach { point ->
                    drawCircle(Color.Black.copy(alpha = 0.72f), 12.dp.toPx(), point)
                    drawCircle(color, 8.dp.toPx(), point)
                    if (selected) drawCircle(Color.White, 8.dp.toPx(), point, style = Stroke(2.dp.toPx()))
                }
            }
            drawMeasurement(
                referenceStart,
                referenceEnd,
                if (active == MeasurementLine.REFERENCE) SunnyColors.Orange else Color.White,
                active == MeasurementLine.REFERENCE,
            )
            drawMeasurement(
                targetStart,
                targetEnd,
                if (active == MeasurementLine.AREA) SunnyColors.Orange else Color.White,
                active == MeasurementLine.AREA,
            )
        }
    }
}

private fun NormalizedPoint.toOffset(size: IntSize) = Offset(x * size.width, y * size.height)

private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
