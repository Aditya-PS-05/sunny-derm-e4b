package com.sunny.skin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.sunny.skin.ui.i18n.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.R
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.data.model.AnalysisComparison
import com.sunny.skin.ui.AnalysisPhase
import com.sunny.skin.ui.AnalysisState
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.ChangeSummaryCard
import com.sunny.skin.ui.components.CircleButton
import com.sunny.skin.ui.components.MetaChip
import com.sunny.skin.ui.components.ApproximateSizeDialog
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.util.Format
import kotlinx.coroutines.delay

@Composable
fun ReviewScanScreen(
    vm: SunnyViewModel,
    onSaved: (scanId: String, wasRecheck: Boolean, checkSessionId: String?) -> Unit,
    onDiscard: (targetScanId: String?, checkSessionId: String?) -> Unit,
    onRetake: () -> Unit,
    onOpenAnalysisSetup: () -> Unit,
) {
    val capture by vm.capture.collectAsStateWithLifecycle()
    val scans by vm.scans.collectAsStateWithLifecycle()
    val previousObservation = capture.targetScanId?.let { targetId ->
        scans.firstOrNull { it.scan.id == targetId }?.latest
    }
    val bitmap = capture.bitmap
    var showPartPicker by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val ready = capture.analysis is AnalysisState.Ready
    val analysisRunning = capture.analysis == AnalysisState.Idle ||
        capture.analysis is AnalysisState.Running
    val hostView = LocalView.current
    DisposableEffect(hostView, analysisRunning) {
        val previous = hostView.keepScreenOn
        if (analysisRunning) hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = previous }
    }
    val isRecheck = capture.targetScanId != null
    val motionEnabled = rememberSunnyMotionEnabled()
    val stateOffset = with(LocalDensity.current) { 8.dp.roundToPx() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Top action bar: destructive action on the left, reversible retake on the right.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onClick = { showDiscardConfirm = true }) {
                Icon(Icons.Outlined.DeleteOutline, "Discard", tint = SunnyColors.Danger,
                    modifier = Modifier.size(26.dp))
            }
            Text(
                stringResource(
                    if (isRecheck) R.string.review_follow_up_title else R.string.review_photo_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            CircleButton(onClick = {
                vm.prepareRetake()
                onRetake()
            }) {
                Icon(Icons.Filled.Replay, "Retake photo", tint = SunnyColors.TextPrimary,
                    modifier = Modifier.size(20.dp))
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

            AnalysisStatusLine(capture.analysis)
            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = capture.analysis,
                transitionSpec = {
                    val spatialEnter = if (motionEnabled) {
                        slideInVertically(
                            animationSpec = tween(
                                SunnyMotion.StateMillis,
                                easing = SunnyMotion.EaseOut,
                            ),
                            initialOffsetY = { stateOffset },
                        )
                    } else {
                        EnterTransition.None
                    }
                    ((fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)) + spatialEnter) togetherWith
                        fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut))).using(
                        SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ -> snap() },
                        ),
                    )
                },
                label = "Analysis state",
            ) { state ->
                when (state) {
                    AnalysisState.Idle -> AnalysingState(AnalysisPhase.PREPARING, vm::cancelAnalysis)
                    is AnalysisState.Running -> AnalysingState(state.phase, vm::cancelAnalysis)
                    is AnalysisState.Ready -> {
                        val changes = previousObservation?.let {
                            AnalysisComparison.changes(it.analysis.toAnalysis(), state.result.analysis)
                        }.orEmpty()
                        Column {
                            if (previousObservation != null) {
                                ChangeSummaryCard(
                                    sinceDate = Format.date(previousObservation.capturedAt),
                                    changes = changes,
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            AnalysisCard(
                                state.result.analysis,
                                changedLabels = changes.mapTo(mutableSetOf()) { it.label },
                            )
                        }
                    }
                    AnalysisState.Unreadable -> UnreadableState(
                        onRetry = { vm.retryAnalysis() },
                        onRetake = {
                            vm.prepareRetake()
                            onRetake()
                        },
                    )
                    AnalysisState.ModelUnavailable -> ModelUnavailableState(
                        onRetry = { vm.retryAnalysis() },
                        onOpenSetup = onOpenAnalysisSetup,
                    )
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

        val saveLabel = when (capture.analysis) {
            is AnalysisState.Ready -> stringResource(
                if (isRecheck) R.string.save_follow_up else R.string.save_photo,
            )
            AnalysisState.Idle, is AnalysisState.Running -> stringResource(R.string.analysing_photo)
            else -> stringResource(R.string.resolve_analysis_to_save)
        }
        Button(
            onClick = { vm.saveCapture(System.currentTimeMillis(), onSaved) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunnyColors.Action,
                disabledContainerColor = SunnyColors.SurfaceMuted,
                disabledContentColor = SunnyColors.TextSecondary,
            ),
        ) {
            if (!ready && (capture.analysis is AnalysisState.Running || capture.analysis == AnalysisState.Idle)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = SunnyColors.TextSecondary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(saveLabel, fontWeight = FontWeight.SemiBold)
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

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = SunnyColors.Surface,
            title = { Text("Discard this photo?") },
            text = {
                Text(
                    if (isRecheck) {
                        "This follow-up has not been saved. Discarding it cannot be undone."
                    } else {
                        "This photo and its analysis have not been saved. Discarding them cannot be undone."
                    },
                    color = SunnyColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    val targetScanId = capture.targetScanId
                    val checkSessionId = capture.checkSessionId
                    vm.discardCapture()
                    onDiscard(targetScanId, checkSessionId)
                }) {
                    Text("Discard", color = SunnyColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep photo", color = SunnyColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AnalysisStatusLine(state: AnalysisState) {
    val labels = listOf("Prepare", "Check", "Analyse", "Done")
    val active = when (state) {
        AnalysisState.Idle -> 0
        is AnalysisState.Running -> if (state.phase == AnalysisPhase.PREPARING) 1 else 2
        is AnalysisState.PoorQuality, AnalysisState.Unreadable -> 1
        AnalysisState.ModelUnavailable, AnalysisState.Cancelled -> 2
        is AnalysisState.Ready -> 3
    }
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Analysis progress: ${labels[active]}"
            progressBarRangeInfo = ProgressBarRangeInfo(active.toFloat(), 0f..3f, 3)
        },
        verticalAlignment = Alignment.Top,
    ) {
        labels.forEachIndexed { index, label ->
            val reached = index <= active
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(10.dp).graphicsLayer {
                            val dotScale = if (index == active) 1f else 0.8f
                            scaleX = dotScale
                            scaleY = dotScale
                        }.clip(CircleShape)
                            .background(if (reached) SunnyColors.Orange else SunnyColors.SurfaceMuted),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) SunnyColors.TextPrimary else SunnyColors.TextTertiary,
                    fontWeight = if (index == active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (index < labels.lastIndex) {
                Box(
                    Modifier.weight(0.32f).padding(top = 4.dp).height(2.dp)
                        .background(
                            if (index < active) SunnyColors.OrangeLight else SunnyColors.Divider,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun ModelUnavailableState(
    onRetry: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val server = com.sunny.skin.AppMode.serverActive(context)
    val canRetry = com.sunny.skin.inference.ModelProvider.realModelAvailable(context)
    val unavailableMessage = if (!server &&
        !com.sunny.skin.inference.ModelProvider.localDeviceSupported()
    ) {
        com.sunny.skin.inference.DeviceInferenceCapabilities.unavailableReason()
    } else {
        com.sunny.skin.AppMode.unavailableMessage(server)
    }
    Column {
        Text(
            "Analysis unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "This scan was not analysed. $unavailableMessage",
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onOpenSetup) {
                Text("Analysis settings", color = SunnyColors.OrangeText)
            }
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text("Try again", color = SunnyColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun AnalysingState(phase: AnalysisPhase, onCancel: () -> Unit) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            elapsedSeconds++
        }
    }
    val (title, note) = when (phase) {
        AnalysisPhase.PREPARING -> "Preparing your photo" to
            "Checking image quality before analysis."
        AnalysisPhase.REMOTE -> "Analysing securely in the cloud" to
            "Your photo is being processed securely. This usually takes under a minute."
        AnalysisPhase.ON_DEVICE -> "Analysing privately on this phone" to
            "Your photo stays on this phone. The first run can take a few minutes."
    }
    SunnyCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = SunnyColors.Orange,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (phase == AnalysisPhase.PREPARING) {
                    "Keep Sunny open until analysis finishes."
                } else {
                    "Elapsed ${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')} · " +
                        "Keep Sunny open until analysis finishes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextTertiary,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel analysis", color = SunnyColors.TextSecondary)
            }
        }
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
