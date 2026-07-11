package com.sunny.skin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors

/** White rounded card used across every screen. */
@Composable
fun SunnyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = SunnyColors.Surface,
        shadowElevation = 0.dp,
    ) { content() }
}

/** Small uppercase section label ("PRIVACY & SECURITY", "Filter:"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = SunnyColors.TextTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
    )
}

/**
 * The persistent not-a-diagnosis disclaimer (S-02). Two flavours: a subtle
 * inline line under analysis, and a bordered card for Settings/onboarding.
 */
@Composable
fun InlineDisclaimer(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        color = SunnyColors.TextTertiary,
        modifier = modifier,
    )
}

@Composable
fun DisclaimerCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SunnyColors.OrangeSoft.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, SunnyColors.Orange.copy(alpha = 0.25f)),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(
                Icons.Filled.Info, contentDescription = null,
                tint = SunnyColors.Orange, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = SunnyColors.TextPrimary,
                )
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    body,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                )
            }
        }
    }
}

/** Pill filter/segment chip (All / Head / Arms / Torso / Legs, Front / Back). */
@Composable
fun SunnyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) SunnyColors.Orange else SunnyColors.Surface
    val fg = if (selected) Color.White else SunnyColors.TextSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** Small rounded metadata chip ("Torso · Chest", "22 Feb 2026", "3:42 pm"). */
@Composable
fun MetaChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = SunnyColors.Surface,
        border = BorderStroke(1.dp, SunnyColors.Divider),
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Shared Switch colours. The off state uses a visible grey track + subtle border
 * so the white thumb reads clearly (not a near-invisible white pill).
 */
@Composable
fun sunnySwitchColors(checkedTrack: Color = SunnyColors.Success): SwitchColors =
    SwitchDefaults.colors(
        checkedTrackColor = checkedTrack,
        checkedThumbColor = SunnyColors.Surface,
        checkedBorderColor = checkedTrack,
        uncheckedThumbColor = SunnyColors.Surface,
        uncheckedTrackColor = SunnyColors.SwitchOffTrack,
        uncheckedBorderColor = SunnyColors.SwitchOffBorder,
    )
