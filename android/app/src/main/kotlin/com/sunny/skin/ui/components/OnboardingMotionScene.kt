package com.sunny.skin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion

/**
 * Persistent onboarding visual inspired by shared-element and morph interactions:
 * the same capture tile moves from camera framing, through a dated timeline, into
 * encrypted local storage as the pager is dragged.
 */
@Composable
fun OnboardingMotionScene(
    progress: Float,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var appeared by remember { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { appeared = true }
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) SunnyMotion.ScreenEnterMillis else 0,
            easing = SunnyMotion.EaseOut,
        ),
        label = "onboarding scene entrance",
    )
    val captureFlash = remember { Animatable(if (motionEnabled) 1f else 0f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            captureFlash.snapTo(1f)
            captureFlash.animateTo(0f, tween(durationMillis = 100, easing = LinearEasing))
        } else {
            captureFlash.snapTo(0f)
        }
    }
    val safeProgress = progress.coerceIn(0f, 2f)
    val timeline = segment(safeProgress, 0.2f, 1f)
    val privacy = segment(safeProgress, 1.15f, 2f)
    val descriptions = listOf(
        "A guided camera frame around a Sunny photo tile.",
        "The photo tile becomes a dated three-photo timeline.",
        "The timeline moves into encrypted storage on this phone.",
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance
                scaleX = 0.96f + entrance * 0.04f
                scaleY = 0.96f + entrance * 0.04f
            }
            .clearAndSetSemantics {
                contentDescription = descriptions[safeProgress.toInt().coerceIn(0, 2)]
            },
        contentAlignment = Alignment.Center,
    ) {
        val spread = minOf(maxWidth * 0.27f, 94.dp)

        MotionGrid(Modifier.fillMaxSize(), privacy)
        CameraFrame(
            alpha = 1f - timeline,
            motionEnabled = motionEnabled,
            modifier = Modifier.align(Alignment.Center),
        )
        TimelineTrack(
            spread = spread,
            progress = timeline,
            alpha = timeline * (1f - privacy),
            modifier = Modifier.fillMaxSize(),
        )
        LocalVault(
            alpha = privacy,
            checkProgress = segment(safeProgress, 1.72f, 2f),
            motionEnabled = motionEnabled,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                translationY = 10.dp.toPx()
            },
        )

        SnapshotTile(
            label = "FIRST",
            alpha = timeline * (1f - privacy),
            modifier = Modifier.align(Alignment.Center)
                .graphicsLayer {
                    translationX = -spread.toPx() * timeline
                    translationY = 12.dp.toPx()
                    scaleX = 0.66f
                    scaleY = 0.66f
                    rotationZ = if (motionEnabled) -7f * timeline else 0f
                },
        )
        SnapshotTile(
            label = "LATEST",
            alpha = timeline * (1f - privacy),
            modifier = Modifier.align(Alignment.Center)
                .graphicsLayer {
                    translationX = spread.toPx() * timeline
                    translationY = 12.dp.toPx()
                    scaleX = 0.66f
                    scaleY = 0.66f
                    rotationZ = if (motionEnabled) 7f * timeline else 0f
                },
        )

        CaptureTile(
            flashAlpha = captureFlash.value,
            modifier = Modifier.align(Alignment.Center)
                .graphicsLayer {
                    translationX = (-42).dp.toPx() * privacy
                    translationY = 8.dp.toPx() * timeline + 10.dp.toPx() * privacy
                    val scale = 1f - 0.22f * timeline - 0.14f * privacy
                    scaleX = scale
                    scaleY = scale
                    rotationZ = if (motionEnabled) 2f * timeline * (1f - privacy) else 0f
                },
        )

        Image(
            painter = painterResource(R.drawable.sunny_mascot),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(end = 18.dp, top = 8.dp)
                .size(70.dp)
                .graphicsLayer {
                    translationY = 8.dp.toPx() * timeline
                    rotationZ = if (motionEnabled) -4f * timeline + 4f * privacy else 0f
                    alpha = 0.92f
                },
        )
    }
}

@Composable
private fun MotionGrid(modifier: Modifier, privacy: Float) {
    Canvas(modifier) {
        val color = SunnyColors.Divider.copy(alpha = 0.48f * (1f - privacy))
        val gap = 34.dp.toPx()
        var y = gap
        while (y < size.height) {
            drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += gap
        }
    }
}

@Composable
private fun CameraFrame(alpha: Float, motionEnabled: Boolean, modifier: Modifier = Modifier) {
    if (motionEnabled) {
        AnimatedCameraFrame(alpha = alpha, modifier = modifier)
    } else {
        CameraFrameCanvas(alpha = alpha, scan = 0.5f, modifier = modifier)
    }
}

