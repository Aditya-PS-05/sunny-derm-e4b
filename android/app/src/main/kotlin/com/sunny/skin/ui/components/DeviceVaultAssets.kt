package com.sunny.skin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sunny.skin.ui.theme.SunnyColors

/** Purpose-built Sunny artwork for the Device Vault; no generic status glyphs. */
enum class VaultAssetKind { VAULT, LOCK, ENCRYPTED, AI, CLEARED }

@Composable
fun SunnyVaultAsset(
    kind: VaultAssetKind,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ink = if (active) SunnyColors.OrangeText else SunnyColors.TextTertiary
    val fill = if (active) SunnyColors.OrangeSoft else SunnyColors.SurfaceMuted
    Canvas(modifier) {
        val stroke = size.minDimension * 0.065f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (kind != VaultAssetKind.AI) {
            drawCircle(fill, radius = size.minDimension / 2f, center = center)
        }

        when (kind) {
            VaultAssetKind.VAULT -> drawVaultShield(ink, stroke)
            VaultAssetKind.LOCK -> drawSunnyLock(ink, stroke)
            VaultAssetKind.ENCRYPTED -> drawEncryptedShield(ink, stroke)
            VaultAssetKind.AI -> drawCloudProcessing(ink, stroke)
            VaultAssetKind.CLEARED -> drawClearedVault(ink, stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVaultShield(
    ink: Color,
    stroke: Float,
) {
    val shield = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.21f)
        lineTo(size.width * 0.75f, size.height * 0.31f)
        lineTo(size.width * 0.71f, size.height * 0.61f)
        quadraticTo(size.width * 0.67f, size.height * 0.76f, size.width * 0.50f, size.height * 0.84f)
        quadraticTo(size.width * 0.33f, size.height * 0.76f, size.width * 0.29f, size.height * 0.61f)
        lineTo(size.width * 0.25f, size.height * 0.31f)
        close()
    }
    drawPath(shield, ink, style = Stroke(stroke, cap = StrokeCap.Round))
    drawCircle(SunnyColors.Orange, size.minDimension * 0.075f, Offset(size.width * 0.50f, size.height * 0.49f))
    drawLine(
        SunnyColors.Orange,
        Offset(size.width * 0.50f, size.height * 0.55f),
        Offset(size.width * 0.50f, size.height * 0.65f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunnyLock(
    ink: Color,
    stroke: Float,
) {
    drawArc(
        color = ink,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.34f, size.height * 0.25f),
        size = Size(size.width * 0.32f, size.height * 0.34f),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(size.width * 0.27f, size.height * 0.47f),
        size = Size(size.width * 0.46f, size.height * 0.32f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.07f),
        style = Stroke(stroke),
    )
    drawCircle(SunnyColors.Orange, size.minDimension * 0.045f, Offset(size.width * 0.50f, size.height * 0.62f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEncryptedShield(
    ink: Color,
    stroke: Float,
) {
    drawVaultShield(ink, stroke)
    drawLine(
        SunnyColors.Orange,
        Offset(size.width * 0.39f, size.height * 0.52f),
        Offset(size.width * 0.47f, size.height * 0.60f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        SunnyColors.Orange,
        Offset(size.width * 0.47f, size.height * 0.60f),
        Offset(size.width * 0.64f, size.height * 0.42f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudProcessing(
    ink: Color,
    stroke: Float,
) {
    val cloud = Path().apply {
        moveTo(size.width * 0.29f, size.height * 0.69f)
        cubicTo(
            size.width * 0.17f, size.height * 0.69f,
            size.width * 0.16f, size.height * 0.51f,
            size.width * 0.29f, size.height * 0.47f,
        )
        cubicTo(
            size.width * 0.32f, size.height * 0.31f,
            size.width * 0.51f, size.height * 0.26f,
            size.width * 0.62f, size.height * 0.39f,
        )
        cubicTo(
            size.width * 0.76f, size.height * 0.36f,
            size.width * 0.86f, size.height * 0.48f,
            size.width * 0.82f, size.height * 0.60f,
        )
        cubicTo(
            size.width * 0.80f, size.height * 0.66f,
            size.width * 0.75f, size.height * 0.69f,
            size.width * 0.68f, size.height * 0.69f,
        )
        close()
    }
    drawPath(cloud, ink, style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClearedVault(
    ink: Color,
    stroke: Float,
) {
    drawRoundRect(
        color = ink,
        topLeft = Offset(size.width * 0.26f, size.height * 0.38f),
        size = Size(size.width * 0.48f, size.height * 0.34f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.06f),
        style = Stroke(stroke),
    )
    drawLine(ink, Offset(size.width * 0.32f, size.height * 0.31f), Offset(size.width * 0.68f, size.height * 0.31f), stroke, StrokeCap.Round)
    drawLine(SunnyColors.Orange, Offset(size.width * 0.50f, size.height * 0.18f), Offset(size.width * 0.50f, size.height * 0.27f), stroke, StrokeCap.Round)
    drawLine(SunnyColors.Orange, Offset(size.width * 0.37f, size.height * 0.22f), Offset(size.width * 0.41f, size.height * 0.29f), stroke, StrokeCap.Round)
    drawLine(SunnyColors.Orange, Offset(size.width * 0.63f, size.height * 0.22f), Offset(size.width * 0.59f, size.height * 0.29f), stroke, StrokeCap.Round)
}
