package com.sunny.skin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.R
import com.sunny.skin.data.model.BodySide
import com.sunny.skin.data.model.BodyZone
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.BodyTemplate
import com.sunny.skin.ui.components.InlineDisclaimer
import com.sunny.skin.ui.components.SunnyGreeting
import com.sunny.skin.ui.components.StreakCard
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlinx.coroutines.delay

@Composable
fun OverviewScreen(
    vm: SunnyViewModel,
    onScanClick: (String) -> Unit,
    onCheckSession: () -> Unit,
    onAddPhoto: () -> Unit,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val scans by vm.scans.collectAsStateWithLifecycle()
    val habit by vm.habit.collectAsStateWithLifecycle()
    val checkSession by vm.checkSession.collectAsStateWithLifecycle()
    var side by remember { mutableStateOf(BodySide.FRONT) }
    var highlightedNewZone by remember { mutableStateOf<BodyZone?>(null) }
    var consumedNewScanId by rememberSaveable { mutableStateOf<String?>(null) }
    val motionEnabled = rememberSunnyMotionEnabled()
    val animatedCoverage by animateFloatAsState(
        targetValue = stats.coverage,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Overview coverage",
    )

    LaunchedEffect(scans) {
        val newest = scans.maxByOrNull { it.scan.createdAt }
        val zone = newest?.scan?.bodyPart?.zone
        val isFresh = newest != null &&
            System.currentTimeMillis() - newest.scan.createdAt in 0L..30_000L
        val introducesZone = zone != null && scans.count { it.scan.bodyPart.zone == zone } == 1
        if (newest != null && isFresh && introducesZone && newest.scan.id != consumedNewScanId) {
            consumedNewScanId = newest.scan.id
            highlightedNewZone = zone
            delay(SunnyMotion.ModalEnterMillis.toLong() + 80L)
            highlightedNewZone = null
        }
    }
    // A compact, one-time welcome glow for a brand-new user landing on Overview.
    var showGreeting by remember { mutableStateOf(!vm.settings.seenGreeting) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 120.dp),
    ) {
        // Header: mascot + title + tagline
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (showGreeting) {
                    LaunchedEffect(Unit) { vm.settings.seenGreeting = true }
                    SunnyGreeting(
                        modifier = Modifier.fillMaxSize(),
                        onFinished = { showGreeting = false },
                    )
                } else {
                    Image(
                        painterResource(R.drawable.sunny_mascot),
                        contentDescription = "Sunny mascot",
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Sunny", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Track visible skin changes over time", style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (scans.isEmpty()) {
            SunnyCard(onClick = onAddPhoto) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = SunnyColors.OrangeText,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            "Add your first photo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Create a baseline for one area you want to track",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Front / Back segmented toggle (reference: grey track, white selected pill)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(SunnyColors.SurfaceMuted)
                .selectableGroup()
                .padding(3.dp),
        ) {
            BodySide.entries.forEach { s ->
                val selected = side == s
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) SunnyColors.Surface else Color.Transparent)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { side = s },
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        BodyTemplate(
            side = side,
            highlighted = stats.scannedZones,
            newlyHighlighted = highlightedNewZone,
        )
        Spacer(Modifier.height(12.dp))

        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), R.drawable.stat_photos, stats.photos, "Photos")
            StatCard(Modifier.weight(1f), R.drawable.stat_scanned, stats.scanned, "Scanned")
            StatCard(Modifier.weight(1f), R.drawable.stat_updates, stats.updates, "Updates")
        }
        Spacer(Modifier.height(16.dp))

        // Weekly check-in streak + activity (retention)
        StreakCard(habit)
        Spacer(Modifier.height(16.dp))

        SunnyCard(onClick = onCheckSession) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(SunnyColors.OrangeSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Checklist, null, tint = SunnyColors.Orange,
                        modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (checkSession == null) "Start a photo check" else "Continue photo check",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        checkSession?.let { "${it.resolvedCount} of ${it.items.size} areas reviewed" }
                            ?: if (stats.scanned == 0) "Create a checklist after saving an area"
                            else "Review ${stats.scanned} saved area${if (stats.scanned == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Body coverage
        SunnyCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Body Coverage", style = MaterialTheme.typography.titleMedium)
                    Text("${(animatedCoverage * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium, color = SunnyColors.OrangeText)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedCoverage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = SunnyColors.OrangeLight,
                    trackColor = SunnyColors.SurfaceMuted,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        InlineDisclaimer(
            "Sunny is for tracking purposes only and does not provide medical advice " +
                "or diagnosis. Consult a healthcare professional for medical concerns.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    }
}

@Composable
private fun StatCard(modifier: Modifier, iconRes: Int, value: Int, label: String) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val displayedValue by animateIntAsState(
        targetValue = value,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "$label count",
    )
    SunnyCard(modifier = modifier) {
        Column(
            Modifier
                .padding(vertical = 10.dp)
                .clearAndSetSemantics { contentDescription = "$value $label" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text("$displayedValue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
        }
    }
}
