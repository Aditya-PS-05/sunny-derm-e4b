package com.sunny.skin.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sunny.skin.reminder.ReminderInterval
import com.sunny.skin.reminder.ReminderIntervalUnit
import com.sunny.skin.reminder.selectedReminderIntervalLabel
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
 * is requested (API 33+). The reminder is scheduled regardless — the alarm still
 * fires — but [afterAsk] receives whether notifications are actually enabled, so
 * callers can avoid promising a nudge that would be silently suppressed.
 */
@Composable
fun rememberNotificationRequester(): (afterAsk: (granted: Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> pending?.invoke(granted); pending = null }

    return remember {
        { afterAsk ->
            val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                afterAsk(true)
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
    var selected by remember { mutableStateOf<Int?>(null) }
    var pendingPick by remember { mutableStateOf<Int?>(null) }

    AnimatedGlassDialog(
        onDismiss = {
            val days = pendingPick
            if (days != null) {
                pendingPick = null
                onPick(days)
            } else {
                onDismiss()
            }
        },
        dimAmount = 0.28f,
    ) { requestDismiss ->
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
                enabled = selected != null,
                onClick = {
                    selected?.let {
                        pendingPick = it
                        requestDismiss()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = requestDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium,
                    color = SunnyColors.TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun RecurringIntervalDialog(
    initialHours: Int,
    onPick: (hours: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(initialHours) { ReminderInterval.fromHours(initialHours) }
    var unit by remember(initialHours) { mutableStateOf(initial.unit) }
    var valueText by remember(initialHours) { mutableStateOf(initial.value.toString()) }
    val value = valueText.toIntOrNull()?.takeIf { it in 1..unit.maxValue }
    val totalHours = value?.let { ReminderInterval(it, unit).totalHours }

    var pendingHours by remember { mutableStateOf<Int?>(null) }
    LiquidGlassDialog(
        onDismiss = {
            val hours = pendingHours
            if (hours != null) {
                pendingHours = null
                onPick(hours)
            } else {
                onDismiss()
            }
        },
    ) { requestDismiss ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(SunnyColors.OrangeSoft.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = SunnyColors.Orange,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Reminder interval",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
            )
            Spacer(Modifier.height(16.dp))

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderIntervalUnit.entries.forEach { option ->
                    SunnyChip(
                        label = option.label,
                        selected = unit == option,
                        onClick = {
                            val current = ReminderInterval(value ?: 1, unit)
                            val converted = current.convertedTo(option)
                            unit = option
                            valueText = converted.value.toString()
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(SunnyColors.SurfaceMuted.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = (value ?: 1) > 1,
                    onClick = { valueText = ((value ?: 1) - 1).coerceAtLeast(1).toString() },
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease interval")
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all(Char::isDigit)) {
                            valueText = input.take(4)
                        }
                    },
                    modifier = Modifier.width(112.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = SunnyColors.Orange,
                        unfocusedBorderColor = SunnyColors.Divider,
                        cursorColor = SunnyColors.Orange,
                    ),
                )
                IconButton(
                    enabled = (value ?: 0) < unit.maxValue,
                    onClick = {
                        valueText = ((value ?: 0) + 1).coerceAtMost(unit.maxValue).toString()
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase interval")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value?.let { selectedReminderIntervalLabel(it, unit) } ?: "Enter an interval",
                style = MaterialTheme.typography.bodyLarge,
                color = SunnyColors.TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    totalHours?.let {
                        pendingHours = it
                        requestDismiss()
                    }
                },
                enabled = totalHours != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
            ) {
                Text("Set interval", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = requestDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Cancel", color = SunnyColors.TextSecondary)
            }
        }
    }
}

/** Opaque modal surface that remains legible across OEM blur implementations. */
@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(SunnyColors.Surface)
            .border(
                BorderStroke(1.dp, SunnyColors.Divider),
                RoundedCornerShape(30.dp),
            )
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(SunnyColors.Action),
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
private fun GlassButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
                Brush.verticalGradient(
                    if (enabled) listOf(SunnyColors.Action, SunnyColors.OrangeDark)
                    else listOf(SunnyColors.TextTertiary, SunnyColors.TextTertiary),
                ),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                RoundedCornerShape(27.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium,
            color = Color.White, fontWeight = FontWeight.Bold)
    }
}
