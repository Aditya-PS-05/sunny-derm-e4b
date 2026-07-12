package com.sunny.skin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.inference.download.ModelStatus
import com.sunny.skin.ui.components.DisclaimerCard
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

/**
 * First-run model download. Scanning remains disabled until the real ~6 GB
 * weights and native runtime are available. The
 * download is Wi-Fi-gated, resumable and checksum-verified, and never transmits
 * any user data — it only pulls the model files.
 */
@Composable
fun ModelSetupScreen(onBack: () -> Unit) {
    val status by ModelDownloadManager.status.collectAsStateWithLifecycle()
    // Pick up weights that were adb-pushed onto the device while the app was open.
    androidx.compose.runtime.LaunchedEffect(Unit) { ModelDownloadManager.refresh() }

    ScreenScaffold(title = "AI Model", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            StatusHeader(status)
            Spacer(Modifier.height(20.dp))

            SunnyCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Sunny-Gemma4-E4B", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("On-device dermatology describer. ~6.0 GB " +
                        "(5.0 GB language model + 990 MB vision). Runs fully offline once installed.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))

            when (val s = status) {
                ModelStatus.NotConfigured -> NotConfiguredBody()
                ModelStatus.Idle, is ModelStatus.Failed -> {
                    (s as? ModelStatus.Failed)?.let {
                        Text(it.message, color = SunnyColors.Danger,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                    }
                    WifiNote()
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { ModelDownloadManager.start() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
                    ) {
                        Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (s is ModelStatus.Failed) "Retry download" else "Download AI Model",
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                is ModelStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = SunnyColors.Orange, trackColor = SunnyColors.SurfaceMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("${gib(s.done)} / ${gib(s.total)}  ·  ${(s.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { ModelDownloadManager.cancel() },
                        modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                ModelStatus.Verifying -> Row(text = "Verifying checksums…")
                ModelStatus.Ready -> Text("The AI model is installed and running on-device.",
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
            DisclaimerCard(
                title = "Research limitation",
                body = "The download pulls only model files, but the model is experimental. " +
                    "It has not been clinically validated for phone photos or all skin tones, " +
                    "and must not be used to delay professional care.",
            )
        }
    }
}

@Composable
private fun StatusHeader(status: ModelStatus) {
    val (icon, tint) = when (status) {
        ModelStatus.Ready -> Icons.Filled.CheckCircle to SunnyColors.Success
        is ModelStatus.Failed, ModelStatus.NotConfigured -> Icons.Filled.ErrorOutline to SunnyColors.Review
        else -> Icons.Filled.CloudDownload to SunnyColors.Orange
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(8.dp))
    val label = when (status) {
        ModelStatus.Ready -> "Model installed"
        is ModelStatus.Downloading -> "Downloading model"
        ModelStatus.Verifying -> "Verifying"
        ModelStatus.NotConfigured -> "Not configured"
        is ModelStatus.Failed -> "Download failed"
        ModelStatus.Idle -> "Model required"
    }
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun NotConfiguredBody() {
    Text(
        "No download source is configured. Scanning is disabled until the real weights " +
            "and native runtime are installed. If the weights already exist locally, " +
            "push them onto this device with " +
            "scripts/push_weights_to_device.sh (adb), then reopen this screen.",
        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WifiNote() {
    Row(text = "About 6 GB — use Wi-Fi. The download resumes if interrupted.")
}

@Composable
private fun Row(text: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Wifi, null, tint = SunnyColors.TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
    }
}

private fun gib(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
