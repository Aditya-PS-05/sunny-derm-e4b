package com.sunny.skin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.Format
import java.io.File

private enum class CompareMode(val label: String) { FADE("Fade"), WIPE("Slide"), SIDE("Side by side") }

/**
 * Compares two dated photos of the same tracked spot. Three modes:
 *  - Fade: crossfade slider (onion-skin) so subtle change "pops".
 *  - Slide: drag a divider to wipe between the older and newer photo.
 *  - Side by side: both photos next to each other.
 * The two photos being compared are pickable from the timeline.
 */
@Composable
fun CompareScreen(vm: SunnyViewModel, scanId: String, onBack: () -> Unit) {
    val scan by vm.scan(scanId).collectAsStateWithLifecycle(initialValue = null)
    val data = scan

    ScreenScaffold(title = "Compare", onBack = onBack) { inner ->
        if (data == null) return@ScreenScaffold
        // Oldest first so "before" → "after" reads left/earlier to right/later.
        val obs = data.timeline.sortedBy { it.capturedAt }
        if (obs.size < 2) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Take at least two photos of this spot to compare them over time.",
                    style = MaterialTheme.typography.bodyLarge, color = SunnyColors.TextSecondary)
            }
            return@ScreenScaffold
        }

        var beforeIdx by remember { mutableIntStateOf(0) }
        var afterIdx by remember { mutableIntStateOf(obs.lastIndex) }
        var mode by remember { mutableStateOf(CompareMode.FADE) }
        var fade by remember { mutableFloatStateOf(0.5f) }
        var wipe by remember { mutableFloatStateOf(0.5f) }

        val before = obs[beforeIdx]
        val after = obs[afterIdx]

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            // Mode segmented control
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(SunnyColors.SurfaceMuted).padding(3.dp),
            ) {
                CompareMode.entries.forEach { m ->
                    val selected = mode == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (selected) SunnyColors.Surface else Color.Transparent)
                            .clickable { mode = m }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(m.label, style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (mode) {
                CompareMode.FADE -> FadeCompare(before, after, fade) { fade = it }
                CompareMode.WIPE -> WipeCompare(before, after, wipe) { wipe = it }
                CompareMode.SIDE -> SideCompare(before, after)
            }

            Spacer(Modifier.height(20.dp))

            // Photo pickers
            PhotoPicker("Before (older)", obs, beforeIdx) { beforeIdx = it }
            Spacer(Modifier.height(12.dp))
            PhotoPicker("After (newer)", obs, afterIdx) { afterIdx = it }

            Spacer(Modifier.height(20.dp))

            // What changed between the two selected photos
            val changed = changedFieldsBetween(before.analysis.toAnalysis(), after.analysis.toAnalysis())
            SunnyCard {
                Column(Modifier.padding(16.dp)) {
                    Text("What changed", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (changed.isEmpty()) {
                        Text("Sunny describes both photos the same way. This is appearance " +
                            "tracking only — not a medical assessment.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    } else {
                        Text("Sunny's description differs on: ${changed.joinToString(", ")}.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Changes here are a prompt to show a clinician — never a verdict.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FadeCompare(before: ObservationEntity, after: ObservationEntity, fade: Float, onFade: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
        .background(SunnyColors.SurfaceMuted)) {
        AsyncImage(File(before.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        AsyncImage(File(after.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alpha = fade)
        DateTag(Format.date(before.capturedAt), Alignment.TopStart, faded = fade > 0.5f)
        DateTag(Format.date(after.capturedAt), Alignment.TopEnd, faded = fade < 0.5f)
    }
    Spacer(Modifier.height(4.dp))
    Slider(value = fade, onValueChange = onFade,
        colors = SliderDefaults.colors(
            thumbColor = SunnyColors.Orange, activeTrackColor = SunnyColors.Orange,
            inactiveTrackColor = SunnyColors.SurfaceMuted))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Before", style = MaterialTheme.typography.labelMedium, color = SunnyColors.TextSecondary)
        Text("After", style = MaterialTheme.typography.labelMedium, color = SunnyColors.TextSecondary)
    }
}

@Composable
private fun WipeCompare(before: ObservationEntity, after: ObservationEntity, wipe: Float, onWipe: (Float) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
        .background(SunnyColors.SurfaceMuted)) {
        val fullW = maxWidth
        val fullWpx = with(density) { fullW.toPx() }
        // After fills the whole area; before is revealed on the left up to the divider.
        AsyncImage(File(after.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.width(fullW * wipe).fillMaxSize().clipToBounds()) {
            AsyncImage(File(before.imagePath), null, Modifier.width(fullW).fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
        DateTag(Format.date(before.capturedAt), Alignment.TopStart, faded = false)
        DateTag(Format.date(after.capturedAt), Alignment.TopEnd, faded = false)
        // Divider + handle, draggable
        Canvas(Modifier.fillMaxSize().pointerInput(fullWpx) {
            detectHorizontalDragGestures { change, _ ->
                onWipe((change.position.x / fullWpx).coerceIn(0f, 1f))
            }
        }) {
            val x = size.width * wipe
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
            drawCircle(Color.White, radius = 26f, center = Offset(x, size.height / 2))
            drawCircle(SunnyColors.Orange, radius = 14f, center = Offset(x, size.height / 2))
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Drag the divider — older photo on the left, newer on the right.",
        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary)
}

@Composable
private fun SideCompare(before: ObservationEntity, after: ObservationEntity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Before" to before, "After" to after).forEach { (label, o) ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .background(SunnyColors.SurfaceMuted)) {
                    AsyncImage(File(o.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(Format.date(o.capturedAt), style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DateTag(
    text: String, align: Alignment, faded: Boolean,
) {
    Box(Modifier.align(align).padding(10.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = if (faded) 0.25f else 0.55f))
                .padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PhotoPicker(
    label: String, obs: List<ObservationEntity>, selected: Int, onSelect: (Int) -> Unit,
) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = SunnyColors.TextTertiary, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        obs.forEachIndexed { i, o ->
            SunnyChip(Format.date(o.capturedAt), selected = i == selected, onClick = { onSelect(i) })
        }
    }
}

private fun changedFieldsBetween(prev: Analysis, curr: Analysis): List<String> =
    Analysis.FIELDS.filterIndexed { i, _ ->
        prev.rows()[i].second.trim() != curr.rows()[i].second.trim()
    }
