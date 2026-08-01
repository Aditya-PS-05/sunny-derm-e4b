package com.sunny.skin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private data class Piece(
    val xFrac: Float,      // horizontal start position (0..1)
    val delay: Float,      // 0..1 fraction of the timeline before it starts
    val speed: Float,      // fall speed multiplier
    val sway: Float,       // horizontal sway amplitude in px
    val swayPhase: Float,
    val size: Float,       // px
    val color: Color,
    val balloon: Boolean,  // true = balloon oval + string, false = confetti rect
    val spin: Float,
)

// Warm "sunshine" spread — tints of the brand orange, from deep flame through
// amber and gold to soft peach and cream. Cohesive with Sunny's calm orange/grey
// palette; celebratory without the off-brand rainbow of cool hues.
private val PALETTE = listOf(
    SunnyColors.FlameCore,   // warm red-orange
    SunnyColors.OrangeDark,  // deep orange
    SunnyColors.Orange,      // brand orange
    Color(0xFFFFB84D),       // amber
    Color(0xFFFFCF7A),       // gold
    Color(0xFFFF9E78),       // soft coral / peach
    Color(0xFFF26D4E),       // warm terracotta (keeps contrast on the light canvas)
)

/** Compact, one-time welcome treatment; the normal header mascot remains in its place. */
@Composable
fun SunnyGreeting(
    modifier: Modifier = Modifier,
    durationMs: Int = 1_200,
    onFinished: () -> Unit = {},
) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val alpha = remember { Animatable(if (motionEnabled) 0f else 1f) }

    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            alpha.animateTo(
                1f,
                animationSpec = tween(SunnyMotion.ScreenEnterMillis, easing = SunnyMotion.EaseOut),
            )
            delay((durationMs - SunnyMotion.ScreenEnterMillis * 2).coerceAtLeast(0).toLong())
            alpha.animateTo(
                0f,
                animationSpec = tween(SunnyMotion.ScreenEnterMillis, easing = SunnyMotion.EaseOut),
            )
        } else {
            delay(durationMs.toLong())
        }
        onFinished()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(52.dp).graphicsLayer { this.alpha = alpha.value }) {
            drawCircle(
                color = SunnyColors.OrangeSoft.copy(alpha = 0.9f),
                radius = size.minDimension * 0.48f,
            )
            drawCircle(
                color = SunnyColors.OrangeLight.copy(alpha = 0.45f),
                radius = size.minDimension * 0.36f,
            )
        }
        Image(
            painter = painterResource(R.drawable.sunny_mascot),
            contentDescription = "Sunny mascot",
            modifier = Modifier.size(44.dp),
        )
    }
}

/**
 * A one-shot earned celebration. Keep its warm palette and host it in a compact
 * container after a real milestone; it is no longer used as an Overview greeting.
 */
@Composable
fun BalloonDrop(
    modifier: Modifier = Modifier,
    count: Int = 18,
    durationMs: Int = 1_200,
    motionEnabled: Boolean = rememberSunnyMotionEnabled(),
    compact: Boolean = true,
    onFinished: () -> Unit = {},
) {
    val safeDurationMs = durationMs.coerceIn(0, 1_500)
    val pieces = remember(count, compact) {
        val rnd = Random(42)
        List(if (compact) count.coerceIn(0, 18) else count.coerceAtLeast(0)) {
            Piece(
                xFrac = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.4f,
                speed = 0.8f + rnd.nextFloat() * 0.6f,
                sway = 20f + rnd.nextFloat() * 40f,
                swayPhase = rnd.nextFloat() * 6.28f,
                size = 22f + rnd.nextFloat() * 26f,
                color = PALETTE[rnd.nextInt(PALETTE.size)],
                balloon = rnd.nextFloat() < 0.55f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
            )
        }
    }
    val progress = remember { Animatable(if (motionEnabled) 0f else 0.55f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(motionEnabled, safeDurationMs) {
        if (motionEnabled) {
            progress.animateTo(
                1f,
                animationSpec = tween(safeDurationMs, easing = SunnyMotion.EaseOut),
            )
        } else {
            val fadeMillis = minOf(SunnyMotion.StateMillis, safeDurationMs)
            delay((safeDurationMs - fadeMillis).toLong())
            if (fadeMillis > 0) {
                alpha.animateTo(
                    0f,
                    animationSpec = tween(fadeMillis, easing = SunnyMotion.EaseOut),
                )
            }
        }
        onFinished()
    }

    val canvasModifier = if (compact) {
        modifier.size(width = 180.dp, height = 140.dp)
    } else {
        modifier.fillMaxSize()
    }
    Canvas(canvasModifier.graphicsLayer { this.alpha = alpha.value }) {
        val h = size.height
        val w = size.width
        pieces.forEach { p ->
            // Local progress for this piece (respecting its start delay).
            val local = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val travel = (h + 160f) * p.speed
            val y = -80f + local * travel
            val x = p.xFrac * w + sin(local * 6.28f * 2 + p.swayPhase) * p.sway
            // Fade out over the last 25% of the fall.
            val alpha = if (local > 0.75f) (1f - (local - 0.75f) / 0.25f) else 1f
            val c = p.color.copy(alpha = alpha.coerceIn(0f, 1f))

            if (p.balloon) {
                // String
                drawLine(
                    color = c.copy(alpha = c.alpha * 0.5f),
                    start = Offset(x, y + p.size * 1.3f),
                    end = Offset(x, y + p.size * 2.1f),
                    strokeWidth = 2f,
                )
                // Balloon body (taller than wide)
                drawOval(
                    color = c,
                    topLeft = Offset(x - p.size * 0.45f, y),
                    size = Size(p.size * 0.9f, p.size * 1.3f),
                )
            } else {
                rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                    drawRect(
                        color = c,
                        topLeft = Offset(x - p.size * 0.28f, y - p.size * 0.18f),
                        size = Size(p.size * 0.56f, p.size * 0.36f),
                    )
                }
            }
        }
    }
}
