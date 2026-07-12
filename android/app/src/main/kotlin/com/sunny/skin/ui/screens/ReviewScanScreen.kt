package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.ui.AnalysisState
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.MetaChip
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors

@Composable
fun ReviewScanScreen(vm: SunnyViewModel, onSaved: () -> Unit, onDiscard: () -> Unit) {
    val capture by vm.capture.collectAsStateWithLifecycle()
    val bitmap = capture.bitmap
    var showPartPicker by remember { mutableStateOf(false) }
    val ready = capture.analysis is AnalysisState.Ready

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Top action bar: delete / redo / confirm
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable { vm.discardCapture(); onDiscard() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DeleteOutline, "Discard", tint = SunnyColors.Danger,
                    modifier = Modifier.size(26.dp))
            }
            Text("Review Scan", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // Redo + confirm grouped into one pill (reference design).
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SunnyColors.Surface,
                shadowElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp, 40.dp).clickable { vm.retryAnalysis() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Replay, "Re-run", tint = SunnyColors.TextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.width(1.dp).height(22.dp).background(SunnyColors.Divider))
                    Box(
                        Modifier.size(48.dp, 40.dp).clickable(enabled = ready) {
                            vm.saveCapture(System.currentTimeMillis()) { onSaved() }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, "Save",
                            tint = if (ready) SunnyColors.TextPrimary else SunnyColors.TextTertiary,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            // Captured photo
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(20.dp))
                    .background(SunnyColors.SurfaceMuted),
            ) {
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Metadata chips: scan type, location, side
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val nextType = if (capture.scanType == ScanType.SINGLE) ScanType.TRACKED else ScanType.SINGLE
                SunnyChip(capture.scanType.prefix, selected = true,
                    onClick = { vm.setScanType(nextType) })
                MetaChip(
                    capture.bodyPart.locationLine,
                    modifier = Modifier.clickable { showPartPicker = true },
                )
                MetaChip(capture.bodyPart.side.label)
            }
            Spacer(Modifier.height(16.dp))

            when (val state = capture.analysis) {
                AnalysisState.Idle, AnalysisState.Running -> AnalysingState()
                is AnalysisState.Ready -> AnalysisCard(state.result.analysis)
                AnalysisState.Unreadable -> UnreadableState(onRetry = { vm.retryAnalysis() })
                AnalysisState.ModelUnavailable -> ModelUnavailableState()
            }
        }
    }

    if (showPartPicker) {
        BodyPartPicker(
            selected = capture.bodyPart,
            onSelect = { vm.setBodyPart(it); showPartPicker = false },
            onDismiss = { showPartPicker = false },
        )
    }
}

@Composable
private fun ModelUnavailableState() {
    Column {
        Text(
            "AI model required",
            style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "This scan was not analysed. Install the real on-device model from Settings before scanning.",
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
    }
}

@Composable
private fun AnalysingState() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = SunnyColors.Orange, strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("Sunny is analysing…", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(com.sunny.skin.AppMode.analysingNote,
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary)
    }
}

@Composable
private fun UnreadableState(onRetry: () -> Unit) {
    Column {
        Text("Couldn't read this image", style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Try again with a well-lit, close-up, filled-frame photo of a single spot.",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Re-run analysis", color = SunnyColors.Orange) }
    }
}

@Composable
private fun BodyPartPicker(selected: BodyPart, onSelect: (BodyPart) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = SunnyColors.Orange) } },
        title = { Text("Body area") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BodyRegion.entries.forEach { region ->
                    Text(region.label.uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = SunnyColors.TextTertiary, modifier = Modifier.padding(vertical = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BodyPart.forRegion(region).forEach { part ->
                            SunnyChip(part.label, selected = part == selected, onClick = { onSelect(part) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        containerColor = SunnyColors.Surface,
    )
}
