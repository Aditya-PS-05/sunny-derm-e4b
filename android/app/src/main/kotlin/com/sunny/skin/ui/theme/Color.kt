package com.sunny.skin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sunny palette — pulled directly from the reference design.
 * Orange accent on a cool light-grey canvas, white cards, near-black text.
 * Accent colour is reserved for actions and the "changed" marker only
 * (design.md §8: informative amber "review", never alarming red).
 */
object SunnyColors {
    val Orange = Color(0xFFFF7A00)
    val OrangeDark = Color(0xFFE86A00)
    val OrangeText = Color(0xFFB54708)  // AA text/button companion on light surfaces
    val Action = OrangeText             // AA-compliant fill for controls with white content
    val OrangeLight = Color(0xFFFFCA80)  // light orange (progress fill, recolored icons)
    val OrangeSoft = Color(0xFFFFE9D4)   // tint fill behind selected chips / icons
    val FlameCore = Color(0xFFE64A19)    // warm red, reserved for the inner flame detail

    val Background = Color(0xFFF2F2F4)   // light grey app canvas (reference)
    val Surface = Color(0xFFFFFFFF)      // white cards
    val SurfaceMuted = Color(0xFFE9E9EB) // inset rows / progress track

    val TextPrimary = Color(0xFF1C1B1A)
    val TextSecondary = Color(0xFF5F5F65)
    val TextTertiary = Color(0xFF6D6D73)

    val Divider = Color(0xFFE5E5EA)
    val Success = Color(0xFF1B7F3A)      // accessible success icon/text and enabled toggle

    // Off-state switch: a clearly visible grey track + subtle border, so the
    // white thumb reads against it (instead of a near-invisible white pill).
    val SwitchOffTrack = Color(0xFFC9CBD3)
    val SwitchOffBorder = Color(0xFFB6B9C2)
    val Review = Color(0xFF8A5300)       // accessible amber "changed since last check"
    val Danger = Color(0xFFD1363B)       // AA contrast on white; destructive actions only
}
