package com.sunny.skin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_LENGTH = 4

/**
 * PIN gate. Two modes:
 *  - [verify] == null  -> CREATE: enter a new PIN, then confirm it.
 *  - [verify] != null  -> UNLOCK: [verify] checks the entry against the stored
 *    salted verifier and wrapped data key, and internally rate-limits attempts;
 *    [lockoutRemainingMs] reports any active lockout so input is blocked.
 *
 * On success [onSuccess] is called with the final PIN (the newly created PIN in
 * CREATE mode, or the entered PIN in UNLOCK mode). Optional [onCancel] shows a
 * back affordance (used when creating a PIN from Settings).
 */
@Composable
fun PinScreen(
    verify: ((String) -> Boolean)?,
    onSuccess: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    changeMode: Boolean = false,
    lockoutRemainingMs: () -> Long = { 0L },
) {
    val creating = verify == null
    var firstEntry by remember { mutableStateOf<String?>(null) } // CREATE: first pass
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var errorFeedbackKey by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var lockoutRemaining by remember { mutableLongStateOf(lockoutRemainingMs().coerceAtLeast(0L)) }
    var lockoutWindow by remember { mutableLongStateOf(lockoutRemaining.coerceAtLeast(1L)) }
    val motionEnabled = rememberSunnyMotionEnabled()
    val scope = rememberCoroutineScope()

    val title = when {
        !creating -> "Enter your PIN"
        changeMode && firstEntry == null -> "Change PIN"
        firstEntry == null -> "Create a PIN"
        changeMode -> "Confirm new PIN"
        else -> "Confirm your PIN"
    }
    val subtitle = when {
        !creating -> "Enter your 4-digit PIN to unlock Sunny."
        changeMode && firstEntry == null -> "Enter a new 4-digit PIN."
        firstEntry == null -> "Choose a 4-digit PIN to protect your skin health data."
        else -> "Re-enter your PIN to confirm."
    }

    fun lockMsg(remainingMs: Long = lockoutRemainingMs()): String {
        val secs = ((remainingMs.coerceAtLeast(0L) + 999L) / 1_000L).coerceAtLeast(1L)
        return "Too many attempts. Try again in ${secs}s."
    }

    fun updateLockout(remainingMs: Long) {
        lockoutRemaining = remainingMs.coerceAtLeast(0L)
        if (lockoutRemaining > 0L) {
            lockoutWindow = maxOf(lockoutWindow, lockoutRemaining)
            error = lockMsg(lockoutRemaining)
        }
    }

    fun showFailure(message: String, remainingMs: Long = 0L) {
        error = message
        errorFeedbackKey++
        if (remainingMs > 0L) updateLockout(remainingMs)
    }

    fun completeSuccessfully(complete: String) {
        completing = true
        error = null
        scope.launch {
            // Verification has already completed; this short pause only lets the
            // accepted state become perceptible before the gate is removed.
            delay(SunnyMotion.StateMillis.toLong())
            onSuccess(complete)
        }
    }

    fun submit(complete: String) {
        when {
            verify != null -> {
                submitting = true
                scope.launch {
                    // Commit the fourth dot to a frame before starting CPU-heavy
                    // key derivation, so touch feedback is never swallowed.
                    withFrameNanos { }
                    val accepted = withContext(Dispatchers.Default) { verify(complete) }
                    submitting = false
                    if (accepted) {
                        completeSuccessfully(complete)
                    } else {
                        val remaining = lockoutRemainingMs().coerceAtLeast(0L)
                        showFailure(
                            if (remaining > 0L) lockMsg(remaining) else "Incorrect PIN. Try again.",
                            remaining,
                        )
                        entry = ""
                    }
                }
            }
            else -> {
                submitting = true
                scope.launch {
                    withFrameNanos { }
                    submitting = false
                    if (firstEntry == null) {
                        firstEntry = complete
                        entry = ""
                        error = null
                    } else if (complete == firstEntry) {
                        completeSuccessfully(complete)
                    } else {
                        showFailure("PINs didn't match. Start over.")
                        firstEntry = null
                        entry = ""
                    }
                }
            }
        }
    }

    fun onKey(d: Char) {
        if (submitting || completing) return
        val remaining = if (verify != null) lockoutRemainingMs().coerceAtLeast(0L) else 0L
        if (remaining > 0L) {
            updateLockout(remaining)
            return
        }
        if (entry.length >= PIN_LENGTH) return
        error = null
        lockoutRemaining = 0L
        entry += d
        if (entry.length == PIN_LENGTH) submit(entry)
    }

    fun onDelete() {
        if (!submitting && !completing && entry.isNotEmpty()) entry = entry.dropLast(1)
    }

    LaunchedEffect(lockoutRemaining > 0L) {
        while (lockoutRemaining > 0L) {
            // One state update per second keeps the determinate ring calm and
            // avoids turning a security countdown into a continuous animation.
            delay(1_000L)
            val remaining = lockoutRemainingMs().coerceAtLeast(0L)
            lockoutRemaining = remaining
            if (remaining > 0L) {
                error = lockMsg(remaining)
            } else if (error?.startsWith("Too many attempts") == true) {
                error = null
            }
        }
    }
    BackHandler(enabled = submitting || completing) {
        // Keep verification and its concise success feedback atomic.
    }

    Column(
        Modifier.fillMaxSize().background(SunnyColors.Background)
            .statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onCancel != null) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape)
                        .background(SunnyColors.Surface)
                        .clickable(enabled = !submitting && !completing, onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back",
                        tint = SunnyColors.TextPrimary, modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Image(
            painterResource(R.drawable.sunny_mascot),
            contentDescription = "Sunny mascot",
            modifier = Modifier.size(84.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SunnyColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))
        PinFeedback(
            enteredDigits = entry.length,
            errorFeedbackKey = errorFeedbackKey,
            success = completing,
            motionEnabled = motionEnabled,
        )
        Spacer(Modifier.height(16.dp))
        if (lockoutRemaining > 0L) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    progress = {
                        (lockoutRemaining.toFloat() / lockoutWindow.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.size(20.dp),
                    color = SunnyColors.Danger,
                    trackColor = SunnyColors.SurfaceMuted,
                    strokeWidth = 2.dp,
                )
                Text(
                    error ?: lockMsg(lockoutRemaining),
                    color = SunnyColors.Danger,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                error ?: " ",
                color = SunnyColors.Danger,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(12.dp))
        Keypad(onKey = ::onKey, onDelete = ::onDelete)
    }
}

