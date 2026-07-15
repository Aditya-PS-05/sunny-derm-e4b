package com.sunny.skin.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sunny is intentionally a single, calm light theme (design.md §1: "calm, not
 * alarming"). We do not follow the system dark theme — a health-adjacent app
 * benefits from one predictable, low-anxiety surface.
 */
private val SunnyColorScheme = lightColorScheme(
    primary = SunnyColors.OrangeText,
    onPrimary = SunnyColors.Surface,
    primaryContainer = SunnyColors.OrangeSoft,
    onPrimaryContainer = SunnyColors.OrangeText,
    background = SunnyColors.Background,
    onBackground = SunnyColors.TextPrimary,
    surface = SunnyColors.Surface,
    onSurface = SunnyColors.TextPrimary,
    surfaceVariant = SunnyColors.SurfaceMuted,
    onSurfaceVariant = SunnyColors.TextSecondary,
    outline = SunnyColors.Divider,
    error = SunnyColors.Danger,
    onError = SunnyColors.Surface,
)

@Composable
fun SunnyTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SunnyColors.Background.toArgb()
            window.navigationBarColor = SunnyColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = SunnyColorScheme,
        typography = SunnyTypography,
        content = content,
    )
}
