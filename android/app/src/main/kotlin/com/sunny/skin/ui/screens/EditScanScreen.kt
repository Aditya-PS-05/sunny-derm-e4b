package com.sunny.skin.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.inference.DescribeResult
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.AnalysisCard
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Edit an existing scan: rename it, replace its photo, and/or re-run the
 * on-device diagnosis. Changes are only persisted when the user taps Save.
 */
@Composable
fun EditScanScreen(vm: SunnyViewModel, scanId: String, onDone: () -> Unit) {
    val scan by vm.scan(scanId).collectAsStateWithLifecycle(initialValue = null)
    val data = scan
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Local edit state, seeded from the latest observation once it loads.
    var name by remember { mutableStateOf("") }
    var newBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var modelVersion by remember { mutableStateOf("") }
    var rawOutput by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var redoing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // True once the current analysis reflects the newly-picked photo. When a new
    // photo has been chosen but not re-analysed, Save re-runs the model first so
    // the saved description can never belong to the old photo.
    var analysisMatchesNewPhoto by remember { mutableStateOf(false) }

    val latest = data?.timeline?.firstOrNull()
    LaunchedEffect(latest?.id) {
        if (!seeded && data != null && latest != null) {
            name = data.scan.name
            analysis = latest.analysis.toAnalysis()
            modelVersion = latest.modelVersion
            rawOutput = latest.rawOutput
            seeded = true
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            newBitmap = BitmapLoader.fromUri(context, uri)
            error = null
            analysisMatchesNewPhoto = false // description now belongs to the old photo
        }
    }

    fun redo() {
        val obs = latest ?: return
        redoing = true
        error = null
        scope.launch {
            val bmp = newBitmap ?: withContext(Dispatchers.IO) { BitmapLoader.fromFile(obs.imagePath) }
            if (bmp == null) { error = "Couldn't load the photo."; redoing = false; return@launch }
            when (val r = vm.runDescribe(bmp)) {
                is DescribeResult.Success -> {
                    analysis = r.analysis; modelVersion = r.modelVersion; rawOutput = r.rawOutput
                    if (newBitmap != null) analysisMatchesNewPhoto = true
                }
                DescribeResult.Unreadable -> error = "Couldn't read this image. Try a clearer photo."
            }
            redoing = false
        }
    }

    // Persist, re-analysing first if a new photo was chosen but never re-run,
    // so the stored photo and description always describe the same image.
    fun save() {
        val obs = latest ?: return
        val bmp = newBitmap
        if (bmp != null && !analysisMatchesNewPhoto) {
            redoing = true; error = null
            scope.launch {
                when (val r = vm.runDescribe(bmp)) {
                    is DescribeResult.Success -> vm.saveScanEdit(
                        scanId, obs, name, bmp, r.analysis, r.modelVersion, r.rawOutput, onDone,
                    )
                    DescribeResult.Unreadable -> {
                        error = "Couldn't read the new photo. Try a clearer one."; redoing = false
                    }
                }
            }
        } else {
            vm.saveScanEdit(scanId, obs, name, newBitmap, analysis!!, modelVersion, rawOutput, onDone)
        }
    }

    ScreenScaffold(
        title = "Edit Scan",
        onBack = onDone,
        trailing = {
            val canSave = data != null && latest != null && analysis != null && name.isNotBlank()
            Text(
                "Save",
                color = if (canSave) SunnyColors.Orange else SunnyColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = canSave && !redoing) { save() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
    ) { inner ->
        if (data == null || latest == null) return@ScreenScaffold

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(inner).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            // Name
            SectionLabel("Name")
            SunnyCard {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = SunnyColors.TextPrimary),
                    cursorBrush = SolidColor(SunnyColors.Orange),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            // Photo
            SectionLabel("Photo")
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(20.dp))
                    .background(SunnyColors.SurfaceMuted),
            ) {
                val bmp = newBitmap
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(model = File(latest.imagePath), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    Modifier.weight(1f), Icons.Filled.PhotoLibrary, "Change Photo",
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                ActionButton(
                    Modifier.weight(1f), Icons.Filled.Replay,
                    if (redoing) "Analysing…" else "Redo Diagnosis",
                    onClick = { if (!redoing) redo() },
                    loading = redoing,
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SunnyColors.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))

            // Current diagnosis preview
            SectionLabel("Diagnosis")
            analysis?.let { AnalysisCard(it) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = SunnyColors.TextTertiary, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(SunnyColors.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = SunnyColors.Orange,
                    strokeWidth = 2.dp)
            } else {
                Icon(icon, null, tint = SunnyColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
