package com.sunny.skin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.CheckSessionStatus
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.BalloonDrop
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled

@Composable
fun CheckSessionScreen(
    vm: SunnyViewModel,
    onBack: () -> Unit,
    onCapture: () -> Unit,
) {
    val scans by vm.scans.collectAsStateWithLifecycle()
    val session by vm.checkSession.collectAsStateWithLifecycle()
    var showEndConfirm by remember { mutableStateOf(false) }
    val motionEnabled = rememberSunnyMotionEnabled()

    val scansById = scans.associateBy { it.scan.id }
    val validItems = session?.items.orEmpty().filter { it.scanId in scansById }
    val resolved = validItems.count { it.status != CheckSessionStatus.PENDING }
    val next = validItems.firstOrNull { it.status == CheckSessionStatus.PENDING }
    val progressTarget = if (validItems.isEmpty()) 0f else resolved.toFloat() / validItems.size
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = if (motionEnabled) {
            tween(durationMillis = SunnyMotion.StateMillis, easing = SunnyMotion.EaseInOut)
        } else {
            snap()
        },
        label = "Photo check progress",
    )
    val isComplete = validItems.isNotEmpty() && resolved == validItems.size
    val sessionWasCompleteOnLoad = session?.items?.let { items ->
        items.isNotEmpty() && items.all { it.status != CheckSessionStatus.PENDING }
    } == true
    var showCompletionCheck by remember(session?.id) { mutableStateOf(false) }
    var completionWasObserved by rememberSaveable(session?.id) {
        mutableStateOf(sessionWasCompleteOnLoad)
    }
    var celebrationConsumed by rememberSaveable(session?.id) {
        mutableStateOf(sessionWasCompleteOnLoad)
    }
    var showCelebration by remember(session?.id) { mutableStateOf(false) }
    LaunchedEffect(session?.id, isComplete) {
        // A recreated destination may already be complete. Starting from false
        // guarantees one restrained entrance after the first composed frame.
        showCompletionCheck = isComplete
    }
    LaunchedEffect(session?.id, isComplete) {
        if (isComplete && !completionWasObserved && !celebrationConsumed) {
            celebrationConsumed = true
            showCelebration = true
        }
        completionWasObserved = isComplete
    }

    fun capture(scanId: String) {
        if (vm.beginCheckSessionRecheck(scanId)) onCapture()
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(title = "Photo Check", onBack = onBack) { inner ->
            if (session == null || validItems.isEmpty()) {
                EmptySession(
                    hasScans = scans.any { it.latest != null },
                    modifier = Modifier.fillMaxSize().padding(inner),
                    onStart = { vm.startCheckSession() },
                )
                return@ScreenScaffold
            }

            Column(Modifier.fillMaxSize().padding(inner)) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$resolved of ${validItems.size} areas reviewed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = showCompletionCheck,
                                transitionSpec = {
                                    ContentTransform(
                                        targetContentEnter = fadeIn(
                                            tween(
                                                SunnyMotion.StateMillis,
                                                easing = SunnyMotion.EaseOut,
                                            ),
                                        ) +
                                            if (motionEnabled) {
                                                scaleIn(
                                                    animationSpec = tween(
                                                        SunnyMotion.StateMillis,
                                                        easing = SunnyMotion.EaseOut,
                                                    ),
                                                    initialScale = 0.88f,
                                                )
                                            } else {
                                                EnterTransition.None
                                            },
                                        initialContentExit = fadeOut(
                                            tween(
                                                SunnyMotion.StateMillis,
                                                easing = SunnyMotion.EaseOut,
                                            ),
                                        ) +
                                            if (motionEnabled) {
                                                scaleOut(
                                                    animationSpec = tween(
                                                        SunnyMotion.StateMillis,
                                                        easing = SunnyMotion.EaseOut,
                                                    ),
                                                    targetScale = 0.96f,
                                                )
                                            } else {
                                                ExitTransition.None
                                            },
                                        sizeTransform = null,
                                    )
                                },
                                label = "Photo check completion",
                            ) { complete ->
                                if (complete) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Photo check complete",
                                        tint = SunnyColors.Success,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Spacer(Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = SunnyColors.Orange,
                        trackColor = SunnyColors.SurfaceMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A personal photo checklist. It does not confirm a complete skin examination.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                items(validItems, key = { it.scanId }) { item ->
                    val scan = scansById.getValue(item.scanId)
                    SessionItemRow(
                        scan = scan,
                        status = item.status,
                        motionEnabled = motionEnabled,
                        onCapture = { capture(item.scanId) },
                        onSkip = { vm.skipCheckSessionItem(item.scanId) },
                    )
                }
            }

                Button(
                    onClick = {
                        if (next == null) {
                            vm.finishCheckSession()
                            onBack()
                        } else {
                            capture(next.scanId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
                ) {
                    Icon(
                        if (next == null) Icons.Filled.CheckCircle else Icons.Filled.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (next == null) "Finish session" else "Continue",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (next != null) {
                    TextButton(
                        onClick = { showEndConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Text("End session", color = SunnyColors.TextSecondary)
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showCelebration) {
            BalloonDrop(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                durationMs = 1_200,
                motionEnabled = motionEnabled,
                compact = true,
                onFinished = { showCelebration = false },
            )
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End this photo check?") },
            text = { Text("The checklist progress will be cleared. Your saved photos are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirm = false
                    vm.finishCheckSession()
                    onBack()
                }) { Text("End session", color = SunnyColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) {
                    Text("Keep checking", color = SunnyColors.TextSecondary)
                }
            },
            containerColor = SunnyColors.Surface,
        )
    }
}

@Composable
private fun EmptySession(hasScans: Boolean, modifier: Modifier, onStart: () -> Unit) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Checklist, null, tint = SunnyColors.Orange, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasScans) "Review your saved areas" else "No saved areas yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasScans) {
                "Create a resumable checklist and photograph each saved area again."
            } else {
                "Save an area first, then return here to create a photo checklist."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (hasScans) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
            ) { Text("Start photo check") }
        }
    }
}

@Composable
private fun SessionItemRow(
    scan: ScanWithObservations,
    status: CheckSessionStatus,
    motionEnabled: Boolean,
    onCapture: () -> Unit,
    onSkip: () -> Unit,
) {
    var visualStatusName by rememberSaveable(scan.scan.id) { mutableStateOf(status.name) }
    LaunchedEffect(status) {
        // Saved visual state survives Camera/Review. On return, only the row whose
        // persisted status changed transitions from its prior icon and color.
        visualStatusName = status.name
    }
    val visualStatus = CheckSessionStatus.valueOf(visualStatusName)
    val iconBackground by animateColorAsState(
        targetValue = when (visualStatus) {
            CheckSessionStatus.COMPLETED -> SunnyColors.Success.copy(alpha = 0.14f)
            CheckSessionStatus.SKIPPED -> SunnyColors.SurfaceMuted
            CheckSessionStatus.PENDING -> SunnyColors.OrangeSoft
        },
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseInOut),
        label = "Photo check row background",
    )
    val iconTint by animateColorAsState(
        targetValue = when (visualStatus) {
            CheckSessionStatus.COMPLETED -> SunnyColors.Success
            CheckSessionStatus.SKIPPED -> SunnyColors.TextTertiary
            CheckSessionStatus.PENDING -> SunnyColors.Orange
        },
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseInOut),
        label = "Photo check row icon color",
    )
    SunnyCard(onClick = if (status == CheckSessionStatus.COMPLETED) null else onCapture) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = visualStatus,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(
                                tween(
                                    SunnyMotion.StateMillis,
                                    easing = SunnyMotion.EaseOut,
                                ),
                            ) +
                                if (motionEnabled) {
                                    scaleIn(
                                        tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
                                        initialScale = 0.88f,
                                    )
                                } else {
                                    EnterTransition.None
                                },
                            initialContentExit = fadeOut(
                                tween(
                                    SunnyMotion.StateMillis,
                                    easing = SunnyMotion.EaseOut,
                                ),
                            ) +
                                if (motionEnabled) {
                                    scaleOut(
                                        tween(
                                            SunnyMotion.StateMillis,
                                            easing = SunnyMotion.EaseOut,
                                        ),
                                        targetScale = 0.96f,
                                    )
                                } else {
                                    ExitTransition.None
                                },
                            sizeTransform = null,
                        )
                    },
                    label = "Photo check row status",
                ) { animatedStatus ->
                    Icon(
                        when (animatedStatus) {
                            CheckSessionStatus.COMPLETED -> Icons.Filled.CheckCircle
                            CheckSessionStatus.SKIPPED -> Icons.Filled.Replay
                            CheckSessionStatus.PENDING -> Icons.Filled.Checklist
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(scan.scan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (status) {
                        CheckSessionStatus.COMPLETED -> "Follow-up saved"
                        CheckSessionStatus.SKIPPED -> "Skipped · tap to recheck"
                        CheckSessionStatus.PENDING -> scan.scan.bodyPart.locationLine
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                )
            }
            if (status == CheckSessionStatus.PENDING) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = SunnyColors.TextSecondary)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Recheck ${scan.scan.name}",
                    tint = SunnyColors.TextTertiary,
                )
            }
        }
    }
}
