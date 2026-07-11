package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.report.ReportGenerator
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.components.SunnyToggle
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DAY_MS = 24L * 60 * 60 * 1000

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
                        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SunnyChip("All", region == null, { region = null }, Modifier.weight(1f))
                            BodyRegion.entries.forEach { r ->
                                SunnyChip(r.label, region == r, { region = r }, Modifier.weight(1f))
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
                            Text(if (allSelected) "Clear all" else "Select all",
                                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.Orange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                    if (allSelected) deselected.addAll(candidates.map { it.scan.id })
                                    else deselected.clear()
                                }.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (candidates.isEmpty()) {
                    item {
                        Text("No scans match these filters.",
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
                    SectionHeader("Report Preview")
                    SunnyCard {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            PreviewRow("Scans included", included.size.toString())
                            HorizontalDivider(color = SunnyColors.Divider)
                            PreviewRow("Total photos", totalPhotos.toString())
                        }
                    }
                }
            }

            Button(
                onClick = { showConfirm = true },
                enabled = included.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
            ) {
                Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Report (${included.size})", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startMs,
            initialSelectedEndDateMillis = endMs,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        startMs = s; endMs = e; dateRangeOn = true
                    }
                    showDatePicker = false
                }) { Text("Apply", color = SunnyColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SunnyColors.TextSecondary)
                }
            },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Save Report to Device?") },
            text = {
                Text("A PDF report of the ${included.size} selected " +
                    "${if (included.size == 1) "scan" else "scans"} will be generated and saved to " +
                    "your device. This report is not a medical diagnosis.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    val toReport = included
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ReportGenerator(context).generate(toReport, System.currentTimeMillis())
                        }
                        onOpenReport(file.nameWithoutExtension)
                    }
                }) { Text("Save", color = SunnyColors.Orange) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
            containerColor = SunnyColors.Surface,
        )
    }
}

@Composable
private fun ScanSelectRow(scan: ScanWithObservations, selected: Boolean, onToggle: () -> Unit) {
    val latest = scan.latest
    SunnyCard(onClick = onToggle) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Orange),
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
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
