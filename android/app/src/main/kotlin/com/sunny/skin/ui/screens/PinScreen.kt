package com.sunny.skin.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors

private const val PIN_LENGTH = 4

/**
 * PIN gate. Two modes:
 *  - [verify] == null  -> CREATE: enter a new PIN, then confirm it.
 *  - [verify] != null  -> UNLOCK: [verify] checks the entry against the stored
 *    salted hash (returning true on match) and internally rate-limits attempts;
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

    fun lockMsg(): String {
        val secs = (lockoutRemainingMs() / 1000) + 1
        return "Too many attempts. Try again in ${secs}s."
    }

    fun submit(complete: String) {
        when {
            verify != null -> {
                if (verify(complete)) onSuccess(complete)
                else { error = if (lockoutRemainingMs() > 0) lockMsg() else "Incorrect PIN. Try again."; entry = "" }
            }
            firstEntry == null -> { firstEntry = complete; entry = ""; error = null }
            complete == firstEntry -> onSuccess(complete)
            else -> { error = "PINs didn't match. Start over."; firstEntry = null; entry = "" }
        }
    }

    fun onKey(d: Char) {
        if (verify != null && lockoutRemainingMs() > 0) { error = lockMsg(); return }
        if (entry.length >= PIN_LENGTH) return
        error = null
        entry += d
        if (entry.length == PIN_LENGTH) submit(entry)
    }

    fun onDelete() {
        if (entry.isNotEmpty()) entry = entry.dropLast(1)
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
                        .background(SunnyColors.Surface).clickable(onClick = onCancel),
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
        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < entry.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) SunnyColors.Orange else SunnyColors.SurfaceMuted),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            error ?: " ",
            color = SunnyColors.Danger,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(12.dp))
        Keypad(onKey = ::onKey, onDelete = ::onDelete)
    }
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
