package com.sunny.skin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sunny.skin.R
import com.sunny.skin.data.model.BodySide
import com.sunny.skin.data.model.BodyZone
import kotlin.math.roundToInt

/**
 * Front/back anatomical body figure (design reference). A single grey figure
 * asset is drawn, then each scanned [BodyZone] is re-drawn tinted amber but
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
) {
    val figure = ImageBitmap.imageResource(
        if (side == BodySide.FRONT) R.drawable.body_figure else R.drawable.body_figure_back,
    )
    val hot = Color(0xFFF2A33C)  // amber highlight

    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth(0.40f)
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

            // Amber highlight for each scanned zone, clipped to the zone's box
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
