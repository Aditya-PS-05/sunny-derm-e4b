package com.sunny.skin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.sunnyPressScale

/** White rounded card used across every screen. */
@Composable
fun SunnyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.sunnyPressScale(interactionSource)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
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
fun InlineDisclaimer(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        color = SunnyColors.TextSecondary,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Keeps a decorative icon and its label together as one optically centred group. */
@Composable
fun CenteredIconLabel(
    icon: ImageVector,
    text: String,
    iconTint: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    spacing: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(spacing))
        Text(
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = maxLines,
        )
    }
}

@Composable
fun DisclaimerCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SunnyColors.Surface,
        border = BorderStroke(1.dp, SunnyColors.Divider),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(
                Icons.Filled.Warning, contentDescription = null,
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
    val interactionSource = remember { MutableInteractionSource() }
    val bg = if (selected) SunnyColors.Action else SunnyColors.Surface
    val fg = if (selected) Color.White else SunnyColors.TextSecondary
    Box(
        modifier = modifier
            .sunnyPressScale(interactionSource)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
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
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
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

/**
 * iOS-style toggle with full control over the thumb size (Material3's Switch
 * shrinks its off-state thumb to a small dot). A large white thumb slides on a
 * pill track — grey with a subtle border when off, [onColor] when on.
 */
@Composable
fun SunnyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = SunnyColors.Success,
    accessibilityLabel: String? = null,
) {
    val trackW = 52.dp
    val trackH = 32.dp
    val thumb = 26.dp
    val pad = 3.dp
    val animationSpec = tween<androidx.compose.ui.unit.Dp>(
        durationMillis = SunnyMotion.StateMillis,
        easing = SunnyMotion.EaseInOut,
    )
    val colorAnimationSpec = tween<Color>(
        durationMillis = SunnyMotion.StateMillis,
        easing = SunnyMotion.EaseInOut,
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackW - thumb - pad else pad,
        animationSpec = animationSpec,
        label = "thumbX",
    )
    val track by animateColorAsState(
        targetValue = if (checked) onColor else SunnyColors.SwitchOffTrack,
        animationSpec = colorAnimationSpec,
        label = "track",
    )
    val border by animateColorAsState(
        targetValue = if (checked) Color.Transparent else SunnyColors.SwitchOffBorder,
        animationSpec = colorAnimationSpec,
        label = "toggle border",
    )
    Box(
        modifier
            .size(trackW, trackH)
            .clip(RoundedCornerShape(50))
            .background(track)
            .border(1.dp, border, RoundedCornerShape(50))
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics { contentDescription = accessibilityLabel }
                } else {
                    Modifier
                },
            )
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = thumbX.toPx() }
                .size(thumb)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
