package com.sunny.skin.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors

/**
 * First-run onboarding: a single, calm "liquid glass" page that mirrors the lock
 * screen — a blurred, frosted rendering of the app behind a translucent scrim,
 * with the mascot, a warm benefit-led promise, and a single Get Started button.
 * The honest "describes, doesn't diagnose" line is present but framed as helpful,
 * not as a warning; the detailed limitations live on the Privacy screen.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        // Frosted backdrop — a blurred silhouette of the Overview (API 31+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlassBackdrop(Modifier.fillMaxSize().blur(34.dp))
        }
        // Translucent scrim so the text stays crisp while the colour blooms through.
        Box(Modifier.fillMaxSize().background(SunnyColors.Background.copy(alpha = 0.42f)))

        Column(
            Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.sunny_mascot),
                    contentDescription = "Sunny mascot",
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Welcome to Sunny",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Track your skin over time and spot what changes.",
                    style = MaterialTheme.typography.bodyLarge, color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                val lockId = "lock"
                val privacyText = buildAnnotatedString {
                    appendInlineContent(lockId, "[lock]")
                    append("  ")
                    append(com.sunny.skin.AppMode.photoPrivacyLine)
                }
                val inlineLock = mapOf(
                    lockId to InlineTextContent(
                        Placeholder(1.05.em, 1.05.em, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            tint = SunnyColors.Orange,
                        )
                    },
                )
                Text(
                    privacyText,
                    inlineContent = inlineLock,
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Sunny describes, it doesn't diagnose — see a professional for any concern.",
                    style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))

            Surface(
                Modifier.fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = onFinish),
                shape = RoundedCornerShape(28.dp),
                color = SunnyColors.Orange,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Get Started", style = MaterialTheme.typography.titleMedium,
                        color = SunnyColors.Surface, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * A static, decorative stand-in for the Overview — soft cards, the body
 * silhouette and the mascot chip — drawn only to be blurred behind the glass.
 */
@Composable
private fun GlassBackdrop(modifier: Modifier) {
    val card = SunnyColors.Surface
    val muted = SunnyColors.SurfaceMuted
    val grey = Color(0xFFC9CBD3)
    Column(modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(muted))
        Spacer(Modifier.size(24.dp))
        Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp, 320.dp).clip(RoundedCornerShape(60.dp)).background(grey))
        }
        Spacer(Modifier.size(20.dp))
        val accents = listOf(
            SunnyColors.Orange, Color(0xFF34C759), Color(0xFFF5A623),
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
        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(card),
            contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(16.dp).size(90.dp, 12.dp).clip(RoundedCornerShape(6.dp))
                .background(SunnyColors.Orange))
        }
    }
}
