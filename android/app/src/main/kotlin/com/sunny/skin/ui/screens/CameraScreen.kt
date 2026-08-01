package com.sunny.skin.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.sunny.skin.data.crypto.EncryptedImage
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.CenteredIconLabel
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.util.BitmapLoader
import com.sunny.skin.util.AlignmentResult
import com.sunny.skin.util.ImageAlignment
import com.sunny.skin.util.PhotoQuality
import com.sunny.skin.util.PhotoQualityIssue
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    val lifecycleOwner = LocalLifecycleOwner.current
    CameraSystemBarsEffect()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) permissionDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (permissionDenied) {
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Camera access is needed",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Allow camera access to take a photo. You can still choose an existing photo from your library.",
                        color = Color(0xFFD7D7D7),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Try again") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Open app settings", color = Color.White) }
                    TextButton(onClick = onClose) { Text("Not now", color = Color(0xFFD7D7D7)) }
                }
            }
        }
        return
    }
    CameraContent(vm = vm, onCaptured = onCaptured, onClose = onClose)
}

@Composable
private fun CameraContent(vm: SunnyViewModel, onCaptured: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val preset by vm.capturePreset.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var capturing by remember { mutableStateOf(false) }
    var previewIssue by remember { mutableStateOf<PhotoQualityIssue?>(null) }
    var previewAlignment by remember { mutableStateOf<AlignmentResult?>(null) }
    var referenceGrid by remember(capture.referenceImagePath) { mutableStateOf<FloatArray?>(null) }
    var previouslyMatched by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var focusTarget by remember { mutableStateOf<FocusTarget?>(null) }
    var focusRequestId by remember { mutableIntStateOf(0) }
    var captureFlashVisible by remember { mutableStateOf(false) }
    var showReference by remember(capture.referenceImagePath) {
        mutableStateOf(capture.referenceImagePath != null)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    LaunchedEffect(capture.referenceImagePath) {
        referenceGrid = capture.referenceImagePath?.let { ImageAlignment.loadReferenceGrid(context, it) }
        previewAlignment = null
    }

    val framingMatched = capture.referenceImagePath != null && previewAlignment?.framingReady == true
    LaunchedEffect(framingMatched) {
        if (framingMatched && !previouslyMatched) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previouslyMatched = framingMatched
    }

    LaunchedEffect(zoom, cameraControl) {
        cameraControl?.setZoomRatio(zoom)
    }

    LaunchedEffect(captureFlashVisible) {
        if (captureFlashVisible) {
            delay(100)
            captureFlashVisible = false
        }
    }

    DisposableEffect(imageAnalysis, referenceGrid) {
        var lastCheckedAt = 0L
        imageAnalysis.setAnalyzer(executor) { image ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastCheckedAt >= 250L) {
                lastCheckedAt = now
                val issue = assessPreview(image)
                val alignment = referenceGrid?.let { ImageAlignment.matchPreview(it, image) }
                ContextCompat.getMainExecutor(context).execute {
                    previewIssue = issue
                    previewAlignment = alignment
                    previewReady = true
                }
            }
            image.close()
        }
        onDispose {
            imageAnalysis.clearAnalyzer()
        }
    }

    DisposableEffect(executor) {
        onDispose {
            imageAnalysis.clearAnalyzer()
            executor.shutdown()
        }
    }


    DisposableEffect(previewView, lensFacing, lifecycleOwner, imageCapture, imageAnalysis) {
        val view = previewView ?: return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner, selector, preview, imageCapture, imageAnalysis,
            )
            cameraControl = camera.cameraControl
            camera.cameraControl.setZoomRatio(zoom)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            cameraControl = null
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                    setOnTouchListener { _, event ->
                        if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
                        performClick()
                        focusRequestId += 1
                        focusTarget = FocusTarget(
                            point = Offset(event.x, event.y),
                            requestId = focusRequestId,
                        )
                        val point = meteringPointFactory.createPoint(event.x, event.y)
                        cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build(),
                        )
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (captureFlashVisible) {
            // Flash the preview only; the framing guide and controls stay legible above it.
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.16f)))
        }

        capture.referenceImagePath?.let { path ->
            if (showReference) {
                AsyncImage(
                    model = EncryptedImage(path),
                    contentDescription = "Previous photo alignment guide",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.38f),
                )
            }
            RoundIconButton(
                icon = Icons.Filled.Layers,
                desc = if (showReference) "Hide previous photo" else "Show previous photo",
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                selected = showReference,
                onClick = { showReference = !showReference },
            )
        }

        val rawGuidance = cameraGuidancePresentation(
            ready = previewReady,
            issue = previewIssue,
            alignment = previewAlignment,
            recheck = capture.referenceImagePath != null,
        )
        var presentedGuidance by remember(capture.referenceImagePath) {
            mutableStateOf(CameraGuidancePresentation.Checking)
        }
        LaunchedEffect(rawGuidance) {
            delay(250)
            presentedGuidance = rawGuidance
        }
        val visibleGuidance = captureError?.let {
            CameraGuidancePresentation(message = it, acceptable = false, matched = false)
        } ?: presentedGuidance
        val guideBorderColor by animateColorAsState(
            targetValue = if (visibleGuidance.acceptable) Color(0xFF7FE09A) else Color.White,
            animationSpec = tween(
                durationMillis = SunnyMotion.StateMillis,
                easing = SunnyMotion.EaseOut,
            ),
            label = "Camera guide border",
        )

        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(0.72f).aspectRatio(1f)
                .border(
                    width = 1.5.dp,
                    color = guideBorderColor,
                    shape = RoundedCornerShape(20.dp),
                ),
        )

        focusTarget?.let { target ->
            FocusReticle(
                target = target,
                onFinished = {
                    if (focusTarget?.requestId == target.requestId) focusTarget = null
                },
            )
        }

        // Close (top-left)
        RoundIconButton(
            icon = Icons.Filled.Close, desc = "Close",
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            onClick = onClose,
        )

        // Pose-guidance banner for the guided full-body flow (keeps framing
        // consistent between visits, which is what the alignment relies on).
        preset?.let { p ->
            Column(
                Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(
                        top = 20.dp,
                        start = 76.dp,
                        end = if (capture.referenceImagePath != null) 76.dp else 16.dp,
                    )
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

        CaptureGuidance(
            presentation = visibleGuidance,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp).padding(bottom = 158.dp),
        )

        // Bottom controls: zoom row + shutter + flip
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black).navigationBarsPadding()
                .padding(bottom = 16.dp, top = 20.dp),
        ) {
            ZoomBar(
                current = zoom,
                onSelect = { z -> zoom = z },
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
                    captureError = null
                    captureFlashVisible = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = image.toUprightBitmap()
                                image.close()
                                val scaled = BitmapLoader.downscale(bmp)
                                if (scaled !== bmp) bmp.recycle()
                                ContextCompat.getMainExecutor(context).execute {
                                    vm.startCapture(scaled, previewAlignment)
                                    onCaptured()
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                ContextCompat.getMainExecutor(context).execute {
                                    capturing = false
                                    captureError = "Photo could not be captured. Try again."
                                }
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

/** Let the camera own the whole display while keeping the rest of Sunny's light system bars. */
@Composable
private fun CameraSystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val activity = view.context as? ComponentActivity
            ?: return@DisposableEffect onDispose { }
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )

        onDispose {
            val background = SunnyColors.Background.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(background, background),
                navigationBarStyle = SystemBarStyle.light(background, background),
            )
        }
    }
}

