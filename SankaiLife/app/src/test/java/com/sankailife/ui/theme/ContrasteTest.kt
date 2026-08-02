package com.sankailife.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
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

    /**
     * Les surfaces « container » appartiennent bien à la palette Sankai.
     *
     * `darkColorScheme()` remplit tout paramètre omis avec les valeurs de base
     * de Material — un gris-violet. Les surfaces de verre, qui s'en servent,
     * viraient donc au violet sur tout appareil sans couleurs dynamiques,
     * pendant que le reste de l'écran restait bleu nuit : exactement le défaut
     * de deux palettes superposées qu'on venait de corriger.
     *
     * Le bleu nuit de Sankai a plus de bleu que de rouge ; le gris-violet de
     * Material, l'inverse. C'est ce que ce test vérifie.
     */
    @Test
    fun `les surfaces sombres restent bleu nuit et non violettes`() {
        listOf(
            "surfaceContainer" to DarkColorScheme.surfaceContainer,
            "surfaceContainerHigh" to DarkColorScheme.surfaceContainerHigh,
            "surfaceContainerHighest" to DarkColorScheme.surfaceContainerHighest,
            "outlineVariant" to DarkColorScheme.outlineVariant
        ).forEach { (nom, couleur) ->
            assertTrue(
                "$nom n'est pas bleu : rouge=%.2f bleu=%.2f".format(couleur.red, couleur.blue),
                couleur.blue > couleur.red + 0.08f
            )
        }
    }

    @Test
    fun `les textes sur fond et sur surface restent lisibles`() {
        verifier("clair / background", LightColorScheme.background, LightColorScheme.onBackground)
        verifier("clair / surface", LightColorScheme.surface, LightColorScheme.onSurface)
        verifier("sombre / background", DarkColorScheme.background, DarkColorScheme.onBackground)
        verifier("sombre / surface", DarkColorScheme.surface, DarkColorScheme.onSurface)
    }

    // --- Accents des themes cosmetiques ---------------------------------------

    /**
     * Les huit themes debloquables doivent rester lisibles.
     *
     * Ils n'avaient aucun effet jusqu'ici : SankaiTheme ne recevait jamais le
     * theme equipe. En les branchant, la mesure a montre qu'aucun ne passait en
     * mode clair — de 1,44 a 2,77 pour un minimum de 4,5. Corriger un defaut en
     * rendant du texte illisible aurait ete un mauvais echange.
     */
    @Test
    fun `chaque accent de theme est lisible sur les deux fonds`() {
        com.sankailife.core.domain.model.ALL_THEMES.forEach { theme ->
            val brut = Contraste.depuisHex(theme.accentHex)!!
            listOf("sombre" to Background, "clair" to LightBackground).forEach { (nom, fond) ->
                val ajuste = Contraste.ajuster(brut, fond)
                val r = Contraste.ratio(ajuste, fond)
                assertTrue(
                    "${theme.name} sur fond $nom : ${"%.2f".format(r)}:1",
                    r >= Contraste.CIBLE_AA - 0.01
                )
            }
        }
    }

    @Test
    fun `un accent deja lisible n'est pas touche`() {
        // Le cas de tous les themes en mode sombre : y toucher changerait
        // l'apparence de l'application sans aucune raison.
        com.sankailife.core.domain.model.ALL_THEMES.forEach { theme ->
            val brut = Contraste.depuisHex(theme.accentHex)!!
            if (Contraste.ratio(brut, Background) >= Contraste.CIBLE_AA) {
                assertEquals(
                    "${theme.name} modifie sans necessite",
                    brut, Contraste.ajuster(brut, Background)
                )
            }
        }
    }

    @Test
    fun `la lecture hexadecimale accepte les formes usuelles`() {
        assertEquals(Color(0xFFF5A623), Contraste.depuisHex("#F5A623"))
        assertEquals(Color(0xFFF5A623), Contraste.depuisHex("F5A623"))
        assertEquals(Color(0x80F5A623), Contraste.depuisHex("#80F5A623"))
    }

    @Test
    fun `une couleur illisible ne fait pas tomber l'application`() {
        assertEquals(null, Contraste.depuisHex(""))
        assertEquals(null, Contraste.depuisHex("#XYZ"))
        assertEquals(null, Contraste.depuisHex("#12345"))
    }

    @Test
    fun `l'ajustement garde la teinte`() {
        // Un dore plus sombre reste dore ; un dore illisible n'est plus rien.
        val dore = Color(0xFFFCD34D)
        val ajuste = Contraste.ajuster(dore, LightBackground)
        assertTrue("Le rouge devrait rester dominant", ajuste.red > ajuste.blue)
        assertTrue("Le vert devrait rester devant le bleu", ajuste.green > ajuste.blue)
        assertTrue("La couleur devrait s'etre assombrie", ajuste.blue < dore.blue)
    }

    @Test
    fun `l'ajustement est stable`() {
        // Reappliquer la correction ne doit plus rien changer, sinon la couleur
        // deriverait a chaque recomposition.
        val brut = Color(0xFF22D3EE)
        val une = Contraste.ajuster(brut, LightBackground)
        val deux = Contraste.ajuster(une, LightBackground)
        assertEquals(une, deux)
    }
}
