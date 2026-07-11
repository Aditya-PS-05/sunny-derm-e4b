package com.sunny.skin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

/** In-app privacy policy (mirrors PRIVACY.md), styled as an iOS grouped list. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Privacy", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            Text(
                "Private by construction",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your photos and their AI descriptions stay on this device — no account, " +
                    "no cloud sync, no analytics.",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.height(20.dp))

            Group(
                "On this device", Icons.Filled.Lock,
                "Photos you capture, the model's description of each, and any PDF reports are " +
                    "kept in this app's private storage only — and encrypted at rest using a " +
                    "key held in your device's hardware-backed keystore.",
            )
            Group(
                "App lock", Icons.Filled.Key,
                "Your app-lock PIN is stored only as a salted hash, never in plain text, and " +
                    "repeated wrong attempts are rate-limited.",
            )
            Group(
                "What leaves the device", Icons.Filled.CloudOff,
                "Nothing about you. The only network use is an optional, one-time download of " +
                    "the AI model files over HTTPS — it only pulls model data and never sends " +
                    "anything, and can be limited to Wi-Fi. A report leaves the device only if " +
                    "you tap Share and choose where it goes.",
            )
            Group(
                "No tracking", Icons.Filled.VisibilityOff,
                "Sunny contains no analytics, advertising or third-party tracking. Deleting a " +
                    "scan removes its photos; uninstalling removes all of its data.",
            )
            Group(
                "Not a diagnosis", Icons.Filled.MedicalServices,
                "Sunny is a tracking tool only. It provides visual descriptions, not medical " +
                    "diagnoses or advice. Always consult a qualified healthcare professional " +
                    "for any skin concern.",
            )
        }
    }
}

/** iOS grouped section: a grey caption above a white rounded card with an icon + body. */
@Composable
private fun Group(caption: String, icon: ImageVector, body: String) {
    SectionHeader(caption)
    SunnyCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(SunnyColors.OrangeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = SunnyColors.Orange, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Spacer(Modifier.height(18.dp))
}