@Composable
private fun ZoomBar(current: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val levels = listOf(
        Triple(0.5f, ".5", "0.5 times"),
        Triple(1f, "1×", "1 time"),
        Triple(2f, "2", "2 times"),
        Triple(4f, "4", "4 times"),
        Triple(8f, "8", "8 times"),
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        levels.forEach { (value, label, description) ->
            val selected = current == value
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.88f,
                animationSpec = if (motionEnabled) {
                    tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)
                } else {
                    snap()
                },
                label = "Zoom selection",
            )
            Box(
                Modifier.size(48.dp)
                    .semantics {
                        contentDescription = "Zoom $description"
                        stateDescription = if (selected) "Selected" else "Not selected"
                        role = Role.RadioButton
                    }
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(CircleShape)
                        .background(if (selected) Color(0x66000000) else Color(0x33000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color(0xFFFFC24B) else Color.White,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberSunnyMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.95f else 1f,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.PressMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Shutter press",
    )
    val shutterAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.58f,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "Shutter availability",
    )
    Box(
        Modifier.size(76.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = shutterAlpha
        }.clip(CircleShape).background(Color.White)
            .border(4.dp, Color(0x55FFFFFF), CircleShape)
            .semantics {
                contentDescription = if (enabled) "Take photo" else "Taking photo"
                stateDescription = if (enabled) "Ready" else "Capturing"
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!enabled) Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .background(if (selected) SunnyColors.Action.copy(alpha = 0.92f) else Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun CaptureGuidance(
    presentation: CameraGuidancePresentation,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = presentation,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "Camera guidance",
        modifier = modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC000000))
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = presentation.message
            },
    ) { current ->
        CenteredIconLabel(
            icon = if (current.acceptable && current.matched) Icons.Filled.CenterFocusStrong
                else if (current.acceptable) Icons.Filled.WbSunny else Icons.Filled.WarningAmber,
            text = current.message,
            iconTint = if (current.acceptable) Color(0xFF7FE09A) else Color(0xFFFFC24B),
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp),
            iconSize = 18.dp,
            maxLines = 2,
        )
    }
}

