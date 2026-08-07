package com.sunny.skin.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.data.db.ObservationEntity
import com.sunny.skin.data.model.AnalysisComparison
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.ScreenLoadingState
import com.sunny.skin.ui.components.ChangeSummaryCard
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.util.AlignTransform
import com.sunny.skin.util.AlignmentResult
import com.sunny.skin.util.FramingQuality
import com.sunny.skin.util.Format
import com.sunny.skin.util.ImageAlignment
import kotlinx.coroutines.delay

private enum class CompareMode(val label: String) {
    FADE("Fade"), WIPE("Wipe"), BLINK("Blink"), SIDE("Side")
}

/** Applies a resolution-independent [AlignTransform] about the image centre. */
private fun Modifier.applyAlign(t: AlignTransform): Modifier = graphicsLayer {
    translationX = t.tx * size.width
    translationY = t.ty * size.height
    scaleX = t.scale
    scaleY = t.scale
    rotationZ = t.rotationDeg
    transformOrigin = TransformOrigin(0.5f, 0.5f)
}

/**
 * Compares two dated photos of the same tracked spot. The newer photo is
 * auto-aligned onto the older one (translation + scale + rotation) so the same
 * spot overlaps — the change then "pops" instead of being lost in reframing.
 * Three modes: Fade (onion-skin), Slide (wipe divider), Side by side
 * (size-normalised). Alignment can be toggled off and nudged by hand.
 */
