package com.sunny.skin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors

/**
 * Reports only literal differences between two generated descriptions. It does
 * not score pixels, classify stability, assess risk, or recommend a care delay.
 */
@Composable
fun ChangeSummaryCard(
    sinceDate: String,
    changedAspects: List<String>,
    modifier: Modifier = Modifier,
) {
    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Description comparison since $sinceDate",
                style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                if (changedAspects.isEmpty()) {
                    "No text-field differences detected"
                } else {
                    "Description differences noted"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SunnyColors.TextPrimary,
            )

            if (changedAspects.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    changedAspects.forEach { AspectChip(it) }
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
                    if (changedAspects.isEmpty()) {
                        "This does not show that the spot is stable, safe, or unchanged. " +
                            "Photos and AI descriptions can miss important changes."
                    } else {
                        "Consider showing the photos to a clinician. Differences are visual notes, " +
                            "not a risk or urgency assessment."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AspectChip(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(SunnyColors.Surface)
            .border(1.dp, SunnyColors.Divider, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.Orange,
            fontWeight = FontWeight.Medium,
        )
    }
}
