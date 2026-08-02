package com.sankailife.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrastes des palettes de repli.
 *
 * Ces palettes servent quand le téléphone ne fournit pas de couleurs
 * dynamiques — Android 11 et antérieurs — ou quand le joueur les a
 * désactivées. Personne ne les regarde en développant, puisque les appareils
 * de test sont récents : elles se dégradent donc en silence.
 *
 * Ce fichier existe parce que c'est arrivé. En passant le bouton principal de
 * couleurs codées en dur au rôle `onPrimary`, son texte est devenu blanc sur
 * orange : **2,39:1**, illisible. Les couleurs dynamiques, elles, calculent ce
 * rôle correctement, donc rien ne se voyait sur un téléphone récent.
 *
 * Le seuil retenu est celui du texte normal, 4,5:1.
 */
class ContrasteTest {

    private fun luminance(couleur: Color): Double {
        fun canal(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal(couleur.red) +
            0.7152 * canal(couleur.green) +
            0.0722 * canal(couleur.blue)
    }

    private fun contraste(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun verifier(nom: String, fond: Color, texte: Color) {
        val ratio = contraste(fond, texte)
        assertTrue(
            "$nom : contraste %.2f:1, minimum 4.5:1".format(ratio),
            ratio >= 4.5
        )
    }

    @Test
    fun `le bouton principal reste lisible en theme clair`() {
        verifier(
            "clair / primary",
            LightColorScheme.primary,
            LightColorScheme.onPrimary
        )
    }

    @Test
    fun `le bouton principal reste lisible en theme sombre`() {
        verifier(
            "sombre / primary",
            DarkColorScheme.primary,
            DarkColorScheme.onPrimary
        )
    }

    @Test
    fun `les boutons secondaires restent lisibles`() {
        verifier("clair / secondary", LightColorScheme.secondary, LightColorScheme.onSecondary)
        verifier("sombre / secondary", DarkColorScheme.secondary, DarkColorScheme.onSecondary)
    }

    @Test
    fun `les textes sur fond et sur surface restent lisibles`() {
        verifier("clair / background", LightColorScheme.background, LightColorScheme.onBackground)
        verifier("clair / surface", LightColorScheme.surface, LightColorScheme.onSurface)
        verifier("sombre / background", DarkColorScheme.background, DarkColorScheme.onBackground)
        verifier("sombre / surface", DarkColorScheme.surface, DarkColorScheme.onSurface)
    }
}
