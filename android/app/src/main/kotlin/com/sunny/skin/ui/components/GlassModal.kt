package com.sunny.skin.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlinx.coroutines.delay

/**
 * A reusable modal: the app behind is blurred (API 31+) and dimmed, while form
 * content sits on one opaque surface so OEM renderers cannot expose child layers.
 * Content is a normal ColumnScope and receives the only safe dismissal path.
 */
@Composable
fun LiquidGlassDialog(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    AnimatedGlassDialog(
        onDismiss = onDismiss,
        dimAmount = 0.3f,
        dismissEnabled = dismissEnabled,
    ) { requestDismiss ->
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(24.dp, shape, clip = false)
                .clip(shape)
                .background(SunnyColors.Surface)
                .border(1.dp, SunnyColors.Divider, shape)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content(requestDismiss)
        }
    }
}

/** Owns the platform dialog until its visual exit has completed. */
@Composable
internal fun AnimatedGlassDialog(
    onDismiss: () -> Unit,
    dimAmount: Float,
    dismissEnabled: Boolean = true,
    content: @Composable (requestDismiss: () -> Unit) -> Unit,
) {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestDismissEnabled by rememberUpdatedState(dismissEnabled)
    var shown by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (latestDismissEnabled && !exitRequested) {
            exitRequested = true
            shown = false
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        val animatedDim by animateFloatAsState(
            targetValue = if (shown) dimAmount else 0f,
            animationSpec = tween(
                durationMillis = if (shown) {
                    SunnyMotion.ModalEnterMillis
                } else {
                    SunnyMotion.ModalExitMillis
                },
                easing = SunnyMotion.EaseOut,
            ),
            label = "Glass dialog scrim",
        )
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = 48 }
                }
            }
        }
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(animatedDim)
        }
        LaunchedEffect(Unit) {
            if (!exitRequested) shown = true
        }
        LaunchedEffect(exitRequested) {
            if (exitRequested) {
                delay(SunnyMotion.ModalExitMillis.toLong())
                latestOnDismiss()
            }
        }

        val motionEnabled = rememberSunnyMotionEnabled()
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(
                tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut),
            ) + if (motionEnabled) {
                scaleIn(
                    tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut),
                    initialScale = 0.96f,
                )
            } else {
                EnterTransition.None
            },
            exit = fadeOut(
                tween(SunnyMotion.ModalExitMillis, easing = SunnyMotion.EaseOut),
            ) + if (motionEnabled) {
                scaleOut(
                    tween(SunnyMotion.ModalExitMillis, easing = SunnyMotion.EaseOut),
                    targetScale = 0.98f,
                )
            } else {
                ExitTransition.None
            },
        ) {
            Box {
                content(requestDismiss)
                if (exitRequested) {
                    Box(
                        Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
