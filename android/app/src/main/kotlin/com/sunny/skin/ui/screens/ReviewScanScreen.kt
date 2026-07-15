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
import androidx.compose.material.icons.filled.Straighten
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
import com.sunny.skin.ui.AnalysisPhase
import com.sunny.skin.ui.AnalysisState
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.MetaChip
import com.sunny.skin.ui.components.ApproximateSizeDialog
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors

@Composable
fun ReviewScanScreen(
    vm: SunnyViewModel,
    onSaved: (scanId: String, wasRecheck: Boolean, checkSessionId: String?) -> Unit,
    onDiscard: (targetScanId: String?, checkSessionId: String?) -> Unit,
    onRetake: () -> Unit,
) {
    val capture by vm.capture.collectAsStateWithLifecycle()
    val bitmap = capture.bitmap
    var showPartPicker by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    val ready = capture.analysis is AnalysisState.Ready
    val isRecheck = capture.targetScanId != null

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Top action bar: delete / redo / confirm
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable {
                        val targetScanId = capture.targetScanId
                        val checkSessionId = capture.checkSessionId
                        vm.discardCapture()
                        onDiscard(targetScanId, checkSessionId)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DeleteOutline, "Discard", tint = SunnyColors.Danger,
                    modifier = Modifier.size(26.dp))
            }
            Text(if (isRecheck) "Review Follow-up" else "Review Scan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // Retake + confirm grouped into one compact command surface.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SunnyColors.Surface,
                shadowElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clickable {
                            vm.prepareRetake()
                            onRetake()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Replay, "Retake photo", tint = SunnyColors.TextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.width(1.dp).height(26.dp).background(SunnyColors.Divider))
                    Box(
                        Modifier.size(48.dp).clickable(enabled = ready) {
                            vm.saveCapture(System.currentTimeMillis(), onSaved)
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
                if (isRecheck) {
                    MetaChip("Follow-up")
                } else {
                    val nextType = if (capture.scanType == ScanType.SINGLE) ScanType.TRACKED else ScanType.SINGLE
                    SunnyChip(capture.scanType.prefix, selected = true,
                        onClick = { vm.setScanType(nextType) })
                }
                MetaChip(
                    capture.bodyPart.locationLine,
                    modifier = if (isRecheck) Modifier else Modifier.clickable { showPartPicker = true },
                )
                MetaChip(capture.bodyPart.side.label)
                capture.alignment?.let { alignment ->
                    MetaChip(
                        "Framing · " + when {
                            alignment.score >= 0.55f -> "High match"
                            alignment.score >= 0.32f -> "Moderate match"
                            else -> "Low match"
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SunnyCard(onClick = { showSizeDialog = true }) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = null,
                        tint = SunnyColors.Orange,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Approximate size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            capture.measurement?.let {
                                "Reference-based estimate · ${it.formattedSize()}"
                            } ?: "Optional reference-based estimate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (val state = capture.analysis) {
                AnalysisState.Idle -> AnalysingState(AnalysisPhase.PREPARING, vm::cancelAnalysis)
                is AnalysisState.Running -> AnalysingState(state.phase, vm::cancelAnalysis)
                is AnalysisState.Ready -> AnalysisCard(state.result.analysis)
                AnalysisState.Unreadable -> UnreadableState(
                    onRetry = { vm.retryAnalysis() },
                    onRetake = {
                        vm.prepareRetake()
                        onRetake()
                    },
                )
                AnalysisState.ModelUnavailable -> ModelUnavailableState(onRetry = { vm.retryAnalysis() })
                AnalysisState.Cancelled -> CancelledState(onRetry = vm::retryAnalysis)
                is AnalysisState.PoorQuality -> PoorQualityState(
                    issue = state.issue,
                    onContinue = { vm.retryAnalysis() },
                    onRetake = {
                        vm.prepareRetake()
                        onRetake()
                    },
                )
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

    if (showSizeDialog && bitmap != null) {
        ApproximateSizeDialog(
            bitmap = bitmap,
            initial = capture.measurement,
            onSave = {
                vm.setApproximateMeasurement(it)
                showSizeDialog = false
            },
            onRemove = {
                vm.setApproximateMeasurement(null)
                showSizeDialog = false
            },
            onDismiss = { showSizeDialog = false },
        )
    }
}

@Composable
private fun ModelUnavailableState(onRetry: () -> Unit) {
    val server = com.sunny.skin.AppMode.serverActive(androidx.compose.ui.platform.LocalContext.current)
    Column {
        Text(
            "Analysis unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "This scan was not analysed. ${com.sunny.skin.AppMode.unavailableMessage(server)}",
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Try again", color = SunnyColors.OrangeText) }
    }
}

@Composable
private fun AnalysingState(phase: AnalysisPhase, onCancel: () -> Unit) {
    val (title, note) = when (phase) {
        AnalysisPhase.PREPARING -> "Preparing photo…" to "Checking image quality and preparing analysis."
        AnalysisPhase.REMOTE -> "Sending to beta server…" to
            "This beta uses the configured GPU server. Keep Sunny open while it finishes."
        AnalysisPhase.ON_DEVICE -> "Analysing on this device…" to
            "Your photo stays on this phone during analysis."
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = SunnyColors.Orange, strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(note,
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel analysis", color = SunnyColors.TextSecondary) }
    }
}

@Composable
private fun CancelledState(onRetry: () -> Unit) {
    Column {
        Text("Analysis cancelled", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("The photo remains private and unsaved until you retry or discard it.",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Run analysis") }
    }
}

@Composable
private fun UnreadableState(onRetry: () -> Unit, onRetake: () -> Unit) {
    Column {
        Text("Couldn't read this image", style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Try again with a well-lit, close-up, filled-frame photo of a single spot.",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onRetake) { Text("Retake photo", color = SunnyColors.OrangeText) }
            TextButton(onClick = onRetry) { Text("Re-run analysis", color = SunnyColors.TextSecondary) }
        }
    }
}

@Composable
private fun PoorQualityState(
    issue: com.sunny.skin.util.PhotoQualityIssue,
    onContinue: () -> Unit,
    onRetake: () -> Unit,
) {
    Column {
        Text(
            "Retake recommended",
            style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            issue.userMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onRetake) {
                Text("Retake photo", color = SunnyColors.OrangeText)
            }
            TextButton(onClick = onContinue) {
                Text("Analyse anyway", color = SunnyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun BodyPartPicker(selected: BodyPart, onSelect: (BodyPart) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = SunnyColors.OrangeText) } },
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
