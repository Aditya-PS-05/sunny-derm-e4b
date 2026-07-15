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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sunny.skin.ui.components.AbcdeCard
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.ChangeSummaryCard
import com.sunny.skin.ui.components.CircleButton
import com.sunny.skin.ui.components.MetaChip
import com.sunny.skin.ui.components.LiquidGlassDialog
import com.sunny.skin.ui.components.ReCheckReminderDialog
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.findDermatologistNearby
import com.sunny.skin.ui.components.rememberNotificationRequester
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.BitmapLoader
import com.sunny.skin.util.Format
import java.io.File

@Composable
fun ScanDetailScreen(
    vm: SunnyViewModel,
    scanId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCompare: () -> Unit,
    onCaptureFollowUp: () -> Unit,
    onReviewFollowUp: () -> Unit,
) {
    val scan by vm.scan(scanId).collectAsStateWithLifecycle(initialValue = null)
    val modelAvailable by vm.modelAvailable.collectAsStateWithLifecycle()
    val data = scan
    var showDelete by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

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
        fun prepareFollowUp() {
            vm.beginRecheckCapture(
                scanId = scanId,
                bodyPart = data.scan.bodyPart,
                referenceImagePath = latest.imagePath,
            )
        }

        fun openFollowUpCamera() {
            if (!modelAvailable) {
                android.widget.Toast.makeText(
                    context,
                    com.sunny.skin.AppMode.unavailableMessage(com.sunny.skin.AppMode.serverActive(context)),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                prepareFollowUp()
                onCaptureFollowUp()
            }
        }

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                prepareFollowUp()
                val bmp = BitmapLoader.fromUri(context, uri)
                vm.startCapture(bmp)
                onReviewFollowUp()
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            ObservationImage(latest, data.scan.bodyPart.label)
            Spacer(Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip(data.scan.bodyPart.locationLine)
                MetaChip(Format.date(latest.capturedAt))
                MetaChip(Format.time(latest.capturedAt))
                latest.approximateSizeMm?.let { MetaChip("Approx. ${formatApproximateMm(it)}") }
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
            SunnyCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AddAPhoto, null, tint = SunnyColors.Orange,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = ::openFollowUpCamera)
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            when {
                                !modelAvailable -> "AI model required"
                                else -> "Take a follow-up photo"
                            },
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (modelAvailable) "Use the previous photo as an alignment guide"
                            else "Install the real model from Settings to enable analysis",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(SunnyColors.SurfaceMuted)
                            .clickable(enabled = modelAvailable) {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = "Choose follow-up from library",
                            tint = if (modelAvailable) SunnyColors.TextPrimary else SunnyColors.TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
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
                            Text("Align and inspect any two of ${timeline.size} photos",
                                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = SunnyColors.TextTertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SunnyCard(onClick = { showNotes = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, tint = SunnyColors.Orange,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Private note", style = MaterialTheme.typography.titleMedium)
                        Text(
                            data.scan.notes.ifBlank { "Add context you want to remember" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                            maxLines = 2,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = SunnyColors.TextTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))

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
            Spacer(Modifier.height(12.dp))

            // Bridge to care: Sunny doesn't diagnose, so it helps you reach someone
            // who can. Opens Maps — only a location leaves the device, never photos.
            SunnyCard(onClick = { findDermatologistNearby(context) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Place, null, tint = SunnyColors.Orange,
                            modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Find a dermatologist near you",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Opens Maps — your photos stay on this phone",
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


        if (showNotes) {
            var draft by remember(data.scan.notes) { mutableStateOf(data.scan.notes) }
            LiquidGlassDialog(onDismiss = { showNotes = false }) {
                Text(
                    "Private note",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                )
                Text(
                    "Stored only with this encrypted scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2_000) },
                    label = { Text("Note") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showNotes = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        vm.setScanNotes(scanId, draft)
                        showNotes = false
                    }) { Text("Save", color = SunnyColors.OrangeText, fontWeight = FontWeight.SemiBold) }
                }
            }
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
private fun ObservationImage(obs: ObservationEntity, bodyLabel: String) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(20.dp))
            .background(SunnyColors.SurfaceMuted),
    ) {
        AsyncImage(
            model = EncryptedImage(obs.imagePath),
            contentDescription = "Latest $bodyLabel photo from ${Format.date(obs.capturedAt)}",
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
                AsyncImage(model = EncryptedImage(obs.imagePath),
                    contentDescription = "Skin photo from ${Format.date(obs.capturedAt)}",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(0.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(Format.date(obs.capturedAt), style = MaterialTheme.typography.titleMedium)
                Text(obs.analysis.summary, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary, maxLines = 2)
                obs.approximateSizeMm?.let {
                    Text("Approx. ${formatApproximateMm(it)} · reference-based",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextTertiary)
                }
            }
        }
    }
}

/** Labels whose value differs between two observations (ordinal, plain compare). */
private fun changedFields(prev: Analysis, curr: Analysis): Set<String> =
    Analysis.FIELDS.filterIndexed { i, _ ->
        prev.rows()[i].second.trim() != curr.rows()[i].second.trim()
    }.toSet()

private fun formatApproximateMm(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()} mm" else "$rounded mm"
}
