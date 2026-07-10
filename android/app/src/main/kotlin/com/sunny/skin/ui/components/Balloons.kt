package com.sunny.skin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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

private val PALETTE = listOf(
    Color(0xFFFF7A00), // orange
    Color(0xFFFFC94D), // yellow
    Color(0xFFEC6F9C), // pink
    Color(0xFF9B7EDE), // purple
    Color(0xFF4CC3C9), // teal
    Color(0xFF6FCF97), // green
    Color(0xFF5B8DEF), // blue
)

/**
 * A one-shot falling balloon + confetti greeting overlay. Plays for ~[durationMs]
 * then calls [onFinished]. Positions are stable across recompositions (seeded).
 */
@Composable
fun BalloonDrop(
    modifier: Modifier = Modifier,
    count: Int = 34,
    durationMs: Int = 4200,
    onFinished: () -> Unit = {},
) {
    val pieces = remember {
        val rnd = Random(42)
        List(count) {
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
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMs))
        onFinished()
    }

    Canvas(modifier.fillMaxSize()) {
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
