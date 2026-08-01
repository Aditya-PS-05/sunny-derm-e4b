package com.sunny.skin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunny.skin.data.AbcdeAnswer
import com.sunny.skin.data.AbcdeItem
import com.sunny.skin.ui.theme.SunnyColors

/**
 * The per-spot ABCDE self-check: an educational checklist that teaches what
 * dermatologists look for. Sunny records the answers but draws NO conclusion from
 * them — a "Yes" only surfaces a gentle "worth mentioning to a clinician" note.
 */
@Composable
fun AbcdeCard(
    answers: Map<AbcdeItem, AbcdeAnswer>,
    onAnswer: (AbcdeItem, AbcdeAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val anyYes = answers.values.any { it == AbcdeAnswer.YES }
    val answered = answers.values.count { it != AbcdeAnswer.UNSET }

    SunnyCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ABCDE self-check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (expanded) "Educational checklist · $answered of 5 answered"
                        else "$answered of 5 answered · tap to open",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse ABCDE self-check" else "Expand ABCDE self-check",
                    tint = SunnyColors.TextTertiary,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.align(Alignment.End).clip(RoundedCornerShape(50)).background(SunnyColors.Surface)
                        .border(1.dp, SunnyColors.Divider, RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showInfo = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = SunnyColors.Orange,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("What's this?", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.OrangeText, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                AbcdeItem.entries.forEachIndexed { i, item ->
                    AbcdeRow(item, answers[item] ?: AbcdeAnswer.UNSET) { onAnswer(item, it) }
                    if (i < AbcdeItem.entries.lastIndex) Spacer(Modifier.height(14.dp))
                }

                if (anyYes) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(SunnyColors.OrangeSoft).padding(12.dp),
                    ) {
                        Text(
                            "You flagged something worth mentioning to a clinician. This is a " +
                                "learning aid — Sunny doesn't diagnose or assess risk.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showInfo) AbcdeInfoDialog(onDismiss = { showInfo = false })
}

@Composable
private fun AbcdeRow(item: AbcdeItem, answer: AbcdeAnswer, onAnswer: (AbcdeAnswer) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.letter, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = SunnyColors.OrangeText)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.question, style = MaterialTheme.typography.bodyMedium,
                color = SunnyColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            TriSelector(answer, onAnswer)
        }
    }
}

/** No / Yes / Not sure — a muted track with a white selected pill (app segmented style). */
@Composable
private fun TriSelector(answer: AbcdeAnswer, onAnswer: (AbcdeAnswer) -> Unit) {
    val options = listOf(
        AbcdeAnswer.NO to "No",
        AbcdeAnswer.YES to "Yes",
        AbcdeAnswer.UNSURE to "Not sure",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
            .background(SunnyColors.SurfaceMuted).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = answer == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (selected) SunnyColors.Surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onAnswer(if (selected) AbcdeAnswer.UNSET else value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Educational "what dermatologists look for" modal (glass), purely informational. */
@Composable
private fun AbcdeInfoDialog(onDismiss: () -> Unit) {
    LiquidGlassDialog(onDismiss = onDismiss) { requestDismiss ->
        Text("The ABCDE guide", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = SunnyColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Five things dermatologists teach people to notice on a mole.",
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            AbcdeItem.entries.forEach { item ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(
                        Modifier.size(30.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.letter, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = SunnyColors.OrangeText)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.info, style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "This is general education, not medical advice or a diagnosis. If you're " +
                "concerned about a spot, see a healthcare professional.",
            style = MaterialTheme.typography.bodySmall, color = SunnyColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(50.dp)
                .clip(RoundedCornerShape(25.dp)).background(SunnyColors.Action)
                .clickable(onClick = requestDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Got it", style = MaterialTheme.typography.titleMedium,
                color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
