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
    ScreenScaffold(title = "Privacy", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            Text("Private by construction", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Your photos and their AI descriptions stay on this device. No account, " +
                "no cloud sync, no analytics.",
                style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(16.dp))

            Section(
                "What Sunny stores, and where",
                "Photos you capture, the model's description of each, and any PDF reports are " +
                    "kept in this app's private storage only — and encrypted at rest using a " +
                    "key held in your device's hardware-backed keystore. Your app-lock PIN is " +
                    "stored only as a salted hash, never in plain text.",
            )
            Section(
                "What leaves the device",
                "Nothing about you. The only network use is an optional, one-time download of " +
                    "the AI model files over HTTPS — it only pulls model data and never sends " +
                    "anything, and can be limited to Wi-Fi. A report leaves the device only if " +
                    "you tap Share and choose where it goes.",
            )
            Section(
                "Permissions",
                "Camera (to take photos), Internet/Network state (only the optional model " +
                    "download and a Wi-Fi check), Notifications and Boot-completed (skin-check " +
                    "reminders), and a Foreground service (to keep the one-time model download " +
                    "alive).",
            )
            Section(
                "No tracking",
                "Sunny contains no analytics, advertising or third-party tracking.",
            )
            Section(
                "Medical disclaimer",
                "Sunny is a tracking tool only. It provides visual descriptions, not medical " +
                    "diagnoses or advice. Always consult a qualified healthcare professional for " +
                    "any skin concern.",
            )
            Section(
                "Your control",
                "Deleting a scan removes its photos from the device. Uninstalling the app " +
                    "removes all of its data.",
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
