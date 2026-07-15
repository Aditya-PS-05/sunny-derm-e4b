package com.sunny.skin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

            Section(
                "What Sunny stores, and where",
                "Photos, descriptions, reference-based size estimates, PDF reports, ABCDE " +
                    "responses, reminder details and active photo-check progress are kept in this " +
                    "app's private storage. Private notes are stored with their scan. " +
                    "All of this is encrypted at rest using a key " +
                    "protected by Android Keystore (hardware-backed where supported). Reports " +
                    "are stream-decrypted when shared, without a plaintext cache copy. Your PIN is " +
                    "stored only as a salted hash, never in plain text.",
            )
            Section(
                "What leaves the device",
                if (com.sunny.skin.AppMode.publicRelease) {
                    "Scan photos and descriptions do not leave for analysis. Sunny only downloads " +
                        "model files over HTTPS. Reports and password-encrypted backups leave only " +
                        "when you explicitly choose a destination in Android's share screen."
                } else if (serverMode) {
                    "For each analysis, the selected scan photo is sent to the configured beta " +
                        "inference server and a visual description is returned. If you separately " +
                        "enable Help improve Sunny, the photo, body area, model description, any " +
                        "correction, device model and app version are also contributed for model " +
                        "improvement. Reports and password-encrypted backups leave only when you " +
                        "explicitly share them."
                } else {
                    "No scan data leaves for inference. The app can download model files over " +
                        "HTTPS. If the optional Help improve Sunny beta is configured and you " +
                        "explicitly enable it, contributed scans leave the device. Reports leave " +
                        "only when you explicitly share them. Password-encrypted backups leave " +
                        "only through the same explicit share action."
                },
            )
            if (serverCapable) {
                Section(
                    "Beta server limitation",
                    "The Android app cannot itself guarantee server-side retention, access logs, " +
                        "or deletion. Those controls must be documented and enforced by the beta " +
                        "server operator. Do not submit identifying photos."
                )
            }
            if (com.sunny.skin.AppMode.insecureBetaTransport) {
                Section(
                    "Development transport warning",
                    "This private beta build is configured with a cleartext HTTP test endpoint. " +
                        "It is blocked from release builds and must be replaced with HTTPS before " +
                        "testing with sensitive or identifying photos."
                )
            }
            Section(
                "Permissions",
                if (com.sunny.skin.AppMode.publicRelease) {
                    "Camera (photos), Internet/Network state and a Foreground service (model " +
                        "download), plus Notifications and Boot-completed (reminders)."
                } else {
                    "Camera (photos), Internet/Network state (model download and configured beta " +
                        "inference/contribution), Notifications and Boot-completed (reminders), " +
                        "and a Foreground service (model download)."
                },
            )
            Section(
                "No tracking",
                "Sunny contains no analytics, advertising or third-party tracking.",
            )
            Section(
                "How Sunny works",
                "Sunny describes what your skin looks like to help you track it over time. It " +
                    "doesn't diagnose, assess risk, or tell you what to do — always see a " +
                    "qualified professional for any concern. Skin can look different in photos " +
                    "and across skin tones, so trust a clinician's eyes over the app.",
            )
            Section(
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
                "Contact",
                com.sunny.skin.BuildConfig.SUNNY_PRIVACY_CONTACT.ifBlank {
                    "The monitored privacy contact will be published with the release build."
                },
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    SunnyCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
        }
    }
    Spacer(Modifier.height(12.dp))
}
