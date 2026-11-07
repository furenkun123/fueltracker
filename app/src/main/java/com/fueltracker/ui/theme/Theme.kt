package com.fueltracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
    onSurfaceSecondary = TextSecondary,

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
    onSurfaceSecondary = TextSecondaryDark,

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

    MiuixTheme(
        colors = colors,
        textStyles = AppTextStyles,
        content = content
    )
}
