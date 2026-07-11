package com.sunny.skin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.theme.SunnyColors

/**
 * Retention hero on Overview: the current weekly check-in streak plus a small
 * 10-week activity chart. Gentle and encouraging — no guilt, no red — matching
 * the balloon-greeting tone.
 */
@Composable
fun StreakCard(habit: com.sunny.skin.ui.HabitStats, modifier: Modifier = Modifier) {
    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(SunnyColors.OrangeSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = SunnyColors.Orange,
                        modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    val streak = habit.currentStreakWeeks
                    Text(
                        when {
                            streak <= 0 -> "Start your streak"
                            else -> "$streak week${if (streak == 1) "" else "s"} in a row"
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
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { i, c ->
            val isThisWeek = i == counts.lastIndex
            val frac = c.toFloat() / max
            val h = (6f + frac * 34f).dp // 6dp stub for empty weeks, up to 40dp
            val color = when {
                c == 0 -> SunnyColors.SurfaceMuted
                isThisWeek -> SunnyColors.Orange
                else -> SunnyColors.OrangeLight
            }
            Box(
                Modifier.weight(1f).height(h).clip(RoundedCornerShape(50)).background(color),
            )
        }
    }
}