private data class CameraGuidancePresentation(
    val message: String,
    val acceptable: Boolean,
    val matched: Boolean,
) {
    companion object {
        val Checking = CameraGuidancePresentation(
            message = "Checking light and detail…",
            acceptable = false,
            matched = false,
        )
    }
}

private fun cameraGuidancePresentation(
    ready: Boolean,
    issue: PhotoQualityIssue?,
    alignment: AlignmentResult?,
    recheck: Boolean,
): CameraGuidancePresentation {
    val message = when {
        !ready -> "Checking light and detail…"
        issue == PhotoQualityIssue.TOO_DARK -> "Add more even light"
        issue == PhotoQualityIssue.TOO_BRIGHT -> "Reduce glare or direct flash"
        issue == PhotoQualityIssue.LOW_CONTRAST -> "Refocus and hold the phone steady"
        issue == PhotoQualityIssue.TOO_SMALL -> "Move closer so the spot fills the guide"
        recheck -> framingGuidance(alignment)
        else -> "Exposure and detail look good"
    }
    val acceptable = ready && issue == null && (!recheck || alignment?.framingReady == true)
    return CameraGuidancePresentation(
        message = message,
        acceptable = acceptable,
        matched = acceptable && recheck,
    )
}

private data class FocusTarget(val point: Offset, val requestId: Int)

@Composable
private fun FocusReticle(target: FocusTarget, onFinished: () -> Unit) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val scale = remember(target.requestId) { Animatable(if (motionEnabled) 1.15f else 1f) }
    val alpha = remember(target.requestId) { Animatable(0.4f) }
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(target.requestId, motionEnabled) {
        val scaleJob = if (motionEnabled) {
            launch {
                scale.animateTo(
                    1f,
                    tween(160, easing = SunnyMotion.EaseOut),
                )
            }
        } else {
            null
        }
        alpha.animateTo(1f, tween(160, easing = SunnyMotion.EaseOut))
        scaleJob?.join()
        delay(600)
        alpha.animateTo(0f, tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut))
        latestOnFinished()
    }

    Box(
        Modifier.size(48.dp).graphicsLayer {
            translationX = target.point.x - 24.dp.toPx()
            translationY = target.point.y - 24.dp.toPx()
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }.border(2.dp, Color.White, RoundedCornerShape(8.dp)),
    )
}

private fun framingGuidance(result: AlignmentResult?): String {
    result ?: return "Match the previous photo in the guide"
    if (!result.isUsable) return "Center the same area in the guide"
    val transform = result.transform
    return when {
        abs(transform.scale - 1f) > 0.13f ->
            if (transform.scale > 1f) "Move closer to match the distance"
            else "Move farther away to match the distance"
        abs(transform.rotationDeg) > 6f -> "Rotate the phone to match the angle"
        sqrt(transform.tx * transform.tx + transform.ty * transform.ty) > 0.08f ->
            "Shift the phone to center the same area"
        result.framingReady -> "Framing matched"
        else -> "Fine-tune the distance and angle"
    }
}

/** Samples only the luminance plane; preview frames are never retained or sent anywhere. */
private fun assessPreview(image: ImageProxy): PhotoQualityIssue? {
    val plane = image.planes.firstOrNull() ?: return PhotoQualityIssue.LOW_CONTRAST
    val buffer = plane.buffer
    val xStep = max(1, image.width / 24)
    val yStep = max(1, image.height / 24)
    val samples = ArrayList<Int>(24 * 24)
    var y = 0
    while (y < image.height) {
        var x = 0
        while (x < image.width) {
            val index = y * plane.rowStride + x * plane.pixelStride
            if (index < buffer.limit()) samples += buffer.get(index).toInt() and 0xff
            x += xStep
        }
        y += yStep
    }
    return PhotoQuality.assessLuma(samples)
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
