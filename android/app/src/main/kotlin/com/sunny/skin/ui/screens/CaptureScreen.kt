package com.sunny.skin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.sunny.skin.ui.i18n.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.R
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
    val libraryError = stringResource(R.string.capture_library_error)
    var pickerError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.beginNewCapture() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bitmap = BitmapLoader.fromUri(context, uri)
            if (bitmap == null) {
                pickerError = libraryError
            } else {
                pickerError = null
                vm.startCapture(bitmap)
                onImageChosen()
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.capture_title), onBack = onBack) { inner ->
        Column(Modifier.fillMaxWidth().padding(inner).padding(16.dp)) {
            Text(
                stringResource(R.string.capture_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
            pickerError?.let { message ->
                Spacer(Modifier.size(12.dp))
                SunnyCard {
                    Text(
                        message,
                        color = SunnyColors.Danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            CaptureActionCard(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.capture_take_photo),
                subtitle = stringResource(R.string.capture_take_photo_support),
                onClick = onOpenCamera,
            )
            Spacer(Modifier.size(12.dp))
            CaptureActionCard(
                icon = Icons.Filled.PhotoLibrary,
                title = stringResource(R.string.capture_choose_library),
                subtitle = stringResource(R.string.capture_choose_library_support),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            Spacer(Modifier.size(12.dp))
            GuidedCard(onClick = onGuided)
        }
    }
}

/** Full-width entry into the guided head-to-toe capture flow. */
@Composable
private fun GuidedCard(onClick: () -> Unit) {
    SunnyCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(SunnyColors.OrangeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Accessibility,
                    contentDescription = null,
                    tint = SunnyColors.OrangeText,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.capture_full_body), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.capture_full_body_support),
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary)
        }
    }
}

@Composable
private fun CaptureActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SunnyCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SunnyColors.OrangeText,
                    modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = SunnyColors.TextTertiary)
        }
    }
}
