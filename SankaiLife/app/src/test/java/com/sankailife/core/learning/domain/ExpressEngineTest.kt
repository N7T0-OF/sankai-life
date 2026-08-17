package com.sankailife.core.learning.domain

import com.sankailife.core.domain.engine.ErreursEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressEngineTest {

    private fun carte(
        id: Long,
        revisions: Int = 0,
        reussites: Int = 0,
        prochaine: Long = 0L,
        boite: Int = 0
    ) = ExpressEngine.Carte(
        id = id, texte = "q | r", boite = boite,
        revisions = revisions, reussites = reussites,
        prochaineRevisionMillis = prochaine, profileId = 1L
    )

    private val maintenant = 1_000_000L

    @Test
    fun `une session vide reste vide`() {
        assertTrue(ExpressEngine.composer(emptyList(), maintenant).isEmpty())
    }

    @Test
    fun `les cartes difficiles passent en premier`() {
        // Deux cartes nettement difficiles : beaucoup de révisions, beaucoup
        // d'échecs — bien au-dessus du seuil d'ErreursEngine.
        val d1 = carte(1, revisions = 10, reussites = 3)
        val d2 = carte(2, revisions = 8, reussites = 2)
        val facile = carte(3, revisions = 10, reussites = 9)
        val session = ExpressEngine.composer(listOf(facile, d1, d2), maintenant)

        assertEquals(d1.id, session[0].id)
        assertEquals(d2.id, session[1].id)
        // La carte facile n'entre pas dans la sélection si elle n'est pas
        // échue : rien à réviser, elle n'a pas sa place dans l'express.
        assertTrue(session.none { it.id == facile.id })
    }

    @Test
    fun `la carte echeue la plus ancienne est choisie`() {
        val ancienne = carte(1, revisions = 2, reussites = 2, prochaine = 100L)
        val recente = carte(2, revisions = 2, reussites = 2, prochaine = 500L)
        val session = ExpressEngine.composer(listOf(recente, ancienne), maintenant)

        assertEquals(ancienne.id, session.first().id)
    }

    @Test
    fun `une carte jamais revisee entre dans la session`() {
        val nouvelle = carte(1) // revisions = 0
        val due = carte(2, revisions = 2, reussites = 1, prochaine = 50L)
        val session = ExpressEngine.composer(listOf(due, nouvelle), maintenant)

        assertEquals(2, session.size)
        assertTrue(session.any { it.id == nouvelle.id })
    }

    @Test
    fun `la session ne depasse jamais la taille annoncee`() {
        val cartes = (0 until 20).map { carte(it.toLong()) }
        val session = ExpressEngine.composer(cartes, maintenant)
        assertEquals(ExpressEngine.TAILLE_SESSION, session.size)
    }

    @Test
    fun `aucune carte n'est repetee dans la session`() {
        val cartes = (0 until 8).map { carte(it.toLong()) }
        val session = ExpressEngine.composer(cartes, maintenant)
        assertEquals(session.size, session.map { it.id }.toSet().size)
    }

    @Test
    fun `deterministe a cartes egales`() {
        val cartes = (0 until 6).map { carte(it.toLong(), revisions = it, reussites = 1) }
        val une = ExpressEngine.composer(cartes, maintenant)
        val deux = ExpressEngine.composer(cartes, maintenant)
        assertEquals(une.map { it.id }, deux.map { it.id })
    }

    @Test
    fun `les cartes difficiles restent en tete meme quand tout est echu`() {
        val d1 = carte(1, revisions = 10, reussites = 3, prochaine = 10L)
        val d2 = carte(2, revisions = 8, reussites = 2, prochaine = 20L)
        val dues = (3..6).map { carte(it.toLong(), revisions = 1, reussites = 1, prochaine = 30L) }
        val session = ExpressEngine.composer(listOf(d1, d2) + dues, maintenant)

        assertTrue(session.indexOfFirst { it.id == d1.id } < session.indexOfLast { it.id == d2.id })
        // Les deux difficiles sont présentes en tête, avant la plus ancienne échue.
        assertTrue(session.take(2).map { it.id }.containsAll(listOf(d1.id, d2.id)))
    }

    @Test
    fun `le seuil de difficulte suit ErreursEngine`() {
        // Une carte avec 3 révisions et 1 échec est à 33 % d'échec, sous le
        // seuil de 34 % : elle n'est pas « difficile » pour le moteur.
        val pasTresDifficile = carte(1, revisions = 3, reussites = 2, prochaine = 10L)
        val tresDifficile = carte(2, revisions = 3, reussites = 1, prochaine = 20L)
        val session = ExpressEngine.composer(listOf(pasTresDifficile, tresDifficile), maintenant)

        assertTrue(session.indexOfFirst { it.id == tresDifficile.id } == 0)
        assertTrue(ErreursEngine.estDifficile(
            ErreursEngine.Historique(
                id = 1, texte = "", boite = 0, revisions = 3, reussites = 2
            )
        ).not())
    }
}
