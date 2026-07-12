package com.sunny.skin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.AbcdeAnswer
import com.sunny.skin.data.AbcdeItem
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.RecheckResult
import com.sunny.skin.ui.components.AbcdeCard
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.ChangeSummaryCard
import com.sunny.skin.ui.components.CircleButton
import com.sunny.skin.ui.components.MetaChip
import com.sunny.skin.ui.components.ReCheckReminderDialog
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.rememberNotificationRequester
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.BitmapLoader
import com.sunny.skin.util.Format
import java.io.File

@Composable
fun ScanDetailScreen(
    vm: SunnyViewModel, scanId: String, onBack: () -> Unit, onEdit: () -> Unit, onCompare: () -> Unit,
) {
    val scan by vm.scan(scanId).collectAsStateWithLifecycle(initialValue = null)
    val modelAvailable by vm.modelAvailable.collectAsStateWithLifecycle()
    val data = scan
    var showDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = data?.scan?.name ?: "Scan",
        onBack = onBack,
        trailing = if (data == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(onClick = { showDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete", tint = SunnyColors.Danger,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(SunnyColors.Surface)
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Edit", color = SunnyColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { inner ->
        if (data == null) return@ScreenScaffold
        val timeline = data.timeline
        val latest = timeline.firstOrNull() ?: return@ScreenScaffold
        val previous = timeline.getOrNull(1)

        // Literal field differences only. No risk, stability, or urgency score.
        val changedFieldsSet = previous?.let {
            changedFields(it.analysis.toAnalysis(), latest.analysis.toAnalysis())
        } ?: emptySet()
        val aspectLabels = changedFieldsSet.filter { it != "Summary" }

        val context = LocalContext.current

        // Per-spot ABCDE self-check answers (persisted; mirrored locally for the UI).
        val abcdeAnswers = remember(scanId) {
            mutableStateMapOf<AbcdeItem, AbcdeAnswer>().apply { putAll(vm.abcde(scanId)) }
        }

        var showReminder by remember { mutableStateOf(false) }
        val requestNotif = rememberNotificationRequester()
        var adding by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                adding = true
                val bmp = BitmapLoader.fromUri(context, uri)
                vm.addRecheck(scanId, bmp) { result ->
                    adding = false
                    android.widget.Toast.makeText(
                        context,
                        when (result) {
                            RecheckResult.SAVED -> "Follow-up photo added — now you can compare."
                            RecheckResult.UNREADABLE -> "Couldn't read that image. Try a clearer photo."
                            RecheckResult.MODEL_UNAVAILABLE ->
                                "Install the AI model from Settings before adding a follow-up."
                        },
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            ObservationImage(latest)
            Spacer(Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip(data.scan.bodyPart.locationLine)
                MetaChip(Format.date(latest.capturedAt))
                MetaChip(Format.time(latest.capturedAt))
            }
            Spacer(Modifier.height(16.dp))

            // Neutral description comparison; never a stability or urgency verdict.
            if (previous != null) {
                ChangeSummaryCard(
                    sinceDate = Format.date(previous.capturedAt),
                    changedAspects = aspectLabels,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Add a follow-up photo (builds the timeline that Compare needs).
            SunnyCard(onClick = {
                if (!modelAvailable) {
                    android.widget.Toast.makeText(
                        context,
                        "Install the AI model from Settings before adding a follow-up.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                } else if (!adding) {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (adding) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = SunnyColors.Orange, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AddAPhoto, null, tint = SunnyColors.Orange,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !modelAvailable -> "AI model required"
                                adding -> "Analysing…"
                                else -> "Add a follow-up photo"
                            },
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (modelAvailable) "Re-photograph this spot to compare descriptions"
                            else "Install the real model from Settings to enable analysis",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Compare over time — only when there are 2+ photos.
            if (timeline.size > 1) {
                SunnyCard(onClick = onCompare) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Compare, null, tint = SunnyColors.Orange,
                                modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Compare over time", style = MaterialTheme.typography.titleMedium)
                            Text("Fade or wipe between ${timeline.size} photos to spot change",
                                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = SunnyColors.TextTertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Re-check reminder
            SunnyCard(onClick = { showReminder = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = SunnyColors.Orange,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remind me to re-check", style = MaterialTheme.typography.titleMedium)
                        Text("Get a nudge to re-photograph this spot",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = SunnyColors.TextTertiary)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Model's structured description, with changed fields highlighted (F-13)
            // as routing information, never a verdict (F-14).
            AnalysisCard(latest.analysis.toAnalysis(), changedLabels = changedFieldsSet)

            Spacer(Modifier.height(16.dp))
            AbcdeCard(
                answers = abcdeAnswers,
                onAnswer = { item, ans ->
                    abcdeAnswers[item] = ans
                    vm.setAbcde(scanId, item, ans)
                },
            )

            if (timeline.size > 1) {
                Spacer(Modifier.height(24.dp))
                Text("History", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                timeline.drop(1).forEach { obs ->
                    HistoryRow(obs)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (showReminder) {
            ReCheckReminderDialog(
                onPick = { days ->
                    showReminder = false
                    requestNotif { granted ->
                        vm.scheduleScanReminder(scanId, data.scan.bodyPart.label, days)
                        android.widget.Toast.makeText(
                            context,
                            if (granted) "Reminder set — we'll nudge you to re-check."
                            else "Reminder saved. Turn on notifications to get the nudge.",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onDismiss = { showReminder = false },
            )
        }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                containerColor = SunnyColors.Surface,
                title = { Text("Delete this scan?") },
                text = {
                    Text(
                        "\"${data.scan.name}\" and all its photos will be permanently removed. " +
                            "This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDelete = false
                        vm.deleteScan(data)
                        onBack()
                    }) { Text("Delete", color = SunnyColors.Danger, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) {
                        Text("Cancel", color = SunnyColors.TextSecondary)
                    }
                },
            )
        }
    }
}

@Composable
private fun ObservationImage(obs: ObservationEntity) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(20.dp))
            .background(SunnyColors.SurfaceMuted),
    ) {
        AsyncImage(
            model = EncryptedImage(obs.imagePath), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HistoryRow(obs: ObservationEntity) {
    SunnyCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(48.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(SunnyColors.SurfaceMuted)) {
                AsyncImage(model = EncryptedImage(obs.imagePath), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(0.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(Format.date(obs.capturedAt), style = MaterialTheme.typography.titleMedium)
                Text(obs.analysis.summary, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary, maxLines = 2)
            }
        }
    }
}

/** Labels whose value differs between two observations (ordinal, plain compare). */
private fun changedFields(prev: Analysis, curr: Analysis): Set<String> =
    Analysis.FIELDS.filterIndexed { i, _ ->
        prev.rows()[i].second.trim() != curr.rows()[i].second.trim()
    }.toSet()
