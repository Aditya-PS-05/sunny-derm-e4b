package com.sunny.skin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.data.db.ScanWithObservations
import com.sunny.skin.data.model.BodyRegion
import com.sunny.skin.report.ReportGenerator
import com.sunny.skin.reminder.Reminder
import com.sunny.skin.reminder.reminderIntervalLabel
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.LiquidGlassDialog
import com.sunny.skin.ui.components.RecurringIntervalDialog
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.components.SunnyToggle
import com.sunny.skin.ui.components.rememberNotificationRequester
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ScanSort(val label: String) {
    RECENT("Recently updated"),
    OLDEST("Oldest updated"),
    NAME("Name"),
}

@Composable
fun SavedScreen(
    vm: SunnyViewModel,
    contentPadding: PaddingValues,
    onScanClick: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onGenerateReport: () -> Unit,
    proReportsEnabled: Boolean,
    onOpenReports: () -> Unit,
    onOpenReport: (String) -> Unit,
    openReminderCenter: Boolean = false,
    onReminderCenterOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scans by vm.scans.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val recurring = reminders.firstOrNull { it.id == Reminder.RECURRING_ID }
    var intervalHours by remember { mutableIntStateOf(recurring?.intervalHours ?: 24 * 30) }
    LaunchedEffect(recurring?.intervalHours) {
        recurring?.intervalHours?.let { intervalHours = it }
    }
    val requestNotifications = rememberNotificationRequester()
    var filter by remember { mutableStateOf<BodyRegion?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ScanSort.RECENT) }
    var animateItemPlacement by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showReminderCenter by remember { mutableStateOf(false) }
    var showIntervalEditor by remember { mutableStateOf(false) }
    LaunchedEffect(openReminderCenter) {
        if (openReminderCenter) {
            showReminderCenter = true
            onReminderCenterOpened()
        }
    }
    val filtered = remember(scans, filter, query, sort) {
        val needle = query.trim()
        scans.asSequence()
            .filter { filter == null || it.scan.bodyPart.region == filter }
            .filter {
                needle.isEmpty() ||
                    it.scan.name.contains(needle, ignoreCase = true) ||
                    it.scan.bodyPart.locationLine.contains(needle, ignoreCase = true)
            }
            .let { matches ->
                when (sort) {
                    ScanSort.RECENT -> matches.sortedByDescending { it.scan.updatedAt }
                    ScanSort.OLDEST -> matches.sortedBy { it.scan.updatedAt }
                    ScanSort.NAME -> matches.sortedBy { it.scan.name.lowercase(Locale.ROOT) }
                }
            }
            .toList()
    }

    // ---- Multi-select state ----
    var selecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    val motionEnabled = rememberSunnyMotionEnabled()
    val headerOffsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    LaunchedEffect(animateItemPlacement) {
        if (animateItemPlacement) {
            delay(SunnyMotion.StateMillis.toLong() + 16L)
            animateItemPlacement = false
        }
    }

    val selectedScans = scans.filter { it.scan.id in selectedIds }

    fun exitSelection() { selecting = false; selectedIds.clear() }
    fun toggle(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selecting = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header — normal vs selection mode.
            AnimatedContent(
                targetState = selecting,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val enterFade = fadeIn(
                        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
                    )
                    val exitFade = fadeOut(
                        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
                    )
                    if (motionEnabled) {
                        (enterFade + slideInHorizontally(
                            animationSpec = tween(
                                SunnyMotion.StateMillis,
                                easing = SunnyMotion.EaseOut,
                            ),
                            initialOffsetX = { if (targetState) headerOffsetPx else -headerOffsetPx },
                        )) togetherWith (exitFade + slideOutHorizontally(
                            animationSpec = tween(
                                SunnyMotion.StateMillis,
                                easing = SunnyMotion.EaseOut,
                            ),
                            targetOffsetX = { if (targetState) -headerOffsetPx else headerOffsetPx },
                        ))
                    } else {
                        enterFade togetherWith exitFade
                    }
                },
                label = "Saved selection header",
            ) {
                selectionMode ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                    Surface(
                        Modifier.size(48.dp).clip(CircleShape).clickable { exitSelection() },
                        shape = CircleShape, color = SunnyColors.Surface, shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Cancel", tint = SunnyColors.TextPrimary,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    val selectionLabel = if (selectedIds.size == 1) {
                        "1 area selected"
                    } else {
                        "${selectedIds.size} areas selected"
                    }
                    Text("${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).semantics {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = selectionLabel
                        })
                    val allSelected = filtered.isNotEmpty() && filtered.all { it.scan.id in selectedIds }
                    TextButton(
                        onClick = {
                            // Scope both actions to the current filter so "Clear all"
                            // can't silently drop selections made under other filters.
                            if (allSelected) filtered.forEach { selectedIds.remove(it.scan.id) }
                            else filtered.forEach { if (it.scan.id !in selectedIds) selectedIds.add(it.scan.id) }
                            if (selectedIds.isEmpty()) selecting = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (allSelected) "Clear all" else "Select all",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SunnyColors.OrangeText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    } else {
                    Text("Tracked Areas", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    HeaderIconButton(
                        icon = Icons.Filled.NotificationsActive,
                        description = if (reminders.isEmpty()) "Reminders" else
                            "Reminders, ${reminders.size} scheduled",
                        active = reminders.isNotEmpty(),
                        onClick = { showReminderCenter = true },
                    )
                    Spacer(Modifier.size(6.dp))
                    HeaderReportsButton(
                        onClick = onOpenReports,
                    )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        animateItemPlacement = false
                        query = it
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search tracked areas") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                animateItemPlacement = false
                                query = ""
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(8.dp),
                )
                Surface(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { sortExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = SunnyColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort: ${sort.label}")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Filter chips
            Text("Filter:", style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SunnyChip("All", filter == null, {
                    animateItemPlacement = true
                    filter = null
                }) }
                items(BodyRegion.entries) { region ->
                    SunnyChip(region.label, filter == region, {
                        animateItemPlacement = true
                        filter = region
                    })
                }
            }
            Spacer(Modifier.height(16.dp))

            if (filtered.isEmpty()) {
                EmptyScans(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    noMatches = scans.isNotEmpty(),
                    onAction = if (scans.isNotEmpty()) {
                        {
                            animateItemPlacement = true
                            query = ""
                            filter = null
                        }
                    } else {
                        onAddPhoto
                    },
                )
            } else {
                if (!selecting) {
                    Text("Tip: long-press an area to select several.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = 24.dp + contentPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.scan.id }) { scan ->
                        val placementModifier = if (animateItemPlacement && motionEnabled) {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = tween(
                                    SunnyMotion.StateMillis,
                                    easing = SunnyMotion.EaseInOut,
                                ),
                                fadeOutSpec = null,
                            )
                        } else {
                            Modifier
                        }
                        ScanRow(
                            modifier = placementModifier,
                            scan = scan,
                            selecting = selecting,
                            selected = scan.scan.id in selectedIds,
                            onOpen = { onScanClick(scan.scan.id) },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selecting = true
                                if (scan.scan.id !in selectedIds) selectedIds.add(scan.scan.id)
                            },
                            onToggle = { toggle(scan.scan.id) },
                        )
                    }
                }
            }
        }

        // Floating action bar in selection mode.
        AnimatedVisibility(
            visible = selecting && selectedIds.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (motionEnabled) {
                fadeIn(tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut)) +
                    slideInVertically(
                        animationSpec = tween(
                            SunnyMotion.ModalEnterMillis,
                            easing = SunnyMotion.DrawerEase,
                        ),
                        initialOffsetY = { it / 5 },
                    )
            } else {
                fadeIn(tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut))
            },
            exit = if (motionEnabled) {
                fadeOut(tween(SunnyMotion.ModalExitMillis, easing = SunnyMotion.EaseOut)) +
                    slideOutVertically(
                        animationSpec = tween(
                            SunnyMotion.ModalExitMillis,
                            easing = SunnyMotion.DrawerEase,
                        ),
                        targetOffsetY = { it / 5 },
                    )
            } else {
                fadeOut(tween(SunnyMotion.ModalExitMillis, easing = SunnyMotion.EaseOut))
            },
        ) {
            Row(
                Modifier.fillMaxWidth()
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
                    container = SunnyColors.Action, content = Color.White,
                    onClick = {
                        if (!proReportsEnabled) {
                            exitSelection()
                            onGenerateReport()
                        } else if (!generating) {
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
            title = { Text(if (n == 1) "Delete this tracked area?" else "Delete $n tracked areas?") },
            text = {
                Text("The selected ${if (n == 1) "area" else "areas"} and all their photos will be " +
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

    if (sortExpanded) {
        SortPickerDialog(
            selected = sort,
            onSelect = {
                animateItemPlacement = true
                sort = it
                sortExpanded = false
            },
            onDismiss = { sortExpanded = false },
        )
    }

    if (showReminderCenter) {
        ReminderCenterDialog(
            recurring = recurring,
            spotReminders = reminders.filterNot { it.id == Reminder.RECURRING_ID },
            intervalHours = intervalHours,
            onRecurringChange = { enabled ->
                if (enabled) {
                    requestNotifications { vm.setRecurringReminder(true, intervalHours) }
                } else {
                    vm.setRecurringReminder(false, 0)
                }
            },
            onEditInterval = {
                showReminderCenter = false
                showIntervalEditor = true
            },
            onRemove = vm::cancelReminder,
            onDismiss = { showReminderCenter = false },
        )
    }

    if (showIntervalEditor) {
        RecurringIntervalDialog(
            initialHours = intervalHours,
            onPick = { hours ->
                intervalHours = hours
                showIntervalEditor = false
                showReminderCenter = true
                requestNotifications { vm.setRecurringReminder(true, hours) }
            },
            onDismiss = {
                showIntervalEditor = false
                showReminderCenter = true
            },
        )
    }

}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Surface(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) SunnyColors.OrangeSoft else SunnyColors.Surface,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) SunnyColors.Orange else SunnyColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HeaderReportsButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = SunnyColors.Surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = SunnyColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text("Reports", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReminderCenterDialog(
    recurring: Reminder?,
    spotReminders: List<Reminder>,
    intervalHours: Int,
    onRecurringChange: (Boolean) -> Unit,
    onEditInterval: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LiquidGlassDialog(
        onDismiss = {
            val action = pendingAction
            pendingAction = null
            if (action != null) action() else onDismiss()
        },
    ) { requestDismiss ->
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(SunnyColors.OrangeSoft.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = SunnyColors.Orange,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Reminder Center",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
            )
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Regular skin check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        recurring?.let { reminderIntervalLabel(it.intervalHours) } ?: "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary,
                    )
                }
                SunnyToggle(
                    checked = recurring != null,
                    onCheckedChange = onRecurringChange,
                    accessibilityLabel = "Regular skin check reminders",
                )
            }

            if (recurring != null) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.45f))
                Row(
                    Modifier.fillMaxWidth().height(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            pendingAction = onEditInterval
                            requestDismiss()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = SunnyColors.Orange,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Interval", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    Text(reminderIntervalLabel(intervalHours),
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = SunnyColors.TextTertiary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Upcoming spot reminders",
                style = MaterialTheme.typography.labelLarge,
                color = SunnyColors.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (spotReminders.isEmpty()) {
                Text(
                    "No spot reminders scheduled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            } else {
                spotReminders.forEachIndexed { index, reminder ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Format.date(reminder.triggerAt)} at ${Format.time(reminder.triggerAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SunnyColors.TextSecondary,
                            )
                        }
                        IconButton(onClick = { onRemove(reminder.id) }) {
                            Icon(Icons.Filled.Close, "Remove reminder", tint = SunnyColors.TextTertiary)
                        }
                    }
                    if (index < spotReminders.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }

            TextButton(
                onClick = requestDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Done", color = SunnyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun SortPickerDialog(
    selected: ScanSort,
    onSelect: (ScanSort) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingSelection by remember { mutableStateOf<ScanSort?>(null) }
    LiquidGlassDialog(
        onDismiss = {
            val selection = pendingSelection
            pendingSelection = null
            if (selection != null) onSelect(selection) else onDismiss()
        },
    ) { requestDismiss ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(SunnyColors.OrangeSoft.copy(alpha = 0.9f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = SunnyColors.Orange,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Sort saved scans",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(14.dp))
            ScanSort.entries.forEachIndexed { index, option ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selected == option,
                            role = Role.RadioButton,
                            onClick = {
                                pendingSelection = option
                                requestDismiss()
                            },
                        )
                        .background(
                            if (selected == option) SunnyColors.OrangeSoft.copy(alpha = 0.55f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected == option) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected == option) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Action),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                if (index < ScanSort.entries.lastIndex) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.45f))
                }
            }
            TextButton(
                onClick = requestDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Cancel", color = SunnyColors.TextSecondary)
            }
        }
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
    modifier: Modifier = Modifier,
    scan: ScanWithObservations,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
) {
    val latest = scan.latest
    SunnyCard(
        modifier = modifier.semantics {
            if (selecting) {
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
        }.combinedClickable(
            onClick = { if (selecting) onToggle() else onOpen() },
            onLongClick = onLongPress,
        ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = selecting,
                enter = fadeIn(tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)) +
                    expandHorizontally(
                        animationSpec = tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut),
                        expandFrom = Alignment.Start,
                    ),
                exit = fadeOut(tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)) +
                    shrinkHorizontally(
                        animationSpec = tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut),
                        shrinkTowards = Alignment.Start,
                    ),
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 56.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SelectionDot(selected)
                }
            }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SunnyColors.SurfaceMuted),
            ) {
                if (latest != null) {
                    AsyncImage(
                        model = EncryptedImage(latest.imagePath),
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
                    val photoLabel = if (scan.observations.size == 1) "1 photo" else
                        "${scan.observations.size} photos"
                    Text("$photoLabel | ${Format.date(it.capturedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
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
    val motionEnabled = rememberSunnyMotionEnabled()
    val fill by animateColorAsState(
        targetValue = if (selected) SunnyColors.Action else Color.Transparent,
        animationSpec = tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut),
        label = "Selection fill",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut),
        label = "Selection check opacity",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Selection check scale",
    )
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(fill)
            .border(
                width = 1.5.dp,
                color = if (selected) SunnyColors.Action else SunnyColors.TextTertiary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp).graphicsLayer {
                alpha = checkAlpha
                scaleX = checkScale
                scaleY = checkScale
            },
        )
    }
}

