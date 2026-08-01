package com.sunny.skin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled

/**
 * Retention hero on Overview: the current weekly check-in streak plus a small
 * 10-week activity chart. Gentle and encouraging — no guilt, no red — matching
 * the balloon-greeting tone.
 */
@Composable
fun StreakCard(habit: com.sunny.skin.ui.HabitStats, modifier: Modifier = Modifier) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val displayedStreak by animateIntAsState(
        targetValue = habit.currentStreakWeeks,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Weekly streak",
    )
    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = SunnyColors.Orange,
                        modifier = Modifier.size(36.dp))
                    Canvas(
                        Modifier.size(12.dp, 17.dp).align(Alignment.BottomCenter).offset(y = (-6).dp),
                    ) {
                        val core = Path().apply {
                            moveTo(size.width * 0.52f, 0f)
                            cubicTo(
                                size.width * 0.5f, size.height * 0.25f,
                                size.width * 0.16f, size.height * 0.4f,
                                size.width * 0.16f, size.height * 0.68f,
                            )
                            cubicTo(
                                size.width * 0.16f, size.height * 0.9f,
                                size.width * 0.32f, size.height,
                                size.width * 0.5f, size.height,
                            )
                            cubicTo(
                                size.width * 0.78f, size.height,
                                size.width * 0.9f, size.height * 0.8f,
                                size.width * 0.86f, size.height * 0.6f,
                            )
                            cubicTo(
                                size.width * 0.82f, size.height * 0.38f,
                                size.width * 0.65f, size.height * 0.24f,
                                size.width * 0.52f, 0f,
                            )
                            close()
                        }
                        drawPath(core, SunnyColors.FlameCore)
                        drawCircle(
                            color = SunnyColors.OrangeLight,
                            radius = size.minDimension * 0.1f,
                            center = Offset(size.width * 0.53f, size.height * 0.73f),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    val streak = habit.currentStreakWeeks
                    val animatedStreak = displayedStreak.coerceAtLeast(1)
                    Text(
                        when {
                            streak <= 0 -> "Start your streak"
                            else -> "$animatedStreak week${if (animatedStreak == 1) "" else "s"} in a row"
                        },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            habit.totalObservations == 0 ->
                                "Take your first photo to begin tracking."
                            streak <= 0 ->
                                "Add a photo this week to start a check-in streak."
                            habit.activeThisWeek ->
                                "Nice — you've checked in this week."
                            else ->
                                "Add a photo this week to keep your streak going."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    )
                }
            }

            if (habit.totalObservations > 0) {
                Spacer(Modifier.height(16.dp))
                WeeklyBars(habit.weeklyCounts)
                Spacer(Modifier.height(6.dp))
                Text("Last ${habit.weeklyCounts.size} weeks",
                    style = MaterialTheme.typography.labelSmall, color = SunnyColors.TextTertiary)
            }
        }
    }
}

/** A tiny bar chart of photos-per-week; the last bar (this week) is highlighted. */
@Composable
private fun WeeklyBars(counts: List<Int>) {
    val motionEnabled = rememberSunnyMotionEnabled()
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { i, c ->
            val reveal = remember { Animatable(if (motionEnabled) 0.85f else 1f) }
            LaunchedEffect(c, motionEnabled) {
                if (motionEnabled) {
                    reveal.snapTo(0.85f)
                    reveal.animateTo(
                        1f,
                        animationSpec = tween(
                            SunnyMotion.StateMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                    )
                } else {
                    reveal.snapTo(1f)
                }
            }
            val isThisWeek = i == counts.lastIndex
            val frac = c.toFloat() / max
            val h = (6f + frac * 34f).dp // 6dp stub for empty weeks, up to 40dp
            val color = when {
                c == 0 -> SunnyColors.SurfaceMuted
                isThisWeek -> SunnyColors.Orange
                else -> SunnyColors.OrangeLight
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(h)
                    .graphicsLayer {
                        scaleY = reveal.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}
