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
    val OrangeSoft = Color(0xFFFFE9D4)   // tint fill behind selected chips / icons

    val Background = Color(0xFFF2F2F4)   // light grey app canvas (reference)
    val Surface = Color(0xFFFFFFFF)      // white cards
    val SurfaceMuted = Color(0xFFE9E9EB) // inset rows / progress track

    val TextPrimary = Color(0xFF1C1B1A)
    val TextSecondary = Color(0xFF8E8E93)
    val TextTertiary = Color(0xFFB9B9BE)

    val Divider = Color(0xFFE5E5EA)
    val Success = Color(0xFF34C759)      // Face ID toggle / "scanned" check
    val Review = Color(0xFFF5A623)       // amber "changed since last check"
    val Danger = Color(0xFFE5484D)       // destructive (delete) actions only
}
