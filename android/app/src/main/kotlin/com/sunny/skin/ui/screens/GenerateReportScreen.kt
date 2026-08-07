package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.report.ReportGenerator
import com.sunny.skin.report.ReportOptions
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.LiquidGlassDialog
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.components.SunnyToggle
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MS = 24L * 60 * 60 * 1000

private enum class ReportSaveState { Idle, Generating, Saved, Failed }

@Composable
fun GenerateReportScreen(vm: SunnyViewModel, onDismiss: () -> Unit, onOpenReport: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scans by vm.scans.collectAsStateWithLifecycle()

    var region by remember { mutableStateOf<BodyRegion?>(null) }
    var dateRangeOn by remember { mutableStateOf(false) }
    var startMs by remember { mutableStateOf<Long?>(null) }
    var endMs by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var visitNote by remember { mutableStateOf("") }
    var saveState by remember { mutableStateOf(ReportSaveState.Idle) }

    // Scans the user has explicitly unchecked (everything else is included).
    val deselected = remember { mutableStateListOf<String>() }

    // Candidates = scans matching the body-area + date-range filters.
    val candidates = run {
        var list = if (region == null) scans else scans.filter { it.scan.bodyPart.region == region }
        if (dateRangeOn && startMs != null && endMs != null) {
            val lo = startMs!!
            val hi = endMs!! + DAY_MS // inclusive of the end day
            list = list.filter { s -> s.observations.any { it.capturedAt in lo until hi } }
        }
        list
    }
    val included = candidates.filterNot { it.scan.id in deselected }
    val totalPhotos = included.sumOf { it.observations.size }
    val allSelected = candidates.isNotEmpty() && included.size == candidates.size

    ScreenScaffold(title = "Generate Report", onBack = onDismiss) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    SectionHeader("Body Area")
                    SunnyCard {
                        FlowRow(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SunnyChip("All", region == null, { region = null })
                            BodyRegion.entries.forEach { r ->
                                SunnyChip(r.label, region == r, { region = r })
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionHeader("Date Range")
                    SunnyCard {
                        Column {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Filter by date range", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge)
                                SunnyToggle(
                                    checked = dateRangeOn,
                                    onCheckedChange = { on ->
                                        if (on) showDatePicker = true
                                        else { dateRangeOn = false; startMs = null; endMs = null }
                                    },
                                    onColor = SunnyColors.Orange,
                                )
                            }
                            if (dateRangeOn && startMs != null && endMs != null) {
                                HorizontalDivider(color = SunnyColors.Divider)
                                Row(
                                    Modifier.fillMaxWidth().clickable { showDatePicker = true }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.CalendarMonth, null, tint = SunnyColors.Orange,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("${Format.date(startMs!!)}  –  ${Format.date(endMs!!)}",
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Scans · ${included.size} of ${candidates.size} selected")
                        Spacer(Modifier.weight(1f))
                        if (candidates.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (allSelected) deselected.addAll(candidates.map { it.scan.id })
                                    else deselected.clear()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    if (allSelected) "Clear all" else "Select all",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SunnyColors.OrangeText,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (candidates.isEmpty()) {
                    item {
                        Text("No tracked areas match these filters.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                } else {
                    items(candidates, key = { it.scan.id }) { scan ->
                        ScanSelectRow(
                            scan = scan,
                            selected = scan.scan.id !in deselected,
                            onToggle = {
                                if (scan.scan.id in deselected) deselected.remove(scan.scan.id)
                                else deselected.add(scan.scan.id)
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Visit Details")
                    OutlinedTextField(
                        value = visitNote,
                        onValueChange = { visitNote = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason for sharing (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    SectionHeader("Report Preview")
                    SunnyCard {
                        ReportCoverPreview(included.size, totalPhotos)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        if (saveState != ReportSaveState.Generating &&
                            saveState != ReportSaveState.Saved
                        ) {
                            showConfirm = true
                        }
                    },
                    enabled = included.isNotEmpty() &&
                        saveState != ReportSaveState.Generating &&
                        saveState != ReportSaveState.Saved,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (saveState == ReportSaveState.Saved) {
                            SunnyColors.Success
                        } else {
                            SunnyColors.Action
                        },
                        disabledContainerColor = when (saveState) {
                            ReportSaveState.Generating -> SunnyColors.Orange.copy(alpha = 0.68f)
                            ReportSaveState.Saved -> SunnyColors.Success
                            else -> SunnyColors.SurfaceMuted
                        },
                        disabledContentColor = when (saveState) {
                            ReportSaveState.Generating, ReportSaveState.Saved -> Color.White
                            else -> SunnyColors.TextTertiary
                        },
                    ),
                ) {
                    Icon(
                        if (saveState == ReportSaveState.Saved) Icons.Filled.Check
                        else Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.height(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (saveState) {
                            ReportSaveState.Idle -> "Save Report (${included.size})"
                            ReportSaveState.Generating -> "Generating report…"
                            ReportSaveState.Saved -> "Report saved"
                            ReportSaveState.Failed -> "Try saving again"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(top = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val status = when (saveState) {
                        ReportSaveState.Generating -> "Encrypting and saving on this device"
                        ReportSaveState.Saved -> "Saved securely on this device"
                        ReportSaveState.Failed -> "Couldn't save the report. Please try again."
                        ReportSaveState.Idle -> null
                    }
                    if (status != null) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (saveState == ReportSaveState.Failed) {
                                SunnyColors.Danger
                            } else {
                                SunnyColors.TextSecondary
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startMs,
            initialSelectedEndDateMillis = endMs,
        )
        var pendingDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
        LiquidGlassDialog(
            onDismiss = {
                pendingDateRange?.let { (start, end) ->
                    startMs = start
                    endMs = end
                    dateRangeOn = true
                }
                pendingDateRange = null
                showDatePicker = false
            },
        ) { requestDismiss ->
            DateRangePicker(
                state = state,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    titleContentColor = SunnyColors.TextSecondary,
                    headlineContentColor = SunnyColors.TextPrimary,
                    weekdayContentColor = SunnyColors.TextSecondary,
                    subheadContentColor = SunnyColors.TextSecondary,
                    navigationContentColor = SunnyColors.TextPrimary,
                    dayContentColor = SunnyColors.TextPrimary,
                    todayContentColor = SunnyColors.Orange,
                    todayDateBorderColor = SunnyColors.Orange,
                    selectedDayContainerColor = SunnyColors.Orange,
                    selectedDayContentColor = Color.White,
                    dayInSelectionRangeContainerColor = SunnyColors.OrangeSoft,
                    dayInSelectionRangeContentColor = SunnyColors.TextPrimary,
                ),
                modifier = Modifier.height(440.dp),
            )
            val completeRange = state.selectedStartDateMillis != null &&
                state.selectedEndDateMillis != null
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = requestDismiss) {
                    Text("Cancel", color = SunnyColors.TextSecondary)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    enabled = completeRange,
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis
                        if (s != null && e != null) {
                            pendingDateRange = s to e
                        }
                        requestDismiss()
                    },
                ) {
                    Text(
                        "Apply",
                        color = if (completeRange) SunnyColors.OrangeText else SunnyColors.TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Save Report to Device?") },
            text = {
                Text("A shareable visual tracking PDF containing aligned comparisons, " +
                    "automated-description provenance and uncropped originals will be saved on " +
                    "this device. It is not a medical diagnosis.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveState == ReportSaveState.Generating ||
                        saveState == ReportSaveState.Saved
                    ) {
                        return@TextButton
                    }
                    showConfirm = false
                    saveState = ReportSaveState.Generating
                    val toReport = included
                    val note = visitNote
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                ReportGenerator(context).generate(
                                    toReport,
                                    System.currentTimeMillis(),
                                    ReportOptions(visitNote = note),
                                )
                            }
                            saveState = ReportSaveState.Saved
                            delay(SunnyMotion.ScreenEnterMillis.toLong())
                            onOpenReport(file.nameWithoutExtension)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            saveState = ReportSaveState.Failed
                        }
                    }
                }) { Text("Save", color = SunnyColors.OrangeText) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
            containerColor = SunnyColors.Surface,
        )
    }
}

@Composable
private fun ReportCoverPreview(scanCount: Int, photoCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(88.dp).height(116.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SunnyColors.Surface)
                .border(1.dp, SunnyColors.Divider, RoundedCornerShape(10.dp))
                .padding(11.dp)
                .semantics {
                    contentDescription = "Report preview with $scanCount scans and $photoCount photos"
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(6.dp))
                        .background(SunnyColors.OrangeSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(SunnyColors.Orange))
                }
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(SunnyColors.TextPrimary),
                )
            }
            Spacer(Modifier.height(14.dp))
            PreviewLine(1f)
            Spacer(Modifier.height(7.dp))
            PreviewLine(0.68f)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp))
                        .background(SunnyColors.OrangeSoft),
                )
                Box(
                    Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp))
                        .background(SunnyColors.SurfaceMuted),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Sunny visual report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SunnyColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A private, on-device PDF preview",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            PreviewRow("Scans", scanCount.toString())
            PreviewRow("Photos", photoCount.toString())
        }
    }
}

@Composable
private fun PreviewLine(fraction: Float) {
    Box(
        Modifier.fillMaxWidth(fraction).height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(SunnyColors.SurfaceMuted),
    )
}

@Composable
private fun ScanSelectRow(scan: ScanWithObservations, selected: Boolean, onToggle: () -> Unit) {
    val latest = scan.latest
    SunnyCard(onClick = onToggle) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Action),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            } else {
                Box(Modifier.size(24.dp).clip(CircleShape)
                    .border(1.5.dp, SunnyColors.TextTertiary, CircleShape))
            }
            Spacer(Modifier.size(12.dp))
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SunnyColors.SurfaceMuted)) {
                if (latest != null) {
                    AsyncImage(EncryptedImage(latest.imagePath), null, Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(scan.scan.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(scan.scan.bodyPart.locationLine, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
                latest?.let {
                    Text(Format.date(it.capturedAt), style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
