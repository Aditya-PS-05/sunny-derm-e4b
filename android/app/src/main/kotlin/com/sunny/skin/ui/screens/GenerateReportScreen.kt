package com.sunny.skin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.report.ReportGenerator
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GenerateReportScreen(vm: SunnyViewModel, onDismiss: () -> Unit, onOpenReport: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scans by vm.scans.collectAsStateWithLifecycle()

    var region by remember { mutableStateOf<BodyRegion?>(null) }
    var byDate by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val selected = remember(scans, region) {
        if (region == null) scans else scans.filter { it.scan.bodyPart.region == region }
    }
    val totalPhotos = selected.sumOf { it.observations.size }

    ScreenScaffold(
        title = "Generate Report",
        onBack = onDismiss,
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
        ) {
            SectionHeader("Body Area")
            SunnyCard {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SunnyChip("All", region == null, { region = null }, Modifier.weight(1f))
                    BodyRegion.entries.forEach { r ->
                        SunnyChip(r.label, region == r, { region = r }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Only scans in the selected body area will be included in the report.",
                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(20.dp))

            SectionHeader("Date Range")
            SunnyCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter by date range", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    com.sunny.skin.ui.components.SunnyToggle(
                        checked = byDate, onCheckedChange = { byDate = it },
                        onColor = SunnyColors.Orange,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionHeader("Report Preview")
            SunnyCard {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PreviewRow("Scans included", selected.size.toString())
                    HorizontalDivider(color = SunnyColors.Divider)
                    PreviewRow("Total photos", totalPhotos.toString())
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { showConfirm = true },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
            ) {
                Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Report to Device", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Save Report to Device?") },
            text = {
                Text("A PDF skin examination report will be generated and saved to your " +
                    "device. This report is not a medical diagnosis.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ReportGenerator(context).generate(selected, System.currentTimeMillis())
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
private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