@Composable
private fun AnimatedCameraFrame(alpha: Float, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "camera scan")
    val animatedScan by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1_350, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan position",
    )
    CameraFrameCanvas(alpha = alpha, scan = animatedScan, modifier = modifier)
}

@Composable
private fun CameraFrameCanvas(alpha: Float, scan: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(218.dp).graphicsLayer { this.alpha = alpha }) {
        val orange = SunnyColors.Orange
        val corner = 30.dp.toPx()
        val inset = 10.dp.toPx()
        val stroke = 3.dp.toPx()
        fun cornerLines(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(orange, androidx.compose.ui.geometry.Offset(x, y),
                androidx.compose.ui.geometry.Offset(x + corner * sx, y), stroke, StrokeCap.Round)
            drawLine(orange, androidx.compose.ui.geometry.Offset(x, y),
                androidx.compose.ui.geometry.Offset(x, y + corner * sy), stroke, StrokeCap.Round)
        }
        cornerLines(inset, inset, 1f, 1f)
        cornerLines(size.width - inset, inset, -1f, 1f)
        cornerLines(inset, size.height - inset, 1f, -1f)
        cornerLines(size.width - inset, size.height - inset, -1f, -1f)
        val y = size.height * scan
        drawLine(
            orange.copy(alpha = 0.34f),
            androidx.compose.ui.geometry.Offset(28.dp.toPx(), y),
            androidx.compose.ui.geometry.Offset(size.width - 28.dp.toPx(), y),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
private fun TimelineTrack(
    spread: Dp,
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + 12.dp.toPx())
        val radius = 5.dp.toPx()
        val startX = center.x - spread.toPx()
        val endX = center.x + spread.toPx()
        drawLine(
            SunnyColors.OrangeLight,
            center.copy(x = startX),
            center.copy(x = startX + (endX - startX) * progress),
            4.dp.toPx(),
            StrokeCap.Round,
        )
        listOf(0f, 0.5f, 1f).forEach { milestone ->
            val nodeAlpha = if (progress >= milestone) 1f else 0.28f
            val nodeCenter = center.copy(x = startX + (endX - startX) * milestone)
            drawCircle(SunnyColors.Surface.copy(alpha = nodeAlpha), radius + 3.dp.toPx(), nodeCenter)
            drawCircle(SunnyColors.Orange.copy(alpha = nodeAlpha), radius, nodeCenter)
        }
    }
}

@Composable
private fun CaptureTile(flashAlpha: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier.size(142.dp).shadow(14.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = SunnyColors.Surface,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize().padding(9.dp).clip(RoundedCornerShape(19.dp))
            .background(SunnyColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.sunny_mascot),
                contentDescription = null,
                modifier = Modifier.size(104.dp),
            )
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.42f * flashAlpha)),
            )
            Box(
                Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape)
                    .background(SunnyColors.OrangeText),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun SnapshotTile(label: String, alpha: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier.size(128.dp).graphicsLayer { this.alpha = alpha }
            .shadow(10.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = SunnyColors.Surface,
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(14.dp))
                    .background(SunnyColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = SunnyColors.Orange,
                    modifier = Modifier.size(26.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = SunnyColors.TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LocalVault(
    alpha: Float,
    checkProgress: Float,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.size(258.dp, 174.dp).graphicsLayer {
            this.alpha = alpha
            scaleX = 0.94f + 0.06f * alpha
            scaleY = 0.94f + 0.06f * alpha
        }.shadow(16.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = SunnyColors.Surface,
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 118.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape)
                    .background(SunnyColors.SurfaceMuted))
                Box(Modifier.fillMaxWidth(0.72f).height(9.dp).clip(CircleShape)
                    .background(SunnyColors.SurfaceMuted))
                Box(Modifier.fillMaxWidth(0.88f).height(9.dp).clip(CircleShape)
                    .background(SunnyColors.OrangeSoft))
            }
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(SunnyColors.OrangeSoft)
                    .border(1.dp, SunnyColors.OrangeLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = SunnyColors.OrangeText,
                    modifier = Modifier.size(26.dp))
                Box(
                    Modifier.align(Alignment.BottomEnd).size(21.dp)
                        .graphicsLayer {
                            this.alpha = checkProgress
                            val checkScale = if (motionEnabled) 0.82f + 0.18f * checkProgress else 1f
                            scaleX = checkScale
                            scaleY = checkScale
                        }.clip(CircleShape).background(SunnyColors.OrangeText),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)
