package com.sunny.skin.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunny.skin.AppMode
import com.sunny.skin.R
import com.sunny.skin.ui.components.OnboardingMotionScene
import com.sunny.skin.ui.theme.SunnyColors
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: String, val body: String)

/** A brand welcome followed by three concise, gesture-driven product pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    var walkthroughStarted by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = walkthroughStarted,
        transitionSpec = {
            if (!motionEnabled) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                val direction = if (targetState) 1 else -1
                (fadeIn(tween(240)) + slideInHorizontally(tween(360)) { direction * it / 5 })
                    .togetherWith(
                        fadeOut(tween(180)) +
                            slideOutHorizontally(tween(300)) { -direction * it / 5 },
                    )
            }
        },
        contentAlignment = Alignment.Center,
        label = "welcome to onboarding",
    ) { started ->
        if (started) {
            OnboardingWalkthrough(
                motionEnabled = motionEnabled,
                onBackToWelcome = { walkthroughStarted = false },
                onFinish = onFinish,
            )
        } else {
            WelcomeScreen(
                motionEnabled = motionEnabled,
                onStart = { walkthroughStarted = true },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingWalkthrough(
    motionEnabled: Boolean,
    onBackToWelcome: () -> Unit,
    onFinish: () -> Unit,
) {
    val serverBuild = AppMode.serverMode
    val pages = remember(serverBuild) {
        listOf(
            OnboardingPage(
                "Build a clear baseline",
                "Guided framing helps you photograph the same area consistently.",
            ),
            OnboardingPage(
                "Follow the visual story",
                "Add dated follow-ups and compare what looks different. Sunny describes appearance; it never diagnoses or assesses risk.",
            ),
            OnboardingPage(
                if (serverBuild) "Private records, clear choices" else "Private by design",
                if (serverBuild) {
                    "Saved records stay encrypted here. Beta analysis sends the selected photo to Sunny's configured server."
                } else {
                    "The AI model runs on this phone. Your saved photos and notes stay encrypted in Sunny."
                },
            ),
        )
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val progress by remember {
        derivedStateOf { pager.currentPage + pager.currentPageOffsetFraction }
    }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val isLast = pager.currentPage == pages.lastIndex
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        val compact = maxHeight < 720.dp || density.fontScale > 1.2f
        val sceneHeight = if (compact) 220.dp else 286.dp
        val pagerHeight = when {
            density.fontScale > 1.6f -> 260.dp
            density.fontScale > 1.2f -> 210.dp
            compact -> 156.dp
            else -> 126.dp
        }
        val supportingHeight = when {
            density.fontScale > 1.6f -> 250.dp
            density.fontScale > 1.2f -> 200.dp
            compact -> 126.dp
            else -> 112.dp
        }
        val columnModifier = if (compact) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }

        Column(
            Modifier.fillMaxSize().then(columnModifier)
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = {
                            if (pager.currentPage == 0) {
                                onBackToWelcome()
                            } else {
                                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            if (pager.currentPage == 0) "Back to welcome" else "Previous page",
                        )
                    }
                }
                Text(
                    "SUNNY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SunnyColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${pager.currentPage + 1}/${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SunnyColors.TextSecondary,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )
            }

            OnboardingMotionScene(
                progress = if (motionEnabled) progress else pager.currentPage.toFloat(),
                motionEnabled = motionEnabled,
                modifier = Modifier.height(sceneHeight),
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(pagerHeight),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        pages[page].title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SunnyColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pages[page].body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SunnyColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PageIndicator(progress = progress, pages = pages.size)
            Spacer(Modifier.height(10.dp))

            Crossfade(
                targetState = pager.currentPage,
                animationSpec = tween(if (motionEnabled) 180 else 0),
                label = "onboarding supporting content",
                modifier = Modifier.fillMaxWidth().height(supportingHeight),
            ) { page ->
                if (page == pages.lastIndex) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = acknowledged,
                            role = Role.Checkbox,
                            onValueChange = { acknowledged = it },
                        ).padding(horizontal = 2.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            AppMode.onboardingAcknowledgement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    val icon = if (page == 0) Icons.Filled.CameraAlt else Icons.Filled.CalendarMonth
                    val text = if (page == 0) {
                        "A calmer way to keep a consistent visual record"
                    } else {
                        "Literal photo and description differences, without a verdict"
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, null, tint = SunnyColors.OrangeText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary)
                    }
                }
            }

            if (!compact) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(12.dp))
            OnboardingButton(
                text = if (isLast) "Enter Sunny" else "Continue",
                enabled = !isLast || acknowledged,
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WelcomeScreen(
    motionEnabled: Boolean,
    onStart: () -> Unit,
) {
    val density = LocalDensity.current
    var appeared by remember { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { appeared = true }
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "welcome mascot entrance",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        val compact = maxHeight < 700.dp || density.fontScale > 1.2f
        val columnModifier = if (compact) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }

        Column(
            Modifier.fillMaxSize().then(columnModifier)
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "SUNNY",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            if (!compact) Spacer(Modifier.weight(0.7f)) else Spacer(Modifier.height(24.dp))

            Image(
                painter = painterResource(R.drawable.sunny_mascot),
                contentDescription = "Sunny mascot",
                modifier = Modifier.size(if (compact) 190.dp else 236.dp).graphicsLayer {
                    alpha = entrance
                    val scale = 0.86f + entrance * 0.14f
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - entrance) * 28.dp.toPx()
                },
            )

            Spacer(Modifier.height(if (compact) 18.dp else 26.dp))
            Text(
                "Sunny",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "A private visual journal for photographing and comparing visible skin changes over time.",
                style = MaterialTheme.typography.bodyLarge,
                color = SunnyColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = SunnyColors.OrangeText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Made for tracking, not diagnosis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(26.dp))
            }

            if (!compact) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(40.dp))
            OnboardingButton(
                text = "Get started",
                enabled = true,
                onClick = onStart,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PageIndicator(progress: Float, pages: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pages) { index ->
            val distance = kotlin.math.abs(progress - index).coerceIn(0f, 1f)
            val emphasis = 1f - distance
            Box(
                Modifier.size(width = (26 - 14 * distance).dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(lerp(SunnyColors.SwitchOffTrack, SunnyColors.OrangeText, emphasis)),
            )
        }
    }
}

@Composable
private fun OnboardingButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "onboarding button press",
    )
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.clip(RoundedCornerShape(28.dp)).clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) SunnyColors.OrangeText else SunnyColors.SurfaceMuted,
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) SunnyColors.Surface else SunnyColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