@Composable
private fun EmptyScans(
    modifier: Modifier,
    noMatches: Boolean,
    onAction: () -> Unit,
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyScansIllustration(noMatches = noMatches)
            Spacer(Modifier.height(20.dp))
            Text(if (noMatches) "No matching areas" else "No tracked areas yet",
                style = MaterialTheme.typography.titleMedium,
                color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(if (noMatches) "Try another search or body-area filter."
                else "Add a photo, then re-check the same area later to build a useful timeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextTertiary,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(if (noMatches) "Clear search and filters" else "Add first photo")
            }
        }
    }
}

@Composable
private fun EmptyScansIllustration(noMatches: Boolean) {
    val lineColor = SunnyColors.TextTertiary.copy(alpha = 0.62f)
    val softColor = SunnyColors.SurfaceMuted.copy(alpha = 0.82f)
    val accentSoft = SunnyColors.OrangeSoft.copy(alpha = 0.9f)
    val accent = SunnyColors.Orange.copy(alpha = 0.88f)
    Canvas(Modifier.size(112.dp)) {
        val stroke = size.minDimension * 0.035f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = softColor,
            radius = size.minDimension * 0.45f,
            center = center,
        )

        if (noMatches) {
            val lensCenter = Offset(size.width * 0.45f, size.height * 0.43f)
            val lensRadius = size.minDimension * 0.19f
            drawCircle(
                color = lineColor,
                radius = lensRadius,
                center = lensCenter,
                style = Stroke(width = stroke),
            )
            drawLine(
                color = lineColor,
                start = Offset(
                    lensCenter.x + lensRadius * 0.72f,
                    lensCenter.y + lensRadius * 0.72f,
                ),
                end = Offset(size.width * 0.72f, size.height * 0.72f),
                strokeWidth = stroke * 1.25f,
            )
            drawCircle(
                color = accentSoft,
                radius = size.minDimension * 0.055f,
                center = lensCenter,
            )
        } else {
            val figureX = size.width * 0.43f
            drawCircle(
                color = lineColor,
                radius = size.minDimension * 0.07f,
                center = Offset(figureX, size.height * 0.27f),
            )
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(size.width * 0.35f, size.height * 0.37f),
                size = Size(size.width * 0.16f, size.height * 0.27f),
                cornerRadius = CornerRadius(stroke * 1.4f),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = lineColor,
                start = Offset(figureX, size.height * 0.64f),
                end = Offset(size.width * 0.34f, size.height * 0.80f),
                strokeWidth = stroke,
            )
            drawLine(
                color = lineColor,
                start = Offset(figureX, size.height * 0.64f),
                end = Offset(size.width * 0.52f, size.height * 0.80f),
                strokeWidth = stroke,
            )
            drawLine(
                color = lineColor,
                start = Offset(size.width * 0.35f, size.height * 0.43f),
                end = Offset(size.width * 0.25f, size.height * 0.59f),
                strokeWidth = stroke,
            )
            drawLine(
                color = lineColor,
                start = Offset(size.width * 0.51f, size.height * 0.43f),
                end = Offset(size.width * 0.61f, size.height * 0.57f),
                strokeWidth = stroke,
            )

            val plusCenter = Offset(size.width * 0.69f, size.height * 0.35f)
            drawCircle(
                color = accentSoft,
                radius = size.minDimension * 0.14f,
                center = plusCenter,
            )
            drawLine(
                color = accent,
                start = Offset(plusCenter.x - size.width * 0.055f, plusCenter.y),
                end = Offset(plusCenter.x + size.width * 0.055f, plusCenter.y),
                strokeWidth = stroke,
            )
            drawLine(
                color = accent,
                start = Offset(plusCenter.x, plusCenter.y - size.height * 0.055f),
                end = Offset(plusCenter.x, plusCenter.y + size.height * 0.055f),
                strokeWidth = stroke,
            )
        }
    }
}
