package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErreursEngineTest {

    private fun carte(
        id: Long = 1L,
        boite: Int = 0,
        revisions: Int = 10,
        reussites: Int = 5
    ) = ErreursEngine.Historique(id, "Olá | Bonjour", boite, revisions, reussites)

    @Test
    fun `une carte neuve n'est jamais declaree difficile`() {
        // Rater une carte vue une seule fois n'apprend rien : c'est le cas
        // normal d'une carte qu'on découvre. Sans ce seuil, « Mes erreurs » se
        // remplirait de cartes qu'on vient de créer.
        assertFalse(ErreursEngine.estDifficile(carte(revisions = 1, reussites = 0)))
        assertFalse(ErreursEngine.estDifficile(carte(revisions = 2, reussites = 0)))
        assertTrue(ErreursEngine.estDifficile(carte(revisions = 3, reussites = 0)))
    }

    @Test
    fun `une carte bien sue n'est pas difficile`() {
        assertFalse(ErreursEngine.estDifficile(carte(revisions = 10, reussites = 9)))
    }

    @Test
    fun `le taux d'echec ne depasse jamais les bornes`() {
        // Une incoherence en base — plus de reussites que de revisions — ne doit
        // pas produire un taux negatif qui remonterait la carte en tete.
        assertEquals(0f, carte(revisions = 5, reussites = 9).tauxEchec, 0.001f)
        assertEquals(1f, carte(revisions = 5, reussites = 0).tauxEchec, 0.001f)
        assertEquals(0f, carte(revisions = 0, reussites = 0).tauxEchec, 0.001f)
    }

    @Test
    fun `a taux egal, le nombre d'echecs departage`() {
        // Entre deux cartes ratées une fois sur deux, celle ratée dix fois pèse
        // plus lourd que celle ratée deux fois.
        val beaucoup = carte(id = 1, revisions = 20, reussites = 10)
        val peu = carte(id = 2, revisions = 4, reussites = 2)
        assertTrue(ErreursEngine.priorite(beaucoup) > ErreursEngine.priorite(peu))
    }

    @Test
    fun `une carte jamais montee passe devant une carte redescendue`() {
        val jamaisMontee = carte(id = 1, boite = 0, revisions = 10, reussites = 5)
        val redescendue = carte(id = 2, boite = 3, revisions = 10, reussites = 5)
        assertTrue(ErreursEngine.priorite(jamaisMontee) > ErreursEngine.priorite(redescendue))
    }

    @Test
    fun `une carte non difficile a une priorite nulle`() {
        assertEquals(0f, ErreursEngine.priorite(carte(revisions = 10, reussites = 10)), 0.001f)
        assertEquals(0f, ErreursEngine.priorite(carte(revisions = 1, reussites = 0)), 0.001f)
    }

    @Test
    fun `la selection est bornee et triee`() {
        // Proposer cent cartes ratées d'un coup ne serait pas une aide, ce
        // serait un constat d'échec.
        val cartes = (1..50).map { carte(id = it.toLong(), revisions = 10, reussites = 10 - it % 9) }
        val choisies = ErreursEngine.selectionner(cartes)

        assertTrue(choisies.size <= ErreursEngine.CARTES_PAR_SESSION)
        val priorites = choisies.map { ErreursEngine.priorite(it) }
        assertEquals(priorites.sortedDescending(), priorites)
        assertTrue(choisies.all { ErreursEngine.estDifficile(it) })
    }

    @Test
    fun `une liste sans difficulte ne renvoie rien`() {
        val faciles = (1..20).map { carte(id = it.toLong(), revisions = 10, reussites = 10) }
        assertTrue(ErreursEngine.selectionner(faciles).isEmpty())
    }

    @Test
    fun `le resume se tait quand il n'y a rien a dire`() {
        // Afficher « 0 erreur » à quelqu'un qui débute lui ferait croire qu'il
        // a déjà tout révisé.
        assertNull(ErreursEngine.resume(0))
        assertNull(ErreursEngine.resume(-3))
        assertTrue(ErreursEngine.resume(1)!!.contains("1 carte"))
        assertTrue(ErreursEngine.resume(7)!!.contains("7 cartes"))
    }
}
