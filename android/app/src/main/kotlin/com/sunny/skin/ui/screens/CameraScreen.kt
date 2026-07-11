package com.sunny.skin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.util.BitmapLoader
import java.util.concurrent.Executors

/**
 * Full-screen camera capture (design: iOS-style camera with zoom levels and a
 * shutter). Uses CameraX; on shutter it converts the frame to a downscaled
 * bitmap, starts analysis, and advances to Review.
 */
@Composable
fun CameraScreen(
    vm: SunnyViewModel,
    onCaptured: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted; if (!granted) onClose() }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {}
        return
    }
    CameraContent(vm = vm, onCaptured = onCaptured, onClose = onClose)
}

@Composable
private fun CameraContent(vm: SunnyViewModel, onCaptured: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val preset by vm.capturePreset.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var capturing by remember { mutableStateOf(false) }
    val cameraControl = remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner, selector, preview, imageCapture,
                    )
                    cameraControl.value = camera.cameraControl
                    camera.cameraControl.setZoomRatio(zoom)
                }, ContextCompat.getMainExecutor(context))
            },
        )

        // Close (top-left)
        RoundIconButton(
            icon = Icons.Filled.Close, desc = "Close",
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            onClick = onClose,
        )

        // Pose-guidance banner for the guided full-body flow (keeps framing
        // consistent between visits, which is what the alignment relies on).
        preset?.let { p ->
            Column(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 76.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.bodyPart.label, color = Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(p.poseHint, color = Color(0xFFEDEDED),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }

        // Bottom controls: zoom row + shutter + flip
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black).padding(bottom = 40.dp, top = 20.dp),
        ) {
            ZoomBar(
                current = zoom,
                onSelect = { z -> zoom = z; cameraControl.value?.setZoomRatio(z) },
                modifier = Modifier.align(Alignment.TopCenter).padding(bottom = 16.dp),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(top = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Spacer(Modifier.size(56.dp))
                ShutterButton(enabled = !capturing) {
                    if (capturing) return@ShutterButton
                    capturing = true
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = image.toUprightBitmap()
                                image.close()
                                val scaled = BitmapLoader.downscale(bmp)
                                ContextCompat.getMainExecutor(context).execute {
                                    vm.startCapture(scaled)
                                    onCaptured()
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                capturing = false
                            }
                        },
                    )
                }
                RoundIconButton(
                    icon = Icons.Filled.Cameraswitch, desc = "Flip camera",
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoomBar(current: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    val levels = listOf(0.5f to ".5", 1f to "1×", 2f to "2", 4f to "4", 8f to "8")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        levels.forEach { (value, label) ->
            val selected = current == value
            Box(
                Modifier.size(if (selected) 34.dp else 30.dp).clip(CircleShape)
                    .background(if (selected) Color(0x66000000) else Color(0x33000000))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) Color(0xFFFFC24B) else Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(Color.White)
            .border(4.dp, Color(0x55FFFFFF), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Color(0x55000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** Convert an ImageProxy (JPEG) to a Bitmap, honouring rotation metadata. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val raw = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
