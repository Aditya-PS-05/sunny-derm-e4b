package com.sunny.skin.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.report.ReportGenerator
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SavedScreen(
    vm: SunnyViewModel,
    contentPadding: PaddingValues,
    onScanClick: (String) -> Unit,
    onGenerateReport: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenReport: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scans by vm.scans.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<BodyRegion?>(null) }
    val filtered = remember(scans, filter) {
        if (filter == null) scans else scans.filter { it.scan.bodyPart.region == filter }
    }

    // ---- Multi-select state ----
    var selecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }

    val selectedScans = scans.filter { it.scan.id in selectedIds }

    fun exitSelection() { selecting = false; selectedIds.clear() }
    fun toggle(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selecting = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header — normal vs selection mode.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Surface(
                        Modifier.size(40.dp).clip(CircleShape).clickable { exitSelection() },
                        shape = CircleShape, color = SunnyColors.Surface, shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Cancel", tint = SunnyColors.TextPrimary,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    val allSelected = filtered.isNotEmpty() && filtered.all { it.scan.id in selectedIds }
                    Text(if (allSelected) "Clear all" else "Select all",
                        style = MaterialTheme.typography.bodyLarge, color = SunnyColors.Orange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            // Scope both actions to the current filter so "Clear all"
                            // can't silently drop selections made under other filters.
                            if (allSelected) filtered.forEach { selectedIds.remove(it.scan.id) }
                            else filtered.forEach { if (it.scan.id !in selectedIds) selectedIds.add(it.scan.id) }
                            if (selectedIds.isEmpty()) selecting = false
                        }.padding(horizontal = 8.dp, vertical = 4.dp))
                } else {
                    Text("Saved Scans", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onGenerateReport),
                        shape = CircleShape, color = SunnyColors.Surface, shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Description, "Generate report",
                                tint = SunnyColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // Filter chips
            Text("Filter:", style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SunnyChip("All", filter == null, { filter = null })
                BodyRegion.entries.forEach { r ->
                    SunnyChip(r.label, filter == r, { filter = r })
                }
            }
            Spacer(Modifier.height(16.dp))

            if (filtered.isEmpty()) {
                EmptyScans(Modifier.weight(1f).fillMaxWidth())
            } else {
                if (!selecting) {
                    Text("Tip: long-press a scan to select several.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = 120.dp + contentPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.scan.id }) { scan ->
                        ScanRow(
                            scan = scan,
                            selecting = selecting,
                            selected = scan.scan.id in selectedIds,
                            onOpen = { onScanClick(scan.scan.id) },
                            onLongPress = { selecting = true; if (scan.scan.id !in selectedIds) selectedIds.add(scan.scan.id) },
                            onToggle = { toggle(scan.scan.id) },
                        )
                    }
                }
            }
        }

        // Floating action bar in selection mode.
        if (selecting && selectedIds.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp + contentPadding.calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionPill(
                    modifier = Modifier.weight(1f),
                    icon = { if (generating) CircularProgressIndicator(Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White,
                            modifier = Modifier.size(18.dp)) },
                    label = "Report (${selectedIds.size})",
                    container = SunnyColors.Orange, content = Color.White,
                    onClick = {
                        if (!generating) {
                            generating = true
                            val toReport = selectedScans
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ReportGenerator(context).generate(toReport, System.currentTimeMillis())
                                }
                                generating = false
                                exitSelection()
                                onOpenReport(file.nameWithoutExtension)
                            }
                        }
                    },
                )
                ActionPill(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = SunnyColors.Danger,
                        modifier = Modifier.size(18.dp)) },
                    label = "Delete (${selectedIds.size})",
                    container = SunnyColors.Surface, content = SunnyColors.Danger,
                    border = true,
                    onClick = { showDeleteConfirm = true },
                )
            }
        }
    }

    // Delete confirmation.
    if (showDeleteConfirm) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SunnyColors.Surface,
            title = { Text(if (n == 1) "Delete this scan?" else "Delete $n scans?") },
            text = {
                Text("The selected ${if (n == 1) "scan" else "scans"} and all their photos will be " +
                    "permanently removed. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    selectedScans.forEach { vm.deleteScan(it) }
                    exitSelection()
                }) { Text("Delete", color = SunnyColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = SunnyColors.TextSecondary)
                }
            },
        )
    }

}

@Composable
private fun ActionPill(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    border: Boolean = false,
) {
    Surface(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
            .then(if (border) Modifier.border(1.dp, SunnyColors.Danger.copy(alpha = 0.4f),
                RoundedCornerShape(26.dp)) else Modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp), color = container, shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.size(8.dp))
            Text(label, color = content, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanRow(
    scan: ScanWithObservations,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
) {
    val latest = scan.latest
    SunnyCard(
        modifier = Modifier.combinedClickable(
            onClick = { if (selecting) onToggle() else onOpen() },
            onLongClick = onLongPress,
        ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                SelectionDot(selected)
                Spacer(Modifier.size(12.dp))
            }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SunnyColors.SurfaceMuted),
            ) {
                if (latest != null) {
                    AsyncImage(
                        model = File(latest.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
            if (!selecting) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = SunnyColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    if (selected) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Orange),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    } else {
        Box(Modifier.size(24.dp).clip(CircleShape)
            .border(1.5.dp, SunnyColors.TextTertiary, CircleShape))
    }
}

@Composable
private fun EmptyScans(modifier: Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No scans yet", style = MaterialTheme.typography.titleMedium,
                color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to add a spot, then re-check it in a\nfew weeks to see any change.",
                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary)
        }
    }
}
