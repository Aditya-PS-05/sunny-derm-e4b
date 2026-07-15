package com.sunny.skin.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.DisclaimerCard
import com.sunny.skin.ui.components.LiquidGlassDialog
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyToggle
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

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
    val improve by vm.improveSunny.collectAsStateWithLifecycle()
    val contributionStatus by vm.contributionStatus.collectAsStateWithLifecycle()
    val useServer by vm.useServerInference.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showContributionConsent by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }

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
                IconBadge(Icons.Filled.Lock, SunnyColors.Orange, iconSize = 30.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Lock (PIN)", style = MaterialTheme.typography.titleMedium)
                    Text("Require a PIN to open Sunny", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
                SunnyToggle(
                    checked = pinOn,
                    onCheckedChange = { on -> if (on) onSetupPin() else vm.clearPin() },
                    accessibilityLabel = "App lock",
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("When enabled, you will need to enter your PIN each time you open Sunny. " +
            com.sunny.skin.AppMode.dataPrivacySubtitle(vm.serverModeAvailable && useServer),
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

        SectionHeader("Your data")
        SunnyCard {
            Row(
                Modifier.fillMaxWidth().clickable { showExport = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.Download, SunnyColors.TextPrimary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Encrypted backup", style = MaterialTheme.typography.titleMedium)
                    Text("Export scans, notes, reminders and reports",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary)
            }
            HorizontalDivider(color = SunnyColors.Divider)
            Row(
                Modifier.fillMaxWidth().clickable { showDeleteAll = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.DeleteForever, SunnyColors.Danger)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Delete all local data", style = MaterialTheme.typography.titleMedium,
                        color = SunnyColors.Danger)
                    Text("Remove every scan, reminder and report",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                }
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

        if (vm.serverModeAvailable) {
            SectionHeader("Analysis source (beta)")
            SunnyCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Memory, SunnyColors.Orange, iconSize = 26.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (useServer) "Beta server" else "On-device",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (useServer) {
                                "Scans run on Sunny's GPU server. Photos leave the phone " +
                                    "to be described."
                            } else {
                                "Scans run entirely on this phone. Nothing leaves the device — " +
                                    "needs the 6 GB model installed on a 64-bit phone."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    SunnyToggle(
                        checked = useServer,
                        onCheckedChange = { vm.setUseServerInference(it) },
                        accessibilityLabel = "Use beta server for analysis",
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (com.sunny.skin.BuildConfig.SUNNY_CONTRIBUTE_URL.isNotBlank()) {
            SectionHeader("Help improve Sunny")
            SunnyCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Science, SunnyColors.Orange, iconSize = 26.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Contribute to improving Sunny",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Beta: your scans and any corrections are uploaded with your consent to help " +
                                "train Sunny's AI. Off by default — turn it off anytime.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    SunnyToggle(
                        checked = improve,
                        onCheckedChange = { enabled ->
                            if (enabled) showContributionConsent = true
                            else vm.setImproveSunny(false)
                        },
                        accessibilityLabel = "Contribute scans to improve Sunny",
                    )
                }
            }
            if (improve && contributionStatus != com.sunny.skin.ui.ContributionStatus.IDLE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    when (contributionStatus) {
                        com.sunny.skin.ui.ContributionStatus.UPLOADING -> "Sending latest contribution…"
                        com.sunny.skin.ui.ContributionStatus.SENT -> "Latest contribution sent."
                        com.sunny.skin.ui.ContributionStatus.FAILED ->
                            "Latest contribution could not be sent. Future scans remain enabled."
                        com.sunny.skin.ui.ContributionStatus.IDLE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contributionStatus == com.sunny.skin.ui.ContributionStatus.FAILED) {
                        SunnyColors.Danger
                    } else {
                        SunnyColors.TextSecondary
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        if (showContributionConsent) {
            AlertDialog(
                onDismissRequest = { showContributionConsent = false },
                title = { Text("Contribute beta scans?") },
                text = {
                    Text(
                        "Future saved scans will send the photo, body area, model description, " +
                            "any correction, device model and app version to Sunny's beta " +
                            "contribution server for model improvement. This is separate from " +
                            "inference and can be turned off for future scans at any time."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setImproveSunny(true)
                        showContributionConsent = false
                    }) { Text("I agree") }
                },
                dismissButton = {
                    TextButton(onClick = { showContributionConsent = false }) { Text("Cancel") }
                },
            )
        }


        if (showExport) {
            var password by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            var exporting by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            LiquidGlassDialog(onDismiss = { if (!exporting) showExport = false }) {
                Text("Encrypted backup", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp))
                Text(
                    "Use a password you can remember. Sunny cannot recover it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        when {
                            password.isNotEmpty() && password.length < 10 -> Text("Use at least 10 characters")
                            confirmPassword.isNotEmpty() && confirmPassword != password -> Text("Passwords do not match")
                            error != null -> Text(error.orEmpty())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    TextButton(onClick = { showExport = false }, enabled = !exporting) { Text("Cancel") }
                    TextButton(
                        enabled = !exporting && password.length >= 10 && password == confirmPassword,
                        onClick = {
                            exporting = true
                            val secret = password.toCharArray()
                            password = ""
                            confirmPassword = ""
                            vm.exportEncryptedBackup(secret) { uri, message ->
                                exporting = false
                                if (uri != null) {
                                    showExport = false
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share encrypted backup"))
                                } else {
                                    error = message ?: "Backup could not be created."
                                }
                            }
                        },
                    ) { Text("Export", color = SunnyColors.OrangeText, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (showDeleteAll) {
            AlertDialog(
                onDismissRequest = { showDeleteAll = false },
                title = { Text("Delete all local data?") },
                text = {
                    Text(
                        if (com.sunny.skin.AppMode.publicRelease) {
                            "Every scan, photo, size estimate, private note, ABCDE answer, reminder, " +
                                "photo-check session, report and encrypted backup will be permanently " +
                                "removed. This cannot be undone."
                        } else {
                            "Every scan, photo, size estimate, private note, ABCDE answer, reminder, " +
                                "photo-check session, report and cached backup will be permanently " +
                                "removed. Contributions already sent during " +
                                "the beta cannot be deleted from this phone. This cannot be undone."
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAll = false
                        vm.deleteAllLocalData {
                            android.widget.Toast.makeText(
                                context, "All local health data deleted.", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) { Text("Delete everything", color = SunnyColors.Danger, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
                },
            )
        }

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

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    // No background box — just the icon, kept in a 36dp slot so rows stay aligned.
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SunnyColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = SunnyColors.TextSecondary)
        if (onClick != null) {
            Spacer(Modifier.size(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary,
                modifier = Modifier.size(20.dp))
        }
    }
}
