package com.sunny.skin.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sunny.skin.R
import com.sunny.skin.data.model.BodySide
import com.sunny.skin.data.model.BodyZone
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlin.math.roundToInt

/**
 * Front/back anatomical body figure (design reference). A single grey figure
 * asset is drawn, then each scanned [BodyZone] is re-drawn in the brand orange but
 * clipped to that zone's rectangle — so the highlight follows the body's real
 * contour (arms, torso, legs) instead of floating boxes. The figure image
 * already carries its own alpha, so the tint only ever paints on the body.
 * Zone rectangles are normalised (0..1) over the figure's 560:1151 canvas.
 */
@Composable
fun BodyTemplate(
    side: BodySide,
    highlighted: Set<BodyZone>,
    modifier: Modifier = Modifier,
    newlyHighlighted: BodyZone? = null,
) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val turn = remember { Animatable(0f) }
    var previousSide by remember { mutableStateOf(side) }
    val illumination = remember { Animatable(0f) }
    var illuminatedZones by remember { mutableStateOf(emptySet<BodyZone>()) }

    LaunchedEffect(side, motionEnabled) {
        if (side != previousSide) {
            previousSide = side
            if (motionEnabled) {
                turn.snapTo(if (side == BodySide.BACK) 12f else -12f)
                turn.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = SunnyMotion.ModalEnterMillis,
                        easing = SunnyMotion.EaseInOut,
                    ),
                )
            } else {
                turn.snapTo(0f)
            }
        }
    }

    LaunchedEffect(newlyHighlighted, motionEnabled) {
        if (newlyHighlighted != null && newlyHighlighted in highlighted) {
            illuminatedZones = setOf(newlyHighlighted)
            if (motionEnabled) {
                illumination.snapTo(1f)
                illumination.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = SunnyMotion.ModalEnterMillis,
                        easing = SunnyMotion.EaseOut,
                    ),
                )
            } else {
                illumination.snapTo(0.7f)
                kotlinx.coroutines.delay(SunnyMotion.StateMillis.toLong())
                illumination.snapTo(0f)
            }
            illuminatedZones = emptySet()
        } else {
            illuminatedZones = emptySet()
            illumination.snapTo(0f)
        }
    }

    val visibleHighlights = zoneRects(side)
        .map { it.first }
        .filter { it in highlighted }
        .joinToString { it.accessibilityLabel }
        .ifEmpty { "No tracked areas highlighted" }

    AnimatedContent(
        targetState = side,
        transitionSpec = {
            val duration = if (motionEnabled) {
                SunnyMotion.ModalEnterMillis
            } else {
                SunnyMotion.StateMillis
            }
            fadeIn(tween(duration, easing = SunnyMotion.EaseOut)) togetherWith
                fadeOut(tween(duration, easing = SunnyMotion.EaseOut))
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${side.label} body. $visibleHighlights"
            }
            .graphicsLayer {
                rotationY = if (motionEnabled) turn.value else 0f
                cameraDistance = 12f * density
            },
        label = "Overview body side",
    ) { visibleSide ->
        BodyFigure(
            side = visibleSide,
            highlighted = highlighted,
            illuminated = illuminatedZones,
            illumination = illumination.value,
        )
    }
}

