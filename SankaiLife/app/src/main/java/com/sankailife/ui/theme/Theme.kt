package com.sankailife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

internal val DarkColorScheme = darkColorScheme(
    primary         = AccentGold,
    secondary       = AccentViolet,
    tertiary        = AccentCyan,
    background      = Background,
    surface         = Surface1,
    surfaceVariant  = Surface2,
    onPrimary       = Color.Black,
    // Le violet d'accent est clair : du blanc dessus ne donnait que 3,95:1.
    // Un texte sombre y est nettement plus lisible, et le test le verifie.
    onSecondary     = Color(0xFF120A2E),
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    outline         = BorderColor,

    // Les surfaces « container » et `outlineVariant` doivent être déclarées.
    //
    // Sans elles, `darkColorScheme()` retombe sur les valeurs de base de
    // Material — un gris-violet sans aucun rapport avec le bleu nuit de
    // Sankai. Les surfaces Liquid Glass, qui les utilisent, viraient au violet
    // dès qu'un téléphone ne fournissait pas de couleurs dynamiques.
    surfaceContainerLowest  = Background,
    surfaceContainerLow     = Surface1,
    surfaceContainer        = Surface1,
    surfaceContainerHigh    = Surface2,
    surfaceContainerHighest = Surface3,
    outlineVariant          = BorderColor
)

// Palette claire de repli, utilisée quand le téléphone ne fournit pas de
// couleurs dynamiques ou quand le joueur les a désactivées.
//
// `onPrimary` était blanc sur un orange saturé : 2,39:1, soit très en dessous
// du minimum lisible. Le bouton principal de l'application était donc
// difficilement lisible en thème clair. Un test verrouille désormais ces
// contrastes.
internal val LightColorScheme = lightColorScheme(
    primary         = Color(0xFFE8960D),
    secondary       = Color(0xFF5B4CF0),
    tertiary        = Color(0xFF0891B2),
    background      = LightBackground,
    surface         = LightSurface1,
    surfaceVariant  = LightSurface2,
    onPrimary       = Color(0xFF2B1800),
    onSecondary     = Color.White,
    onBackground    = LightTextPrimary,
    onSurface       = LightTextPrimary,
    outline         = LightBorder,

    // Même raison qu'en thème sombre : sans ces valeurs, Material impose son
    // lavande de base.
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = LightSurface1,
    surfaceContainer        = LightSurface2,
    surfaceContainerHigh    = LightSurface3,
    surfaceContainerHighest = LightSurface3,
    outlineVariant          = LightBorder
)

/**
 * Thème de l'application.
 *
 * Sur Android 12 et au-delà, la palette du téléphone peut être reprise
 * (Material You). Elle ne remplace pas tout : elle fournit les **accents** et
 * les **surfaces**, c'est-à-dire ce qui doit s'harmoniser avec le système.
 *
 * Ce qui a un sens ne bouge jamais — l'eau reste bleue, une erreur rouge, une
 * récompense dorée. Un thème jaune qui transformerait l'eau en jaune ou les
 * erreurs en violet rendrait l'interface illisible tout en ayant l'air
 * « personnalisée ».
 *
 * Les illustrations du Jardin et de l'Île ne sont jamais teintées : ce sont des
 * dessins, pas des composants.
 */
@Composable
fun SankaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    couleursSysteme: Boolean = true,
    content: @Composable () -> Unit
) {
    val contexte = LocalContext.current

    // `Build.VERSION.SDK_INT` est testé explicitement, et non déduit d'un
    // `try` : le lint refuse une API récente appelée sans garde visible, et il
    // a raison — une exception au lancement ne se voit qu'en production.
    val dynamique = couleursSysteme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamique && darkTheme -> dynamicDarkColorScheme(contexte)
        dynamique -> dynamicLightColorScheme(contexte)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val base = if (darkTheme) SankaiColors(
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

    // La palette Sankai emprunte au système ses accents et ses surfaces, et
    // garde le reste. Les textes ne sont pas repris : ceux du système sont
    // calculés pour ses propres fonds, et les appliquer aux nôtres produit des
    // contrastes que personne n'a vérifiés.
    val sankaiColors = if (!dynamique) base else base.copy(
        accent = colorScheme.primary,
        accentSecondary = colorScheme.tertiary,
        surface2 = colorScheme.surfaceVariant,
        surface3 = colorScheme.surfaceContainerHigh,
        border = colorScheme.outlineVariant
    )

    CompositionLocalProvider(LocalSankaiColors provides sankaiColors) {
        MaterialTheme(colorScheme = colorScheme, typography = SankaiTypography, content = content)
    }
}

val MaterialTheme.sankaiColors: SankaiColors
    @Composable get() = LocalSankaiColors.current
