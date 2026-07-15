package com.sunny.skin.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors

/**
 * "Sunny is Locked" landing screen. A blurred, frosted-glass rendering of the
 * app sits behind a translucent scrim (the "liquid glass" look); tapping the
 * button reveals the PIN keypad (handled by the caller).
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        // Frosted backdrop — a blurred silhouette of the Overview. Blur needs
        // RenderEffect (API 31+); on older devices we simply show the canvas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LockedBackdrop(Modifier.fillMaxSize().blur(34.dp))
        }
        // Translucent scrim over the blur, so the lock text stays crisp/readable
        // while the colourful frosted shapes still read through.
        Box(Modifier.fillMaxSize().background(SunnyColors.Background.copy(alpha = 0.42f)))

        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.sunny_mascot),
                    contentDescription = "Sunny mascot",
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text("Sunny is Locked", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Your health data is protected.\nAuthenticate to continue.",
                    style = MaterialTheme.typography.bodyLarge, color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center)
            }

            Surface(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(28.dp)).clickable(onClick = onUnlock),
                shape = RoundedCornerShape(28.dp), color = SunnyColors.OrangeText,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = SunnyColors.Surface,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Unlock with PIN", style = MaterialTheme.typography.titleMedium,
                            color = SunnyColors.Surface, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * A static, decorative stand-in for the Overview screen — soft cards, the body
 * silhouette and the mascot chip — drawn only to be blurred behind the lock.
 * No data, no interaction.
 */
@Composable
private fun LockedBackdrop(modifier: Modifier) {
    val card = SunnyColors.Surface
    val muted = SunnyColors.SurfaceMuted
    val grey = SunnyColors.SwitchOffTrack
    Column(modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Header: mascot chip + title bars
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(SunnyColors.OrangeSoft))
            Spacer(Modifier.size(12.dp))
            Column {
                Box(Modifier.size(120.dp, 18.dp).clip(RoundedCornerShape(6.dp)).background(muted))
                Spacer(Modifier.size(6.dp))
                Box(Modifier.size(160.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(muted))
            }
        }
        Spacer(Modifier.size(16.dp))
        // Toggle pill
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(muted))
        Spacer(Modifier.size(24.dp))
        // Body silhouette blob
        Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp, 340.dp).clip(RoundedCornerShape(60.dp)).background(grey))
        }
        Spacer(Modifier.size(20.dp))
        // Stat cards, each with a soft coloured accent (camera/check/clock) that
        // blooms into a colourful blob once blurred.
        val accents = listOf(
            SunnyColors.Orange,
            SunnyColors.Success,
            SunnyColors.Review,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            accents.forEach { accent ->
                Box(
                    Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(20.dp)).background(card),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(accent))
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        // Coverage bar with an orange fill segment.
        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(card),
            contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(16.dp).size(90.dp, 12.dp).clip(RoundedCornerShape(6.dp))
                .background(SunnyColors.Orange))
        }
    }
}