@Composable
private fun BodyFigure(
    side: BodySide,
    highlighted: Set<BodyZone>,
    illuminated: Set<BodyZone>,
    illumination: Float,
) {
    val figure = ImageBitmap.imageResource(
        if (side == BodySide.FRONT) R.drawable.body_figure else R.drawable.body_figure_back,
    )
    val hot = SunnyColors.Orange

    Box(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth(0.28f)
                .aspectRatio(560f / 1151f)
                .align(Alignment.Center),
        ) {
            val w = size.width
            val h = size.height
            val dst = IntSize(w.roundToInt(), h.roundToInt())

            // Base grey figure.
            drawImage(
                figure, dstOffset = IntOffset.Zero, dstSize = dst,
                filterQuality = FilterQuality.High,
            )

            // Brand-orange highlight for each scanned zone, clipped to the zone's box
            // and (via the image's own alpha) to the body silhouette.
            val tint = ColorFilter.tint(hot, BlendMode.SrcIn)
            zoneRects(side).forEach { (zone, rects) ->
                if (zone in highlighted) {
                    rects.forEach { r ->
                        clipRect(r.l * w, r.t * h, r.r * w, r.b * h) {
                            drawImage(
                                figure, dstOffset = IntOffset.Zero, dstSize = dst,
                                colorFilter = tint, filterQuality = FilterQuality.High,
                            )
                        }
                    }
                }
                if (zone in illuminated && illumination > 0f) {
                    val glowTint = ColorFilter.tint(
                        SunnyColors.OrangeLight.copy(alpha = illumination),
                        BlendMode.SrcIn,
                    )
                    rects.forEach { r ->
                        clipRect(r.l * w, r.t * h, r.r * w, r.b * h) {
                            drawImage(
                                figure, dstOffset = IntOffset.Zero, dstSize = dst,
                                colorFilter = glowTint, filterQuality = FilterQuality.High,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BodyZone.accessibilityLabel: String
    get() = name.lowercase().replace('_', ' ')

/**
 * A small standalone thumbnail: the anatomical figure with a single [zone] lit
 * orange, sized by [modifier] (keeps the figure's 560:1151 aspect). Used as the
 * per-pose "asset" in the guided full-body flow so each row shows exactly where
 * on the body that step is — no generic icons, no coloured chips.
 */
@Composable
fun BodyZoneThumb(side: BodySide, zone: BodyZone, modifier: Modifier = Modifier) {
    val figure = ImageBitmap.imageResource(
        if (side == BodySide.FRONT) R.drawable.body_figure else R.drawable.body_figure_back,
    )
    val hot = SunnyColors.Orange
    val rects = zoneRects(side).firstOrNull { it.first == zone }?.second ?: emptyList()

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val iw = figure.width.toFloat()
        val ih = figure.height.toFloat()
        val dst = IntSize(w.roundToInt(), h.roundToInt())

        if (rects.isEmpty()) {
            drawImage(figure, dstOffset = IntOffset.Zero, dstSize = dst, filterQuality = FilterQuality.High)
            return@Canvas
        }

        // Bounding box of the zone (normalised over the figure).
        var l = 1f; var t = 1f; var r = 0f; var b = 0f
        rects.forEach { l = minOf(l, it.l); t = minOf(t, it.t); r = maxOf(r, it.r); b = maxOf(b, it.b) }
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f

        // A fixed-zoom crop window centred on the zone — shows the highlighted area
        // plus some body around it (not the whole figure). Width is derived from the
        // canvas aspect in PIXEL space so the crop is never distorted.
        val chN = 0.38f
        val cwN = (chN * (w / h) * (ih / iw)).coerceIn(0.02f, 1f)
        val clN = (cx - cwN / 2f).coerceIn(0f, (1f - cwN).coerceAtLeast(0f))
        val ctN = (cy - chN / 2f).coerceIn(0f, (1f - chN).coerceAtLeast(0f))

        val srcOff = IntOffset((clN * iw).roundToInt(), (ctN * ih).roundToInt())
        val srcSz = IntSize((cwN * iw).roundToInt(), (chN * ih).roundToInt())

        drawImage(
            figure, srcOffset = srcOff, srcSize = srcSz,
            dstOffset = IntOffset.Zero, dstSize = dst, filterQuality = FilterQuality.High,
        )

        // Brand-orange highlight, clipped to the zone box mapped into the cropped canvas.
        val tint = ColorFilter.tint(hot, BlendMode.SrcIn)
        rects.forEach { rr ->
            val x0 = (rr.l - clN) / cwN * w
            val y0 = (rr.t - ctN) / chN * h
            val x1 = (rr.r - clN) / cwN * w
            val y1 = (rr.b - ctN) / chN * h
            clipRect(x0, y0, x1, y1) {
                drawImage(
                    figure, srcOffset = srcOff, srcSize = srcSz,
                    dstOffset = IntOffset.Zero, dstSize = dst,
                    colorFilter = tint, filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

/**
 * Compact progress figure for summary cards. It uses the same detailed anatomy
 * asset and contour-clipped orange treatment as [BodyTemplate], without the
 * overview's side-switch animation or full-width sizing.
 *
 * The guided sequence includes front and back torso poses. A single compact
 * figure stays legible at thumbnail size, so back completion is projected onto
 * the matching front torso region rather than disappearing from the summary.
 */
@Composable
fun BodyProgressFigure(
    highlighted: Set<BodyZone>,
    modifier: Modifier = Modifier,
) {
    val side = BodySide.FRONT
    val figure = ImageBitmap.imageResource(R.drawable.body_figure)
    val projectedHighlights = highlighted.mapTo(mutableSetOf()) { zone ->
        when (zone) {
            BodyZone.UPPER_BACK -> BodyZone.CHEST
            BodyZone.LOWER_BACK -> BodyZone.ABDOMEN
            else -> zone
        }
    }

    Canvas(modifier) {
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        drawImage(
            figure,
            dstOffset = IntOffset.Zero,
            dstSize = dst,
            filterQuality = FilterQuality.High,
        )

        val tint = ColorFilter.tint(SunnyColors.Orange, BlendMode.SrcIn)
        zoneRects(side).forEach { (zone, rects) ->
            if (zone in projectedHighlights) {
                rects.forEach { rect ->
                    clipRect(
                        rect.l * size.width,
                        rect.t * size.height,
                        rect.r * size.width,
                        rect.b * size.height,
                    ) {
                        drawImage(
                            figure,
                            dstOffset = IntOffset.Zero,
                            dstSize = dst,
                            colorFilter = tint,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }
    }
}

/** Normalised zone box: left, top, right, bottom in 0..1 over the figure. */
private data class RectN(val l: Float, val t: Float, val r: Float, val b: Float)

/** Zone -> highlight rectangles, tuned to the anatomical figure asset. */
private fun zoneRects(side: BodySide): List<Pair<BodyZone, List<RectN>>> {
    val head = BodyZone.HEAD to listOf(RectN(0.35f, 0.00f, 0.65f, 0.105f))
    val neck = BodyZone.NECK to listOf(RectN(0.42f, 0.10f, 0.58f, 0.152f))
    val shoulder = BodyZone.SHOULDER to listOf(RectN(0.13f, 0.145f, 0.87f, 0.205f))
    val leftArm = BodyZone.LEFT_ARM to listOf(RectN(0.00f, 0.185f, 0.30f, 0.605f))
    val rightArm = BodyZone.RIGHT_ARM to listOf(RectN(0.70f, 0.185f, 1.00f, 0.605f))
    val leftLeg = BodyZone.LEFT_LEG to listOf(RectN(0.33f, 0.455f, 0.505f, 1.00f))
    val rightLeg = BodyZone.RIGHT_LEG to listOf(RectN(0.505f, 0.455f, 0.67f, 1.00f))

    val chestBox = RectN(0.27f, 0.205f, 0.73f, 0.305f)
    val abdomenBox = RectN(0.29f, 0.305f, 0.71f, 0.455f)
    val torso = if (side == BodySide.FRONT) {
        listOf(BodyZone.CHEST to listOf(chestBox), BodyZone.ABDOMEN to listOf(abdomenBox))
    } else {
        listOf(BodyZone.UPPER_BACK to listOf(chestBox), BodyZone.LOWER_BACK to listOf(abdomenBox))
    }
    return listOf(head, neck, shoulder, leftArm, rightArm) + torso + listOf(leftLeg, rightLeg)
}
