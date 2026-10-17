package com.fueltracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary            = Blue600,
    onPrimary          = OnPrimaryWhite,
    primaryContainer   = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,

    background         = BackgroundLight,
    onBackground       = TextPrimary,

    surface            = SurfaceWhite,
    onSurface          = TextPrimary,

    surfaceVariant     = SurfaceVariantLight,
    onSurfaceVariant   = TextSecondary,

    outline            = OutlineLight,
    error              = ErrorLight,
    onError            = OnPrimaryWhite,
)

private val DarkColors = darkColorScheme(
    primary            = Blue400,
    onPrimary          = OnPrimaryWhite,
    primaryContainer   = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,

    background         = BackgroundDark,
    onBackground       = TextPrimaryDark,

    surface            = SurfaceDark,
    onSurface          = TextPrimaryDark,

    surfaceVariant     = SurfaceVariantDark,
    onSurfaceVariant   = TextSecondaryDark,

    outline            = OutlineDark,
    error              = ErrorDark,
    onError            = OnPrimaryWhite,
)


@Composable
fun FuelTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val colors = if (isDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}