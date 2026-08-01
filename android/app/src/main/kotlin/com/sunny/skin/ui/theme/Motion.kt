package com.sunny.skin.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shared timing and easing values for Sunny's calm, responsive motion. */
object SunnyMotion {
    const val PressMillis = 140
    const val StateMillis = 180
    const val ScreenEnterMillis = 240
    const val ScreenExitMillis = 180
    const val ModalEnterMillis = 220
    const val ModalExitMillis = 160

    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val DrawerEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}

/**
 * Whether spatial motion is enabled by the system. Color and opacity feedback
 * may remain visible when this is false, but translation, rotation, and scale
 * should resolve without animation.
 */
@Composable
fun rememberSunnyMotionEnabled(): Boolean = remember {
    ValueAnimator.areAnimatorsEnabled()
}

/**
 * Applies Sunny's subtle press response while sharing [interactionSource] with
 * the clickable modifier so ripple and scale observe the same gesture.
 */
@Composable
fun Modifier.sunnyPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberSunnyMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (enabled && motionEnabled && isPressed) 0.97f else 1f,
        animationSpec = if (motionEnabled) {
            tween(durationMillis = SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Sunny press scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
