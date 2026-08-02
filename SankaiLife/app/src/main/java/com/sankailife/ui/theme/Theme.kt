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
    /** Fond noir réel, pour les écrans OLED. Implique le mode sombre. */
    amoled: Boolean = false,
    /**
     * Accent du thème cosmétique équipé, ou `null` pour celui d'origine.
     *
     * **Il n'était pas branché du tout.** Les huit thèmes se débloquaient au
     * niveau 12, 15, 20, 25, dans les coffres rares et légendaires, et en
     * récompense d'arène — et en équiper un ne changeait pas un pixel :
     * `SankaiTheme` ne recevait jamais l'identifiant, et `accentHex` n'était lu
     * nulle part. Toute une piste de récompenses ne payait rien.
     *
     * Il ne s'applique qu'aux accents. Repeindre les fonds ou les erreurs avec
     * la couleur d'un thème rendrait l'interface illisible tout en ayant l'air
     * personnalisée.
     */
    accentTheme: Color? = null,
    content: @Composable () -> Unit
) {
    val contexte = LocalContext.current

    // `Build.VERSION.SDK_INT` est testé explicitement, et non déduit d'un
    // `try` : le lint refuse une API récente appelée sans garde visible, et il
    // a raison — une exception au lancement ne se voit qu'en production.
    val dynamique = couleursSysteme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val schemaBase = when {
        dynamique && darkTheme -> dynamicDarkColorScheme(contexte)
        dynamique -> dynamicLightColorScheme(contexte)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // AMOLED : noir réel pour le fond, surfaces conservées.
    //
    // Un noir approché ne sert à rien : sur une dalle OLED, seul le noir exact
    // éteint le pixel. Les surfaces gardent leur teinte, sinon les cartes
    // disparaissent dans le fond.
    val colorScheme = if (amoled) {
        schemaBase.copy(background = Color.Black, surface = schemaBase.surfaceContainerLowest)
    } else {
        schemaBase
    }

    val base = if (darkTheme) SankaiColors(
        background = Background, surface1 = Surface1, surface2 = Surface2,
        surface3 = Surface3, border = BorderColor,
        textPrimary = TextPrimary, textSecondary = TextSecondary, textDisabled = TextDisabled,
        accent = accentTheme?.let { Contraste.ajuster(it, Background) } ?: AccentGold,
        accentSecondary = AccentViolet, isDark = true
    ).let { if (amoled) it.copy(background = Color.Black) else it } else SankaiColors(
        background = LightBackground, surface1 = LightSurface1, surface2 = LightSurface2,
        surface3 = LightSurface3, border = LightBorder,
        textPrimary = LightTextPrimary, textSecondary = LightTextSecondary,
        textDisabled = Color(0xFFAAAAAA),
        // En mode clair, aucun des huit accents ne passe : de 1,44 a 2,77 pour
        // un minimum de 4,5. Ils sont assombris jusqu'a etre lisibles, en
        // gardant leur teinte — c'est elle qui fait l'identite d'un theme.
        accent = accentTheme?.let { Contraste.ajuster(it, LightBackground) }
            ?: Color(0xFFE8960D),
        accentSecondary = Color(0xFF5B4CF0), isDark = false
    )

    // Une seule palette à la fois, complète.
    //
    // La version précédente ne reprenait que les accents et deux surfaces :
    // `background` et `surface1` restaient le bleu nuit codé en dur. Le fond
    // de l'application restait donc bleu même avec une palette jaune, et les
    // écrans le peignaient par-dessus une racine pourtant dynamique.
    //
    // Les textes sont repris **avec** les fonds, et c'est délibéré. Je les
    // avais volontairement laissés de côté pour ne pas poser un texte système
    // sur un fond Sankai ; maintenant que le fond vient du système, c'est
    // l'inverse qui serait faux. Material You calcule ces deux rôles ensemble,
    // il faut les prendre ensemble.
    val sankaiColors = if (!dynamique) base else base.copy(
        background = if (amoled) Color.Black else colorScheme.background,
        surface1 = if (amoled) colorScheme.surfaceContainerLowest else colorScheme.surface,
        surface2 = colorScheme.surfaceContainer,
        surface3 = colorScheme.surfaceContainerHigh,
        border = colorScheme.outlineVariant,
        textPrimary = colorScheme.onBackground,
        textSecondary = colorScheme.onSurfaceVariant,
        textDisabled = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        // Les couleurs du téléphone l'emportent sur le thème équipé, y compris
        // sur son accent. Deux sources de couleur qui se disputent le même rôle
        // donnent une interface qui n'a plus d'identité du tout.
        //
        // C'est un vrai arbitrage, pas un oubli, et l'écran de personnalisation
        // le dit à qui a les deux actifs — sinon on retomberait exactement dans
        // le défaut qu'on vient de corriger : un thème équipé sans effet.
        accent = colorScheme.primary,
        accentSecondary = colorScheme.tertiary
    )

    CompositionLocalProvider(LocalSankaiColors provides sankaiColors) {
        MaterialTheme(colorScheme = colorScheme, typography = SankaiTypography, content = content)
    }
}

val MaterialTheme.sankaiColors: SankaiColors
    @Composable get() = LocalSankaiColors.current
