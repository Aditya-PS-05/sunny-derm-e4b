package com.sunny.skin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.data.model.Analysis
import com.sunny.skin.data.model.normalized
import com.sunny.skin.ui.theme.SunnyColors

/**
 * The "Sunny Analysis" card — the six parsed fields in fixed order, each a
 * labelled row (design.md §4). Values use the controlled public vocabulary.
 * A persistent inline disclaimer sits directly under the header.
 */
@Composable
fun AnalysisCard(
    analysis: Analysis,
    modifier: Modifier = Modifier,
    changedLabels: Set<String> = emptySet(),
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WbSunny, contentDescription = null,
                tint = SunnyColors.Orange,
                modifier = Modifier.width(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Sunny Analysis",
                style = MaterialTheme.typography.titleMedium,
                color = SunnyColors.TextPrimary,
            )
        }
        Spacer(Modifier.padding(top = 2.dp))
        InlineDisclaimer(
            "A visual description to help you track changes — not a diagnosis. " +
                "See a professional for anything that concerns you.",
        )
        Spacer(Modifier.padding(top = 10.dp))

        SunnyCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val rows = analysis.normalized().rows()
                rows.forEachIndexed { i, (label, value) ->
                    FieldRow(label, value, changed = label in changedLabels)
                    if (i != rows.lastIndex) HorizontalDivider(color = SunnyColors.Divider)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, changed: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = SunnyColors.TextSecondary,
            )
            if (changed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "changed",
                    style = MaterialTheme.typography.labelSmall,
                    color = SunnyColors.Review,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.padding(top = 3.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = SunnyColors.TextPrimary,
        )
    }
}
