package com.sunny.skin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.util.ChangeAnalysis

/** Colour + wording for a change level. Green/amber/orange — never alarming red. */
private fun level(level: ChangeAnalysis.Level): Triple<Color, String, String> = when (level) {
    ChangeAnalysis.Level.STABLE ->
        Triple(SunnyColors.Success, "Stable", "Looks about the same as before.")
    ChangeAnalysis.Level.MINOR ->
        Triple(SunnyColors.Review, "Minor change", "A little different from the previous photo.")
    ChangeAnalysis.Level.NOTABLE ->
        Triple(SunnyColors.Orange, "Notable change", "This looks meaningfully different from before.")
}

/**
 * The change-detection hero card on a tracked spot: a relative 0–100 change score
 * with a level, the aspects the description flagged, and — for real change — a
 * gentle nudge to show a clinician. Reads as routing information, never a verdict
 * (F-14). [result] null means the measurement is still running.
 */
@Composable
fun ChangeScoreCard(
    result: ChangeAnalysis.ChangeResult?,
    sinceDate: String,
    changedAspects: List<String>,
    modifier: Modifier = Modifier,
) {
    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            if (result == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp),
                        color = SunnyColors.Orange, strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("Measuring change…", style = MaterialTheme.typography.titleMedium,
                        color = SunnyColors.TextSecondary)
                }
                return@Column
            }

            val (color, title, subtitle) = level(result.level)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Change since $sinceDate",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = color)
                }
                // Level pill.
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${result.score}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = color)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Score bar (relative indicator, 0–100).
            val fraction by animateFloatAsState(result.score / 100f, label = "changeBar")
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                    .background(SunnyColors.SurfaceMuted),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                        .clip(RoundedCornerShape(50)).background(color),
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)

            if (changedAspects.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("What looks different", style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    changedAspects.forEach { AspectChip(it, color) }
                }
            }

            if (result.level != ChangeAnalysis.Level.STABLE) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(SunnyColors.Surface)
                        .border(1.dp, SunnyColors.Divider, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = color,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Consider showing these photos to a clinician. Sunny tracks appearance " +
                            "only and doesn't assess risk.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectChip(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(SunnyColors.Surface)
            .border(1.dp, SunnyColors.Divider, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = color, fontWeight = FontWeight.Medium)
    }
}
