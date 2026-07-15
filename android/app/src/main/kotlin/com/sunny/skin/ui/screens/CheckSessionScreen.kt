package com.sunny.skin.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.CheckSessionStatus
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

@Composable
fun CheckSessionScreen(
    vm: SunnyViewModel,
    onBack: () -> Unit,
    onCapture: () -> Unit,
) {
    val scans by vm.scans.collectAsStateWithLifecycle()
    val session by vm.checkSession.collectAsStateWithLifecycle()
    var showEndConfirm by remember { mutableStateOf(false) }

    val scansById = scans.associateBy { it.scan.id }
    val validItems = session?.items.orEmpty().filter { it.scanId in scansById }
    val resolved = validItems.count { it.status != CheckSessionStatus.PENDING }
    val next = validItems.firstOrNull { it.status == CheckSessionStatus.PENDING }

    fun capture(scanId: String) {
        if (vm.beginCheckSessionRecheck(scanId)) onCapture()
    }

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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "$resolved of ${validItems.size} areas reviewed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { resolved.toFloat() / validItems.size },
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
            ) {
                Icon(
                    if (next == null) Icons.Filled.CheckCircle else Icons.Filled.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(if (next == null) "Finish session" else "Continue", fontWeight = FontWeight.SemiBold)
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
            ) { Text("Start photo check") }
        }
    }
}

@Composable
private fun SessionItemRow(
    scan: ScanWithObservations,
    status: CheckSessionStatus,
    onCapture: () -> Unit,
    onSkip: () -> Unit,
) {
    SunnyCard(onClick = if (status == CheckSessionStatus.COMPLETED) null else onCapture) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    when (status) {
                        CheckSessionStatus.COMPLETED -> SunnyColors.Success.copy(alpha = 0.14f)
                        CheckSessionStatus.SKIPPED -> SunnyColors.SurfaceMuted
                        CheckSessionStatus.PENDING -> SunnyColors.OrangeSoft
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (status) {
                        CheckSessionStatus.COMPLETED -> Icons.Filled.CheckCircle
                        CheckSessionStatus.SKIPPED -> Icons.Filled.Replay
                        CheckSessionStatus.PENDING -> Icons.Filled.Checklist
                    },
                    contentDescription = null,
                    tint = when (status) {
                        CheckSessionStatus.COMPLETED -> SunnyColors.Success
                        CheckSessionStatus.SKIPPED -> SunnyColors.TextTertiary
                        CheckSessionStatus.PENDING -> SunnyColors.Orange
                    },
                    modifier = Modifier.size(21.dp),
                )
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
