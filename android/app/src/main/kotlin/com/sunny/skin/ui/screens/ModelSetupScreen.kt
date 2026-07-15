package com.sunny.skin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.setValue
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // Which engine is selected right now (beta toggle). On-device needs the model
    // downloaded; server mode does not — this drives which action we offer.
    val serverSelected = com.sunny.skin.AppMode.serverActive(context)
    // Opt-in to metered (mobile-data) download; default off so Wi-Fi stays the norm.
    var allowMetered by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // Pick up weights that were adb-pushed onto the device while the app was open.
    androidx.compose.runtime.LaunchedEffect(Unit) { ModelDownloadManager.refresh() }

    ScreenScaffold(title = "AI Model", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            StatusHeader(status, serverSelected)
            Spacer(Modifier.height(20.dp))

            SunnyCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Sunny-Gemma4-E4B", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(com.sunny.skin.AppMode.modelSummary(serverSelected),
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
                    if (serverSelected) {
                        Text(
                            "No model download is needed while Analysis source is set to Beta " +
                                "server. Switch it to On-device in Settings to run the model on " +
                                "this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                com.sunny.skin.inference.ModelProvider.reset()
                                ModelDownloadManager.refresh()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Orange),
                        ) { Text("Retry server", fontWeight = FontWeight.SemiBold) }
                    } else {
                        WifiNote()
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = allowMetered,
                                role = androidx.compose.ui.semantics.Role.Checkbox,
                                onValueChange = { allowMetered = it },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(checked = allowMetered, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Download over mobile data (uses ~6 GB of your plan)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SunnyColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { ModelDownloadManager.start(allowMetered = allowMetered) },
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
                ModelStatus.Ready -> Text(com.sunny.skin.AppMode.modelReadyNote(serverSelected),
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
            DisclaimerCard(
                title = "About the AI",
                body = com.sunny.skin.AppMode.aiDescription(serverSelected),
            )
        }
    }
}

@Composable
private fun StatusHeader(status: ModelStatus, serverSelected: Boolean) {
    val (icon, tint) = when (status) {
        ModelStatus.Ready -> Icons.Filled.CheckCircle to SunnyColors.Success
        is ModelStatus.Failed, ModelStatus.NotConfigured -> Icons.Filled.ErrorOutline to SunnyColors.Review
        else -> Icons.Filled.CloudDownload to SunnyColors.Orange
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(8.dp))
    val label = when (status) {
        ModelStatus.Ready -> if (serverSelected) "Server mode configured" else "Model installed"
        is ModelStatus.Downloading -> "Downloading model"
        ModelStatus.Verifying -> "Verifying"
        ModelStatus.NotConfigured -> "Not configured"
        is ModelStatus.Failed -> if (serverSelected) "Server unavailable" else "Download failed"
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
    Row(text = "About 6 GB. Wi-Fi recommended; tick below to use mobile data. Resumes if interrupted.")
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
