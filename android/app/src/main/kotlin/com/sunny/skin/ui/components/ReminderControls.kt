package com.sunny.skin.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.sunny.skin.ui.theme.SunnyColors

/** Selectable delay options for a re-check reminder. */
val RECHECK_OPTIONS = listOf(
    "In 2 weeks" to 14,
    "In 1 month" to 30,
    "In 3 months" to 90,
    "In 6 months" to 180,
)

/**
 * Returns a lambda that runs [afterAsk] after ensuring notification permission
 * is requested (API 33+). The reminder is scheduled regardless of the result —
 * the alarm still fires; only the visible notification needs the grant.
 */
@Composable
fun rememberNotificationRequester(): (afterAsk: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { pending?.invoke(); pending = null }

    return remember {
        { afterAsk ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                afterAsk()
            } else {
                pending = afterAsk
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * iOS-style "liquid glass" modal to pick when to be reminded to re-check a spot.
 * The dialog window blurs whatever is behind it (API 31+) and the card is a
 * translucent frosted panel with a soft glass-sheen border, so the app colours
 * bloom through. Grouped rows with an orange check mark the selection.
 */
@Composable
fun ReCheckReminderDialog(
    onPick: (days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(30) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Blur the app behind the dialog for the "glass" depth (API 31+). The
        // dim keeps the frosted panel readable on both light and busy backdrops.
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setDimAmount(0.28f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = 48 }
                }
            }
        }

        // Enter with a gentle iOS spring: fade + slight scale up.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.92f),
        ) {
            GlassCard {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(SunnyColors.OrangeSoft.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = SunnyColors.Orange,
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Remind me to re-check",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SunnyColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sunny will nudge you to re-photograph this spot so you can compare it over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))

                // Grouped selectable rows — hairline separators, iOS inset list.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.55f)),
                ) {
                    RECHECK_OPTIONS.forEachIndexed { i, (label, days) ->
                        OptionRow(
                            label = label,
                            selected = selected == days,
                            onClick = { selected = days },
                        )
                        if (i < RECHECK_OPTIONS.lastIndex) {
                            Box(
                                Modifier.fillMaxWidth().height(1.dp)
                                    .padding(start = 18.dp)
                                    .background(Color.White.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Primary action — orange glass pill.
                GlassButton(
                    text = "Set Reminder",
                    onClick = { onPick(selected) },
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium,
                        color = SunnyColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Translucent frosted panel with a soft top-lit glass sheen and hairline rim. */
@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.86f),
                        Color.White.copy(alpha = 0.72f),
                    ),
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.2f),
                        ),
                    ),
                ),
                RoundedCornerShape(30.dp),
            )
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(if (selected) SunnyColors.OrangeSoft.copy(alpha = 0.55f) else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = SunnyColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .border(1.5.dp, SunnyColors.TextTertiary.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
}

@Composable
private fun GlassButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
                Brush.verticalGradient(listOf(SunnyColors.Orange, SunnyColors.OrangeDark)),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                RoundedCornerShape(27.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium,
            color = Color.White, fontWeight = FontWeight.Bold)
    }
}