@Composable
fun CompareScreen(vm: SunnyViewModel, scanId: String, onBack: () -> Unit) {
    val scan by vm.scan(scanId).collectAsStateWithLifecycle(initialValue = null)
    val data = scan

    ScreenScaffold(title = "Compare", onBack = onBack) { inner ->
        if (data == null) {
            ScreenLoadingState("Loading comparison…", Modifier.padding(inner))
            return@ScreenScaffold
        }
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
        var blinkAfter by remember { mutableStateOf(false) }
        val motionEnabled = rememberSunnyMotionEnabled()
        var blinkPlaying by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }

        // Auto-alignment state for the selected pair.
        var alignOn by remember { mutableStateOf(true) }
        var aligning by remember { mutableStateOf(false) }
        var alignment by remember { mutableStateOf(AlignmentResult()) }
        var alignmentPair by remember { mutableStateOf<Pair<String, String>?>(null) }
        var nudge by remember { mutableStateOf(Offset.Zero) } // manual fine-tune (normalised)

        // Clamp against the current list so a reactive shrink can't crash and the
        // pickers can't invert older/newer.
        val bIdx = beforeIdx.coerceIn(0, obs.lastIndex)
        val aIdx = afterIdx.coerceIn(0, obs.lastIndex)
        val before = obs[bIdx]
        val after = obs[aIdx]

        // Recompute registration whenever the compared pair changes.
        val alignCtx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(before.imagePath, after.imagePath) {
            nudge = Offset.Zero
            aligning = true
            alignmentPair = null
            val pair = before.imagePath to after.imagePath
            val result = ImageAlignment.computeResult(alignCtx, pair.first, pair.second)
            alignment = result
            alignmentPair = pair
            aligning = false
        }

        LaunchedEffect(mode, blinkPlaying, before.imagePath, after.imagePath) {
            if (mode != CompareMode.BLINK || !blinkPlaying) return@LaunchedEffect
            while (true) {
                delay(720)
                blinkAfter = !blinkAfter
            }
        }

        // Only the computed registration settles. Manual nudge remains a direct,
        // unanimated offset so the photo never lags behind the user's finger.
        val pairMatches = alignmentPair == (before.imagePath to after.imagePath)
        val computedTransform = if (alignOn && pairMatches && alignment.isUsable) {
            alignment.transform
        } else {
            AlignTransform.Identity
        }
        val alignmentSpec = if (motionEnabled && pairMatches && !aligning) {
            tween<Float>(durationMillis = 220, easing = SunnyMotion.EaseInOut)
        } else {
            snap()
        }
        val alignedTx by animateFloatAsState(computedTransform.tx, alignmentSpec, label = "Alignment x")
        val alignedTy by animateFloatAsState(computedTransform.ty, alignmentSpec, label = "Alignment y")
        val alignedScale by animateFloatAsState(computedTransform.scale, alignmentSpec, label = "Alignment scale")
        val alignedRotation by animateFloatAsState(
            computedTransform.rotationDeg,
            alignmentSpec,
            label = "Alignment rotation",
        )
        val eff = AlignTransform(
            tx = alignedTx + nudge.x,
            ty = alignedTy + nudge.y,
            scale = alignedScale,
            rotationDeg = alignedRotation,
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            CompareModeSelector(
                selected = mode,
                motionEnabled = motionEnabled,
                onSelect = { mode = it },
            )
            Spacer(Modifier.height(12.dp))

            // Auto-align control row
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunnyChip(
                    if (alignOn) "Auto-aligned" else "Align: off",
                    selected = alignOn,
                    onClick = { alignOn = !alignOn },
                )
                Spacer(Modifier.width(10.dp))
                when {
                    aligning -> {
                        CircularProgressIndicator(Modifier.size(16.dp),
                            color = SunnyColors.Orange, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Aligning…", style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary)
                    }
                    alignOn && alignment.isUsable -> {
                        Icon(Icons.Filled.CenterFocusStrong, null, tint = SunnyColors.Orange,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (alignment.quality) {
                                FramingQuality.HIGH -> "High framing match"
                                FramingQuality.MODERATE -> "Moderate framing match"
                                FramingQuality.LOW -> "Low framing match"
                            } + if (nudge != Offset.Zero) " · adjusted" else "",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                        if (nudge != Offset.Zero) {
                            IconButton(onClick = { nudge = Offset.Zero }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Refresh, "Reset manual alignment",
                                    tint = SunnyColors.TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    alignOn -> Text("Photos could not be aligned confidently",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    else -> Text("Showing raw photos", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(150, easing = SunnyMotion.EaseOut)),
                        initialContentExit = fadeOut(tween(150, easing = SunnyMotion.EaseOut)),
                        sizeTransform = null,
                    )
                },
                label = "Comparison mode",
            ) { activeMode ->
                when (activeMode) {
                    CompareMode.FADE -> FadeCompare(before, after, eff, alignOn, fade,
                        onFade = { fade = it }, onNudge = { nudge += it })
                    CompareMode.WIPE -> WipeCompare(before, after, eff, wipe) { wipe = it }
                    CompareMode.BLINK -> BlinkCompare(
                        before = before,
                        after = after,
                        transform = eff,
                        showAfter = blinkAfter,
                        playing = blinkPlaying,
                        onTogglePlaying = { blinkPlaying = !blinkPlaying },
                        onToggleFrame = { blinkAfter = !blinkAfter },
                    )
                    CompareMode.SIDE -> SideCompare(before, after, eff)
                }
            }

            Spacer(Modifier.height(12.dp))
            ComparisonDateRail(
                before = Format.date(before.capturedAt),
                after = Format.date(after.capturedAt),
            )

            if (alignOn && mode == CompareMode.FADE) {
                Spacer(Modifier.height(6.dp))
                Text("Drag the top photo to fine-tune the match.",
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary)
            }

            Spacer(Modifier.height(20.dp))

            // Photo pickers
            PhotoPicker("Before (older)", obs, 0 until obs.lastIndex, beforeIdx) {
                beforeIdx = it
                if (afterIdx <= it) afterIdx = (it + 1).coerceAtMost(obs.lastIndex)
            }
            Spacer(Modifier.height(12.dp))
            PhotoPicker("After (newer)", obs, 1..obs.lastIndex, afterIdx) {
                afterIdx = it
                if (beforeIdx >= it) beforeIdx = (it - 1).coerceAtLeast(0)
            }

            Spacer(Modifier.height(20.dp))

            // What changed between the two selected photos
            val descriptionChanges = AnalysisComparison.changes(
                before.analysis.toAnalysis(),
                after.analysis.toAnalysis(),
            )
            ChangeSummaryCard(
                sinceDate = Format.date(before.capturedAt),
                changes = descriptionChanges,
            )

            val beforeSize = before.approximateSizeMm
            val afterSize = after.approximateSizeMm
            if (beforeSize != null || afterSize != null) {
                Spacer(Modifier.height(12.dp))
                SunnyCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Reference-based estimates", style = MaterialTheme.typography.labelLarge,
                            color = SunnyColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Before: ${beforeSize?.let(::formatMillimetres) ?: "Not recorded"}   ·   " +
                                "After: ${afterSize?.let(::formatMillimetres) ?: "Not recorded"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "These estimates depend on marker placement and are not clinical measurements.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareModeSelector(
    selected: CompareMode,
    motionEnabled: Boolean,
    onSelect: (CompareMode) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50))
            .background(SunnyColors.SurfaceMuted).padding(3.dp),
    ) {
        val modes = CompareMode.entries
        val segmentWidth = maxWidth / modes.size
        val segmentWidthPx = with(LocalDensity.current) { segmentWidth.toPx() }
        val selectedOffsetPx by animateFloatAsState(
            targetValue = segmentWidthPx * selected.ordinal,
            animationSpec = if (motionEnabled) {
                tween(durationMillis = SunnyMotion.StateMillis, easing = SunnyMotion.EaseInOut)
            } else {
                snap()
            },
            label = "Comparison mode indicator",
        )
        Box(
            Modifier.width(segmentWidth).fillMaxHeight().graphicsLayer {
                translationX = selectedOffsetPx
            }
                .clip(RoundedCornerShape(50)).background(SunnyColors.Surface),
        )
        Row(Modifier.fillMaxSize()) {
            modes.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        CompareModePictogram(
                            mode = mode,
                            tint = if (isSelected) SunnyColors.TextPrimary else SunnyColors.TextTertiary,
                        )
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareModePictogram(mode: CompareMode, tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx())
        val inset = 1.5.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())
        when (mode) {
            CompareMode.FADE -> {
                drawRoundRect(
                    color = tint.copy(alpha = 0.45f),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.58f),
                    cornerRadius = corner,
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.32f, size.height * 0.32f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.58f),
                    cornerRadius = corner,
                    style = stroke,
                )
            }
            CompareMode.WIPE -> {
                drawRoundRect(tint, cornerRadius = corner, style = stroke)
                drawLine(
                    tint,
                    start = Offset(size.width / 2f, inset),
                    end = Offset(size.width / 2f, size.height - inset),
                    strokeWidth = stroke.width,
                )
                drawCircle(tint, radius = 1.8.dp.toPx(), center = center)
            }
            CompareMode.BLINK -> {
                drawOval(
                    color = tint,
                    topLeft = Offset(inset, size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height * 0.5f),
                    style = stroke,
                )
                drawCircle(tint, radius = 2.dp.toPx(), center = center)
            }
            CompareMode.SIDE -> {
                val gap = 2.dp.toPx()
                val panelWidth = (size.width - gap) / 2f
                drawRoundRect(
                    tint,
                    size = androidx.compose.ui.geometry.Size(panelWidth, size.height),
                    cornerRadius = corner,
                    style = stroke,
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(panelWidth + gap, 0f),
                    size = androidx.compose.ui.geometry.Size(panelWidth, size.height),
                    cornerRadius = corner,
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun ComparisonDateRail(before: String, after: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            before,
            style = MaterialTheme.typography.labelMedium,
            color = SunnyColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val y = size.height / 2f
            val radius = 2.5.dp.toPx()
            drawCircle(SunnyColors.TextTertiary, radius, Offset(radius, y))
            drawLine(
                SunnyColors.TextTertiary,
                Offset(radius * 2.5f, y),
                Offset(size.width - radius * 2.5f, y),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                SunnyColors.TextTertiary,
                Offset(size.width - radius * 4f, y - radius * 1.7f),
                Offset(size.width - radius, y),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                SunnyColors.TextTertiary,
                Offset(size.width - radius * 4f, y + radius * 1.7f),
                Offset(size.width - radius, y),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        Text(
            after,
            style = MaterialTheme.typography.labelMedium,
            color = SunnyColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FadeCompare(
    before: ObservationEntity,
    after: ObservationEntity,
    transform: AlignTransform,
    alignOn: Boolean,
    fade: Float,
    onFade: (Float) -> Unit,
    onNudge: (Offset) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
            .background(SunnyColors.SurfaceMuted),
    ) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        AsyncImage(EncryptedImage(before.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val overlay = Modifier.fillMaxSize().applyAlign(transform).let {
            if (alignOn) it.pointerInput(before.imagePath, after.imagePath) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onNudge(Offset(drag.x / wPx, drag.y / hPx))
                }
            } else it
        }
        AsyncImage(EncryptedImage(after.imagePath), null, overlay, contentScale = ContentScale.Crop, alpha = fade)
        DateTag(Format.date(before.capturedAt), Alignment.TopStart, emphasis = 1f - fade)
        DateTag(Format.date(after.capturedAt), Alignment.TopEnd, emphasis = fade)
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
private fun WipeCompare(
    before: ObservationEntity,
    after: ObservationEntity,
    transform: AlignTransform,
    wipe: Float,
    onWipe: (Float) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
        .background(SunnyColors.SurfaceMuted)) {
        val fullWpx = with(density) { maxWidth.toPx() }
        // Before is the fixed base; the aligned "after" is revealed on the right.
        AsyncImage(EncryptedImage(before.imagePath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().drawWithContent {
                clipRect(left = size.width * wipe, top = 0f, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
            },
        ) {
            AsyncImage(EncryptedImage(after.imagePath), null, Modifier.fillMaxSize().applyAlign(transform),
                contentScale = ContentScale.Crop)
        }
        DateTag(Format.date(before.capturedAt), Alignment.TopStart, emphasis = 1f)
        DateTag(Format.date(after.capturedAt), Alignment.TopEnd, emphasis = 1f)
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
private fun BlinkCompare(
    before: ObservationEntity,
    after: ObservationEntity,
    transform: AlignTransform,
    showAfter: Boolean,
    playing: Boolean,
    onTogglePlaying: () -> Unit,
    onToggleFrame: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
            .background(SunnyColors.SurfaceMuted).clickable(onClick = onToggleFrame),
    ) {
        AsyncImage(
            EncryptedImage(if (showAfter) after.imagePath else before.imagePath),
            contentDescription = if (showAfter) "Newer comparison photo" else "Older comparison photo",
            modifier = Modifier.fillMaxSize().then(
                if (showAfter) Modifier.applyAlign(transform) else Modifier,
            ),
            contentScale = ContentScale.Crop,
        )
        DateTag(
            Format.date(if (showAfter) after.capturedAt else before.capturedAt),
            Alignment.TopStart,
            emphasis = 1f,
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            IconButton(onClick = onTogglePlaying) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause blinking" else "Play blinking",
                    tint = Color.White,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showAfter) "After" else "Before", style = MaterialTheme.typography.labelMedium,
            color = SunnyColors.TextSecondary)
        Text(if (playing) "Playing" else "Paused", style = MaterialTheme.typography.labelMedium,
            color = SunnyColors.TextTertiary)
    }
}

@Composable
private fun SideCompare(before: ObservationEntity, after: ObservationEntity, transform: AlignTransform) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Older on the left (untransformed reference).
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(SunnyColors.SurfaceMuted)) {
                AsyncImage(EncryptedImage(before.imagePath), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(6.dp))
            Text("Before", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(Format.date(before.capturedAt), style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary)
        }
        // Newer on the right, size-normalised to match the reference framing.
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(SunnyColors.SurfaceMuted).clipToBounds()) {
                AsyncImage(EncryptedImage(after.imagePath), null, Modifier.fillMaxSize().applyAlign(transform),
                    contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(6.dp))
            Text("After", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(Format.date(after.capturedAt), style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DateTag(
    text: String, align: Alignment, emphasis: Float,
) {
    val backgroundAlpha = 0.25f + 0.30f * emphasis.coerceIn(0f, 1f)
    Box(Modifier.align(align).padding(10.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PhotoPicker(
    label: String,
    obs: List<ObservationEntity>,
    indices: IntRange,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = SunnyColors.TextTertiary, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        indices.forEach { i ->
            val o = obs[i]
            SunnyChip(Format.date(o.capturedAt), selected = i == selected, onClick = { onSelect(i) })
        }
    }
}

private fun formatMillimetres(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()} mm" else "$rounded mm"
}
