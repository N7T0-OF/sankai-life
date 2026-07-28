package com.sankailife

import com.sankailife.core.garden.domain.MemoChallengeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests du défi souvenir.
 *
 * L'enjeu n'est pas l'affichage mais l'impossibilité de rejouer : un défi
 * rejouable transformerait la simple réouverture de l'application en source
 * de ressources, sans le moindre apprentissage.
 */
class MemoChallengeEngineTest {

    private val heure = 3_600_000L
    private val maintenant = 1_000_000_000L
    private val alea = Random(42)

    private val phrases = listOf(
        "Continue même si c'est lent",
        "Cinq minutes maintenant valent mieux qu'une heure parfaite",
        "La discipline, c'est se souvenir de ce qu'on veut",
        "Fais-le mal, mais fais-le",
        "L'énergie vient en agissant"
    )

    // --- Construction des options ----------------------------------------

    @Test
    fun `la bonne reponse figure toujours parmi les options`() {
        val bonne = phrases[0]
        val options = MemoChallengeEngine.construireOptions(bonne, phrases, alea)
        assertTrue(options.contains(bonne))
    }

    @Test
    fun `le nombre d'options est respecte quand assez de phrases existent`() {
        val options = MemoChallengeEngine.construireOptions(phrases[0], phrases, alea)
        assertEquals(MemoChallengeEngine.NOMBRE_OPTIONS, options.size)
    }

    @Test
    fun `la bonne reponse n'apparait jamais en double`() {
        val bonne = phrases[0]
        val options = MemoChallengeEngine.construireOptions(bonne, phrases, alea)
        assertEquals(1, options.count { it == bonne })
    }

    @Test
    fun `un module pauvre en phrases ne fait pas planter`() {
        val options = MemoChallengeEngine.construireOptions(
            "Seule phrase", listOf("Seule phrase", "Une autre"), alea
        )
        assertTrue(options.contains("Seule phrase"))
        assertTrue(options.size <= MemoChallengeEngine.NOMBRE_OPTIONS)
    }

    @Test
    fun `les doublons du module ne creent pas d'options identiques`() {
        val avecDoublons = listOf("A", "A", "A", "B", "C")
        val options = MemoChallengeEngine.construireOptions("A", avecDoublons, alea)
        assertEquals(options.size, options.distinct().size)
    }

    // --- Rejouabilité -----------------------------------------------------

    @Test
    fun `un defi deja reclame n'est plus proposable`() {
        assertFalse(
            MemoChallengeEngine.estProposable(
                dejaReclame = true,
                envoyeALeMillis = maintenant - heure,
                maintenantMillis = maintenant
            )
        )
    }

    @Test
    fun `un defi recent et non reclame est proposable`() {
        assertTrue(
            MemoChallengeEngine.estProposable(
                dejaReclame = false,
                envoyeALeMillis = maintenant - heure,
                maintenantMillis = maintenant
            )
        )
    }

    @Test
    fun `un defi trop ancien expire`() {
        assertFalse(
            MemoChallengeEngine.estProposable(
                dejaReclame = false,
                envoyeALeMillis = maintenant - (MemoChallengeEngine.VALIDITE_HEURES + 1) * heure,
                maintenantMillis = maintenant
            )
        )
    }

    @Test
    fun `une notification datee dans le futur n'est pas proposable`() {
        // Cas d'une horloge reculée : la trace paraît venir du futur.
        assertFalse(
            MemoChallengeEngine.estProposable(
                dejaReclame = false,
                envoyeALeMillis = maintenant + heure,
                maintenantMillis = maintenant
            )
        )
    }

    // --- Récompense -------------------------------------------------------

    @Test
    fun `une bonne reponse rapporte eau et pieces`() {
        val r = MemoChallengeEngine.recompense(reussi = true)
        assertTrue(r.eau > 0)
        assertTrue(r.pieces > 0)
    }

    @Test
    fun `une erreur ne rapporte rien mais ne retire rien`() {
        val r = MemoChallengeEngine.recompense(reussi = false)
        assertEquals(0, r.eau)
        assertEquals(0, r.pieces)
    }
}
