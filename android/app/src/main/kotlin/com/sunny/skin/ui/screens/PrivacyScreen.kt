package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

/** In-app privacy policy (mirrors PRIVACY.md). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverMode = com.sunny.skin.AppMode.serverActive(context)
    val serverCapable = com.sunny.skin.AppMode.serverMode
    ScreenScaffold(title = "Privacy", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            Text(if (serverMode) "Private local records" else "Private by construction",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (serverMode) {
                "Your saved records stay encrypted on this device. New scan photos are sent " +
                    "to Sunny's beta server for visual description."
            } else {
                "Your photos and their AI descriptions stay on this device. No account, " +
                    "no cloud sync, no analytics."
            },
                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(16.dp))

            PrivacyDataFlow(serverMode = serverMode, serverCapable = serverCapable)
            Spacer(Modifier.height(16.dp))

            Section(
                Icons.Filled.Lock,
                "What Sunny stores, and where",
                "Photos, descriptions, reference-based size estimates, PDF reports, ABCDE " +
                    "responses, reminder details and active photo-check progress are kept in this " +
                    "app's private storage. Private notes are stored with their scan. " +
                    "All of this is encrypted at rest using a key " +
                    "protected by Android Keystore (hardware-backed where supported). Reports " +
                    "are stream-decrypted when shared, without a plaintext cache copy. Your PIN is " +
                    "stored only as a strong salted verifier and also wraps the encryption key.",
            )
            Section(
                Icons.Filled.CloudUpload,
                "What leaves the device",
                if (serverMode) {
                    "For each analysis, the selected scan photo is sent to Sunny AI Cloud and " +
                        "a visual description is returned. A random installation ID and request " +
                        "counts enforce the cloud allowance; they contain no device, advertising, " +
                        "account or user ID. Reports and password-encrypted backups " +
                        "leave only when you explicitly share them."
                } else if (com.sunny.skin.AppMode.publicRelease) {
                    "Scan photos and descriptions do not leave for offline analysis. The model " +
                        "arrives with the app installation; Sunny does not download it later. A random " +
                        "installation ID is sent only for cloud access and quota accounting. Reports and " +
                        "password-encrypted backups leave only " +
                        "when you explicitly choose a destination in Android's share screen."
                } else {
                    "No scan data leaves for offline inference. The model arrives in Play App Bundle " +
                        "builds, and the app sends a random installation ID for quota accounting when the " +
                        "access service is configured. If the optional Help improve Sunny beta is configured and you " +
                        "explicitly enable it, contributed scans leave the device. Reports leave " +
                        "only when you explicitly share them. Password-encrypted backups leave " +
                        "only through the same explicit share action."
                },
            )
            if (serverCapable) {
                Section(
                    Icons.Filled.Info,
                    "Beta server limitation",
                    "The Android app cannot itself guarantee server-side retention, access logs, " +
                        "or deletion. Those controls must be documented and enforced by the beta " +
                        "server operator. Do not submit identifying photos."
                )
            }
            Section(
                Icons.Filled.PermDeviceInformation,
                "Permissions",
                if (com.sunny.skin.AppMode.publicRelease) {
                    "Camera (photos), Internet/Network state (optional cloud analysis), plus " +
                        "Notifications and Boot-completed (reminders)."
                } else {
                    "Camera (photos), Internet/Network state (configured beta inference/contribution), " +
                        "plus Notifications and Boot-completed (reminders)."
                },
            )
            Section(
                Icons.Filled.NoAccounts,
                "No tracking",
                "Sunny contains no analytics, advertising or third-party tracking. Its random " +
                    "installation ID is used only for daily and monthly quota accounting.",
            )
            Section(
                Icons.Filled.HealthAndSafety,
                "How Sunny works",
                "Sunny describes what your skin looks like to help you track it over time. It " +
                    "doesn't diagnose, assess risk, or tell you what to do — always see a " +
                    "qualified professional for any concern. Skin can look different in photos " +
                    "and across skin tones, so trust a clinician's eyes over the app.",
            )
            Section(
                Icons.Filled.Info,
                "Model and dataset notices",
                "Sunny uses a modified SmolVLM 500M model provided under Apache 2.0. " +
                    "It was fine-tuned, merged and quantized using " +
                    "PAD-UFES-20 smartphone images provided under CC BY 4.0. Dataset: " +
                    "Pacheco et al., https://doi.org/10.17632/zr7vgbcyr2.1. License: " +
                    "https://creativecommons.org/licenses/by/4.0/. The original creators, " +
                    "institutions and Hugging Face do not endorse Sunny. Complete notices and the " +
                    "Apache 2.0 license are included with the app.",
            )
            Section(
                Icons.Filled.DeleteSweep,
                "Your control",
                if (com.sunny.skin.AppMode.publicRelease) {
                    "Deleting a scan removes its photos, measurements and reminders. Delete all " +
                        "local data removes scans, notes, answers, reminders, photo-check progress, " +
                        "reports and encrypted backup files."
                } else {
                    "Deleting a scan removes its photos, measurements and reminders from the device. " +
                        "Settings also provides Delete all local data for scans, notes, answers, " +
                        "reminders, photo-check progress, reports and encrypted backup files. This " +
                        "cannot recall beta contributions already sent."
                },
            )
            Section(
                Icons.Filled.ContactMail,
                "Contact",
                com.sunny.skin.BuildConfig.SUNNY_PRIVACY_CONTACT.ifBlank {
                    "The monitored privacy contact will be published with the release build."
                },
            )
        }
    }
}

@Composable
private fun PrivacyDataFlow(serverMode: Boolean, serverCapable: Boolean) {
    val routeDescription = when {
        serverMode -> "Analysis now uses Sunny AI Cloud over HTTPS."
        serverCapable -> "Analysis now runs on this phone; the optional beta server path is off."
        else -> "Analysis runs on this phone."
    }
    SunnyCard(
        modifier = Modifier.semantics {
            contentDescription = "Scan data flow. A scan photo is saved in encrypted app-private " +
                "storage. $routeDescription"
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = SunnyColors.Orange,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Where a scan goes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
            FlowNode(
                icon = Icons.Filled.PhotoCamera,
                title = "Scan photo",
                detail = "Captured in Sunny",
            )
            FlowConnector()
            Text(
                "SAVED RECORD",
                style = MaterialTheme.typography.labelSmall,
                color = SunnyColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            FlowNode(
                icon = Icons.Filled.Security,
                title = "Encrypted local vault",
                detail = "App-private storage on this device",
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "ANALYSIS NOW",
                style = MaterialTheme.typography.labelSmall,
                color = SunnyColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (serverMode) {
                FlowNode(
                    icon = Icons.Filled.CloudUpload,
                    title = "Sunny AI Cloud over HTTPS",
                    detail = "Selected photo is sent; a visual description returns",
                )
            } else {
                FlowNode(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "On-device analysis",
                    detail = "No scan data is sent for inference",
                )
            }
            if (serverCapable && !serverMode) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(SunnyColors.SurfaceMuted, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudOff, null, tint = SunnyColors.TextTertiary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Beta server path is off",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "If enabled, selected photos are sent over HTTPS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SunnyColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowNode(icon: ImageVector, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SunnyColors.OrangeSoft.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(SunnyColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SunnyColors.OrangeText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = SunnyColors.TextSecondary)
        }
    }
}

@Composable
private fun FlowConnector() {
    Box(
        Modifier.padding(start = 18.dp).width(2.dp).height(14.dp)
            .background(SunnyColors.OrangeLight),
    )
}

@Composable
private fun Section(icon: ImageVector, title: String, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    SunnyCard(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(SunnyColors.SurfaceMuted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = SunnyColors.TextSecondary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = SunnyColors.TextTertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (expanded) body else body.substringBefore(". ").let {
                    if (it.endsWith('.')) it else "$it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}
