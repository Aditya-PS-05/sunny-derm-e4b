package com.sunny.skin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunny.skin.ui.theme.SunnyColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

private data class OnboardCard(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val tint: Color,
    val back: String,
)

private val CARDS = listOf(
    OnboardCard("📷", "Capture", "your skin", Color(0xFFDDE3E4),
        "Snap or choose a photo of any spot. Sunny describes what it sees — entirely on your device."),
    OnboardCard("📈", "Track", "over time", Color(0xFFE7E1D6),
        "Save scans to your body map and watch how a spot changes across weeks and months."),
    OnboardCard("🔒", "Private", "by design", Color(0xFFDCE6DE),
        "Your photos never leave your phone. Lock Sunny behind a PIN that only you know."),
)

/**
 * First-run onboarding: a deck of flippable flashcards (swipe between them, tap
 * to flip) introducing what Sunny does. The last card reveals "Get Started".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { CARDS.size })
    val scope = rememberCoroutineScope()
    val flipped = remember { mutableStateMapOf<Int, Boolean>() }
    val onLast = pager.currentPage == CARDS.lastIndex

    Column(
        Modifier.fillMaxSize().background(SunnyColors.Background)
            .statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: back chevron + page pill + skip
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp)) {
                if (pager.currentPage > 0) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(SunnyColors.Surface)
                            .clickable { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back",
                            tint = SunnyColors.TextPrimary, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(50), color = SunnyColors.Surface,
                    shadowElevation = 2.dp) {
                    Text("${pager.currentPage + 1} / ${CARDS.size}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = SunnyColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                }
            }
            Text("Skip", color = SunnyColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onFinish)
                    .padding(horizontal = 8.dp, vertical = 8.dp))
        }

        // Card deck
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val card = CARDS[page]
            // Parallax/scale relative to the current page.
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
            val scale = 1f - (abs(offset) * 0.12f).coerceIn(0f, 0.12f)
            Box(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
                contentAlignment = Alignment.Center,
            ) {
                FlipCard(
                    card = card,
                    flipped = flipped[page] == true,
                    onClick = { flipped[page] = !(flipped[page] ?: false) },
                )
            }
        }

        Text(
            if (onLast && flipped[pager.currentPage] != true) "Tap the card to flip"
            else "Swipe to explore Sunny",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextTertiary,
        )
        Spacer(Modifier.height(16.dp))

        // Page dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CARDS.indices.forEach { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier.size(if (active) 22.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) SunnyColors.Orange else SunnyColors.SurfaceMuted),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Primary action
        val label = if (onLast) "Get Started" else "Next"
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable {
                    if (onLast) onFinish()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            shape = RoundedCornerShape(28.dp), color = SunnyColors.Orange,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    color = SunnyColors.Surface, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlipCard(card: OnboardCard, flipped: Boolean, onClick: () -> Unit) {
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(500), label = "flip")
    Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
        // Stacked "deck" shadow cards behind.
        Box(Modifier.fillMaxSize().padding(top = 18.dp, start = 16.dp)
            .graphicsLayer { rotationZ = 4f }
            .clip(RoundedCornerShape(28.dp)).background(SunnyColors.TextTertiary.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxSize().padding(top = 10.dp)
            .graphicsLayer { rotationZ = -2f }
            .clip(RoundedCornerShape(28.dp)).background(SunnyColors.TextTertiary.copy(alpha = 0.4f)))

        // The flipping face.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { rotationY = angle; cameraDistance = 14f * density }
                .clip(RoundedCornerShape(28.dp))
                .background(card.tint)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            SpeckleOverlay(card.tint)
            if (angle <= 90f) CardFront(card)
            else Box(Modifier.graphicsLayer { rotationY = 180f }) { CardBack(card) }
        }
    }
}

@Composable
private fun CardFront(card: OnboardCard) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) { Text(card.emoji, fontSize = 64.sp) }
        Spacer(Modifier.height(28.dp))
        Text(card.title, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = SunnyColors.TextPrimary)
        Text(card.subtitle, style = MaterialTheme.typography.titleMedium,
            color = SunnyColors.TextSecondary)
    }
}

@Composable
private fun CardBack(card: OnboardCard) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(card.title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = SunnyColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(card.back, fontSize = 20.sp, color = SunnyColors.TextPrimary,
            textAlign = TextAlign.Start, lineHeight = 28.sp)
    }
}

/** Terrazzo-style speckles over a card, matching the reference flashcards. */
@Composable
private fun SpeckleOverlay(base: Color) {
    val dots = remember(base) {
        val rnd = Random(7)
        List(140) {
            Triple(rnd.nextFloat(), rnd.nextFloat(),
                if (rnd.nextBoolean()) Color.White.copy(alpha = 0.5f)
                else SunnyColors.TextPrimary.copy(alpha = 0.10f))
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        dots.forEach { (fx, fy, c) ->
            drawCircle(color = c, radius = 2.2f, center = Offset(fx * size.width, fy * size.height))
        }
    }
}
