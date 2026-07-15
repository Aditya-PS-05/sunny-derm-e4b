package com.sunny.skin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.BitmapLoader

/**
 * Capture chooser: "Take Photo" (live camera) or "Choose from Library"
 * (system photo picker). Picking an image kicks off analysis and moves to
 * the Review screen.
 */
@Composable
fun CaptureScreen(
    vm: SunnyViewModel,
    onOpenCamera: () -> Unit,
    onImageChosen: () -> Unit,
    onGuided: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.beginNewCapture() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bitmap = BitmapLoader.fromUri(context, uri)
            vm.startCapture(bitmap)
            onImageChosen()
        }
    }

    ScreenScaffold(title = "Capture", onBack = onBack) { inner ->
        Column(Modifier.fillMaxWidth().padding(inner).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChooserCard(
                    Modifier.weight(1f), Icons.Filled.CameraAlt, "Take Photo",
                    onClick = onOpenCamera,
                )
                ChooserCard(
                    Modifier.weight(1f), Icons.Filled.PhotoLibrary, "Choose from Library",
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            Spacer(Modifier.size(16.dp))
            GuidedCard(onClick = onGuided)
        }
    }
}

/** Full-width entry into the guided head-to-toe capture flow. */
@Composable
private fun GuidedCard(onClick: () -> Unit) {
    SunnyCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Accessibility, null, tint = SunnyColors.Orange,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Guided full-body scan", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("Capture head to toe with framing tips for each zone",
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary)
        }
    }
}

@Composable
private fun ChooserCard(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(SunnyColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = SunnyColors.TextPrimary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
