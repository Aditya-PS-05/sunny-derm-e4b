package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.reminder.Reminder
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.DisclaimerCard
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.components.rememberNotificationRequester
import com.sunny.skin.ui.components.sunnySwitchColors
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.Format

@Composable
fun SettingsScreen(
    vm: SunnyViewModel,
    contentPadding: PaddingValues,
    onOpenReports: () -> Unit,
    onOpenModelSetup: () -> Unit,
    onSetupPin: () -> Unit,
    onChangePin: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val pinOn by vm.pinEnabled.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val recurring = reminders.firstOrNull { it.id == Reminder.RECURRING_ID }
    var interval by remember { mutableIntStateOf(recurring?.intervalDays ?: 30) }
    val requestNotif = rememberNotificationRequester()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 120.dp + contentPadding.calculateBottomPadding()),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionHeader("Privacy & Security")
        SunnyCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Lock, SunnyColors.Orange)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Lock (PIN)", style = MaterialTheme.typography.titleMedium)
                    Text("Require a PIN to open Sunny", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
                Switch(
                    checked = pinOn,
                    onCheckedChange = { on -> if (on) onSetupPin() else vm.clearPin() },
                    colors = sunnySwitchColors(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("When enabled, you will need to enter your PIN each time you open Sunny. " +
            "Your skin health data never leaves your device.",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp))
        if (pinOn) {
            Spacer(Modifier.height(8.dp))
            SunnyCard(onClick = onChangePin) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Lock, SunnyColors.TextPrimary)
                    Spacer(Modifier.size(12.dp))
                    Text("Change PIN", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = SunnyColors.TextTertiary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("Reminders")
        SunnyCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.NotificationsActive, SunnyColors.Orange)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Regular skin check", style = MaterialTheme.typography.titleMedium)
                    Text("Get reminded to check your skin", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
                Switch(
                    checked = recurring != null,
                    onCheckedChange = { on ->
                        if (on) requestNotif { vm.setRecurringReminder(true, interval) }
                        else vm.setRecurringReminder(false, 0)
                    },
                    colors = sunnySwitchColors(),
                )
            }
        }
        if (recurring != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Weekly" to 7, "Monthly" to 30, "Every 3 months" to 90).forEach { (label, d) ->
                    SunnyChip(label, selected = interval == d, onClick = {
                        interval = d
                        requestNotif { vm.setRecurringReminder(true, d) }
                    })
                }
            }
        }
        if (reminders.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("UPCOMING", style = MaterialTheme.typography.labelSmall,
                color = SunnyColors.TextTertiary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            reminders.forEach { r ->
                SunnyCard {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (r.recurring) intervalLabel(r.intervalDays)
                                else Format.date(r.triggerAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SunnyColors.TextSecondary,
                            )
                        }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(50))
                                .clickable { vm.cancelReminder(r.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove", tint = SunnyColors.TextTertiary,
                            modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("Reports")
        SunnyCard(onClick = onOpenReports) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.PictureAsPdf, SunnyColors.TextPrimary)
                Spacer(Modifier.size(12.dp))
                Text("Exported Reports", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary)
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("About")
        SunnyCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AboutRow(Icons.Filled.Info, "Version", "1.0 (1)")
                HorizontalDivider(color = SunnyColors.Divider)
                AboutRow(Icons.Filled.Memory, "AI Model", vm.modelName(), onClick = onOpenModelSetup)
                HorizontalDivider(color = SunnyColors.Divider)
                AboutRow(Icons.Filled.Shield, "Privacy Policy", "", onClick = onOpenPrivacy)
            }
        }
        Spacer(Modifier.height(20.dp))

        DisclaimerCard(
            title = "Medical Disclaimer",
            body = "Sunny is a skin tracking tool only. It does not provide medical diagnoses " +
                "or advice. Always consult a qualified healthcare professional for any skin concerns.",
        )
    }
}

private fun intervalLabel(days: Int): String = when (days) {
    7 -> "Every week"
    30 -> "Every month"
    90 -> "Every 3 months"
    else -> "Every $days days"
}

@Composable
private fun IconBadge(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SunnyColors.OrangeSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SunnyColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = SunnyColors.TextSecondary)
        if (onClick != null) {
            Spacer(Modifier.size(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary,
                modifier = Modifier.size(20.dp))
        }
    }
}