@Composable
private fun PinFeedback(
    enteredDigits: Int,
    errorFeedbackKey: Int,
    success: Boolean,
    motionEnabled: Boolean,
) {
    val shake = remember { Animatable(0f) }
    var errorFlash by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dotsAlpha by animateFloatAsState(
        targetValue = if (success) 0f else 1f,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "PIN dots fade",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (success) 1f else 0f,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "PIN success",
    )

    LaunchedEffect(errorFeedbackKey, motionEnabled) {
        if (errorFeedbackKey == 0) {
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        errorFlash = true
        shake.snapTo(0f)
        if (motionEnabled) {
            shake.animateTo(-6f, tween(55))
            shake.animateTo(6f, tween(55))
            shake.animateTo(-3f, tween(55))
            shake.animateTo(0f, tween(55))
        } else {
            delay(220)
        }
        errorFlash = false
    }

    Box(
        Modifier
            .height(24.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationX = with(density) { shake.value.dp.toPx() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.graphicsLayer { alpha = dotsAlpha },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(PIN_LENGTH) { index ->
                PinDot(
                    filled = index < enteredDigits || errorFlash,
                    error = errorFlash,
                    motionEnabled = motionEnabled,
                )
            }
        }
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = if (success) "PIN accepted" else null,
            tint = SunnyColors.Success,
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = checkAlpha },
        )
    }
}

@Composable
private fun PinDot(filled: Boolean, error: Boolean, motionEnabled: Boolean) {
    val color by animateColorAsState(
        targetValue = when {
            error -> SunnyColors.Danger
            filled -> SunnyColors.Orange
            else -> SunnyColors.SurfaceMuted
        },
        animationSpec = tween(
            durationMillis = if (motionEnabled) 120 else 0,
            easing = SunnyMotion.EaseOut,
        ),
        label = "PIN dot color",
    )
    val alpha by animateFloatAsState(
        targetValue = if (filled) 1f else 0.62f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) 120 else 0,
            easing = SunnyMotion.EaseOut,
        ),
        label = "PIN dot opacity",
    )
    Box(
        Modifier
            .size(16.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "⌫" -> KeyButton(label = "⌫", onClick = onDelete, filled = false)
                        else -> KeyButton(label = key, onClick = { onKey(key[0]) }, filled = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, filled: Boolean) {
    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .background(if (filled) SunnyColors.Surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (label == "⌫") 24.sp else 28.sp,
            fontWeight = FontWeight.Medium, color = SunnyColors.TextPrimary)
    }
}
