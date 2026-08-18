package com.sankailife.core.domain.engine

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Alias de type : `val J = ...` ne marche pas sur une classe.
private typealias J = FlashcardEngine.Jugement

class JugementFlashcardTest {

    @Test
    fun `a revoir renvoie au debut, les autres non`() {
        assertEquals(0, FlashcardEngine.boiteSuivante(4, J.A_REVOIR))
        assertTrue(FlashcardEngine.boiteSuivante(4, J.DIFFICILE) > 0)
    }

    @Test
    fun `difficile recule d'un cran sans tout effacer`() {
        // Une carte qu'on retrouve péniblement n'est pas une carte oubliée.
        // La renvoyer au début effacerait tout le travail déjà fait dessus.
        assertEquals(2, FlashcardEngine.boiteSuivante(3, J.DIFFICILE))
        // Mais on ne descend pas sous la première boîte.
        assertEquals(0, FlashcardEngine.boiteSuivante(0, J.DIFFICILE))
    }

    @Test
    fun `facile saute une boite`() {
        // Revoir dans trois jours ce qu'on connaît déjà, c'est du temps pris
        // aux cartes qui en ont besoin.
        assertEquals(3, FlashcardEngine.boiteSuivante(1, J.FACILE))
        assertTrue(
            FlashcardEngine.boiteSuivante(1, J.FACILE) >
                FlashcardEngine.boiteSuivante(1, J.CORRECT)
        )
    }

    @Test
    fun `aucune boite ne sort de l'intervalle`() {
        for (boite in 0 until FlashcardEngine.NOMBRE_BOITES) {
            for (j in J.entries) {
                val suivante = FlashcardEngine.boiteSuivante(boite, j)
                assertTrue(
                    "boite $boite / $j -> $suivante",
                    suivante in 0 until FlashcardEngine.NOMBRE_BOITES
                )
            }
        }
    }

    @Test
    fun `les quatre jugements donnent quatre boites distinctes en milieu de parcours`() {
        // C'est tout l'intérêt du glissement : porter une nuance que deux
        // boutons ne pouvaient pas exprimer.
        val boites = J.entries.map { FlashcardEngine.boiteSuivante(2, it) }
        assertEquals(4, boites.toSet().size)
    }

    @Test
    fun `a revoir ramene la carte dans la meme session`() {
        val maintenant = 1_000_000L
        val bientot = FlashcardEngine.prochaineRevision(0, J.A_REVOIR, maintenant)
        val normal = FlashcardEngine.prochaineRevision(0, J.CORRECT, maintenant)
        assertTrue("la carte ratee doit revenir vite", bientot < normal)
        assertTrue(bientot - maintenant <= 60_000L * 2)
    }

    @Test
    fun `seul a revoir compte comme un echec`() {
        assertTrue(!J.A_REVOIR.reussi)
        assertTrue(J.DIFFICILE.reussi)
        assertTrue(J.CORRECT.reussi)
        assertTrue(J.FACILE.reussi)
    }

    @Test
    fun `les intervalles suivent la progression 1-3-7-14-30 jours`() {
        // La mission de Sankai : une carte revient demain, dans 3 jours, dans
        // 7, dans 14, puis dans 30. Les paliers doivent être strictement
        // croissants — une répétition espacée qui rapprocherait les échéances
        // n'espacerait rien.
        val maintenant = 0L
        val echeances = (0 until FlashcardEngine.NOMBRE_BOITES).map {
            FlashcardEngine.prochaineRevision(it, maintenant)
        }
        assertEquals(FlashcardEngine.NOMBRE_BOITES, echeances.size)
        for (i in 1 until echeances.size) {
            assertTrue("palier $i non croissant", echeances[i] > echeances[i - 1])
        }
        // Le dernier palier est un mois, pas une durée au hasard : c'est la
        // maîtrise, et c'est l'échéance la plus lointaine.
        assertEquals(
            TimeUnit.DAYS.toMillis(30),
            echeances.last() - maintenant
        )
    }

    @Test
    fun `la maitrise est la derniere boite`() {
        // Six boîtes de 0 à 5 : la maîtrise est la boîte 5, et plus aucune
        // réponse ne fait déborder une carte hors du système.
        assertEquals(5, FlashcardEngine.NOMBRE_BOITES - 1)
        assertEquals(
            5,
            FlashcardEngine.boiteSuivante(5, FlashcardEngine.Jugement.CORRECT)
        )
        assertEquals(
            5,
            FlashcardEngine.boiteSuivante(4, FlashcardEngine.Jugement.FACILE)
        )
    }

    @Test
    fun `l'ancienne signature booleenne reste coherente`() {
        // Elle est encore appelée par les exercices corrigés automatiquement.
        assertEquals(
            FlashcardEngine.boiteSuivante(2, J.CORRECT),
            FlashcardEngine.boiteSuivante(2, reussi = true)
        )
        assertEquals(
            FlashcardEngine.boiteSuivante(2, J.A_REVOIR),
            FlashcardEngine.boiteSuivante(2, reussi = false)
        )
    }
}
