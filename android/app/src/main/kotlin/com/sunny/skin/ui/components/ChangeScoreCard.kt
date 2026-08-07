package com.sunny.skin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.data.model.AnalysisChange

/**
 * Reports only literal differences between two generated descriptions. It does
 * not score pixels, classify stability, assess risk, or recommend a care delay.
 */
@Composable
fun ChangeSummaryCard(
    sinceDate: String,
    changes: List<AnalysisChange>,
    modifier: Modifier = Modifier,
) {
    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "What changed since $sinceDate",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                if (changes.isEmpty()) {
                    "No description differences found"
                } else {
                    "${changes.size} ${if (changes.size == 1) "detail looks" else "details look"} different"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
            )

            if (changes.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    changes.forEach { ChangeRow(it) }
                }
            }

            Spacer(Modifier.size(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(SunnyColors.Surface)
                    .border(1.dp, SunnyColors.Divider, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = SunnyColors.Orange,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    if (changes.isEmpty()) {
                        "No description difference does not prove the area is unchanged. " +
                            "Lighting, framing, and AI descriptions can miss visual changes."
                    } else {
                        "This compares AI-generated visual descriptions, not medical risk. " +
                            "Review the photos side by side or share them with a clinician."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChangeRow(change: AnalysisChange) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SunnyColors.Surface)
            .border(1.dp, SunnyColors.Divider, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            change.label,
            style = MaterialTheme.typography.labelLarge,
            color = SunnyColors.Orange,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChangeValue("Previous", change.previous, Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "changed to",
                tint = SunnyColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
            )
            ChangeValue("Current", change.current, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChangeValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SunnyColors.TextTertiary)
        Spacer(Modifier.size(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}
