package com.sankailife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SankaiColors(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentSecondary: Color,
    val isDark: Boolean
)

val LocalSankaiColors = staticCompositionLocalOf {
    SankaiColors(
        background = Background, surface1 = Surface1, surface2 = Surface2,
        surface3 = Surface3, border = BorderColor,
        textPrimary = TextPrimary, textSecondary = TextSecondary, textDisabled = TextDisabled,
        accent = AccentGold, accentSecondary = AccentViolet, isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary         = AccentGold,
    secondary       = AccentViolet,
    tertiary        = AccentCyan,
    background      = Background,
    surface         = Surface1,
    surfaceVariant  = Surface2,
    onPrimary       = Color.Black,
    onSecondary     = Color.White,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    outline         = BorderColor
)

private val LightColorScheme = lightColorScheme(
    primary         = Color(0xFFE8960D),
    secondary       = Color(0xFF5B4CF0),
    tertiary        = Color(0xFF0891B2),
    background      = LightBackground,
    surface         = LightSurface1,
    surfaceVariant  = LightSurface2,
    onPrimary       = Color.White,
    onSecondary     = Color.White,
    onBackground    = LightTextPrimary,
    onSurface       = LightTextPrimary,
    outline         = LightBorder
)

@Composable
fun SankaiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val sankaiColors = if (darkTheme) SankaiColors(
        background = Background, surface1 = Surface1, surface2 = Surface2,
        surface3 = Surface3, border = BorderColor,
        textPrimary = TextPrimary, textSecondary = TextSecondary, textDisabled = TextDisabled,
        accent = AccentGold, accentSecondary = AccentViolet, isDark = true
    ) else SankaiColors(
        background = LightBackground, surface1 = LightSurface1, surface2 = LightSurface2,
        surface3 = LightSurface3, border = LightBorder,
        textPrimary = LightTextPrimary, textSecondary = LightTextSecondary,
        textDisabled = Color(0xFFAAAAAA), accent = Color(0xFFE8960D),
        accentSecondary = Color(0xFF5B4CF0), isDark = false
    )
    CompositionLocalProvider(LocalSankaiColors provides sankaiColors) {
        MaterialTheme(colorScheme = colorScheme, typography = SankaiTypography, content = content)
    }
}

val MaterialTheme.sankaiColors: SankaiColors
    @Composable get() = LocalSankaiColors.current
