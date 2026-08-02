package com.sankailife.core.island.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecolteRapideEngineTest {

    private fun prete(x: Int, y: Int, graine: String = "tournesol", largeur: Int = 64) =
        RecolteRapideEngine.Prete(cle = y * largeur + x, x = x, y = y, graineId = graine)

    // --- Regroupement ---------------------------------------------------------

    @Test
    fun `rien de mur ne donne aucune bulle`() {
        assertTrue(RecolteRapideEngine.bulles(emptyList()).isEmpty())
    }

    @Test
    fun `une plante isolee donne une bulle d'une seule case`() {
        val bulles = RecolteRapideEngine.bulles(listOf(prete(5, 5)))
        assertEquals(1, bulles.size)
        assertEquals(1, bulles.single().quantite)
        assertFalse(bulles.single().groupee)
    }

    @Test
    fun `un champ de la meme espece donne une seule bulle`() {
        // C'est tout l'objet : huit tournesols muers affichaient huit bulles et
        // demandaient huit fiches.
        val champ = (0..3).flatMap { y -> (0..1).map { x -> prete(x, y) } }
        val bulles = RecolteRapideEngine.bulles(champ)
        assertEquals(1, bulles.size)
        assertEquals(8, bulles.single().quantite)
    }

    @Test
    fun `deux especes ne se melangent jamais`() {
        // Melanger ramasserait une plante qu'on gardait peut-etre pour autre
        // chose, et l'appui ne serait plus lisible.
        val melange = listOf(prete(0, 0, "tournesol"), prete(1, 0, "menthe"))
        val bulles = RecolteRapideEngine.bulles(melange)
        assertEquals(2, bulles.size)
        assertEquals(setOf("tournesol", "menthe"), bulles.map { it.graineId }.toSet())
    }

    @Test
    fun `deux champs eloignes restent deux bulles`() {
        val loin = listOf(prete(0, 0), prete(20, 20))
        assertEquals(2, RecolteRapideEngine.bulles(loin).size)
    }

    @Test
    fun `le regroupement est transitif`() {
        // Deux parcelles eloignees reliees par une troisieme forment un seul
        // champ, ce qui correspond a ce qu'on voit.
        val chaine = listOf(prete(0, 0), prete(2, 0), prete(4, 0))
        val bulles = RecolteRapideEngine.bulles(chaine)
        assertEquals(1, bulles.size)
        assertEquals(3, bulles.single().quantite)
    }

    @Test
    fun `la portee limite le regroupement`() {
        val juste = listOf(prete(0, 0), prete(RecolteRapideEngine.PORTEE, 0))
        assertEquals(1, RecolteRapideEngine.bulles(juste).size)

        val trop = listOf(prete(0, 0), prete(RecolteRapideEngine.PORTEE + 1, 0))
        assertEquals(2, RecolteRapideEngine.bulles(trop).size)
    }

    @Test
    fun `sans regroupement chaque plante a sa bulle`() {
        val champ = (0..3).map { prete(it, 0) }
        assertEquals(4, RecolteRapideEngine.bulles(champ, groupe = false).size)
    }

    @Test
    fun `aucune parcelle n'est oubliee ni comptee deux fois`() {
        // Une parcelle absente serait une recolte perdue ; une parcelle en
        // double serait une recolte creditee deux fois.
        val champ = (0..4).flatMap { y -> (0..4).map { x -> prete(x, y) } } +
            (0..2).map { prete(it + 20, 0, "menthe") }
        val cles = RecolteRapideEngine.bulles(champ).flatMap { it.cles }
        assertEquals(champ.size, cles.size)
        assertEquals(champ.map { it.cle }.toSet(), cles.toSet())
    }

    @Test
    fun `la bulle s'ancre sur la parcelle la plus haute a gauche`() {
        // Un point d'ancrage stable : sinon elle sauterait d'une case a l'autre
        // a chaque recolte partielle.
        val champ = listOf(prete(3, 3), prete(2, 2), prete(3, 2))
        val bulle = RecolteRapideEngine.bulles(champ).single()
        assertEquals(2, bulle.x)
        assertEquals(2, bulle.y)
    }

    @Test
    fun `le regroupement est deterministe`() {
        val champ = (0..3).flatMap { y -> (0..3).map { x -> prete(x, y) } }
        assertEquals(
            RecolteRapideEngine.bulles(champ),
            RecolteRapideEngine.bulles(champ.shuffled())
        )
    }

    // --- Toucher --------------------------------------------------------------

    @Test
    fun `toucher la case de la bulle la selectionne`() {
        val bulles = RecolteRapideEngine.bulles(listOf(prete(5, 5)))
        assertNotNull(RecolteRapideEngine.bulleTouchee(bulles, 5, 5))
    }

    @Test
    fun `toucher juste sous la bulle la selectionne aussi`() {
        // La bulle est dessinee au-dessus de la plante : on vise ce qu'on voit.
        val bulles = RecolteRapideEngine.bulles(listOf(prete(5, 5)))
        assertNotNull(RecolteRapideEngine.bulleTouchee(bulles, 5, 4))
    }

    @Test
    fun `toucher a cote ne selectionne rien`() {
        // Une zone trop large ferait recolter en voulant toucher la parcelle
        // voisine.
        val bulles = RecolteRapideEngine.bulles(listOf(prete(5, 5)))
        assertNull(RecolteRapideEngine.bulleTouchee(bulles, 6, 5))
        assertNull(RecolteRapideEngine.bulleTouchee(bulles, 5, 7))
        assertNull(RecolteRapideEngine.bulleTouchee(bulles, 4, 5))
    }

    // --- Portee d'un appui ----------------------------------------------------

    @Test
    fun `par defaut on ne recolte que le groupe touche`() {
        val champs = (0..1).map { prete(it, 0) } + (0..1).map { prete(it + 20, 0) }
        val bulles = RecolteRapideEngine.bulles(champs)
        val touchee = bulles.first()
        val cles = RecolteRapideEngine.aRecolter(
            touchee, bulles, RecolteRapideEngine.Portee.GROUPE
        )
        assertEquals(2, cles.size)
        assertEquals(touchee.cles.toSet(), cles.toSet())
    }

    @Test
    fun `la portee zone ne ramasse que ce qui est a l'ecran`() {
        val champs = (0..1).map { prete(it, 0) } + (0..1).map { prete(it + 20, 0) }
        val bulles = RecolteRapideEngine.bulles(champs)
        val cles = RecolteRapideEngine.aRecolter(
            bulles.first(), bulles, RecolteRapideEngine.Portee.ZONE
        ) { x, _ -> x < 10 }
        assertEquals(2, cles.size)
    }

    @Test
    fun `la portee tout ramasse l'espece entiere`() {
        val champs = (0..1).map { prete(it, 0) } + (0..1).map { prete(it + 20, 0) }
        val bulles = RecolteRapideEngine.bulles(champs)
        val cles = RecolteRapideEngine.aRecolter(
            bulles.first(), bulles, RecolteRapideEngine.Portee.TOUT
        )
        assertEquals(4, cles.size)
    }

    @Test
    fun `aucune portee ne ramasse une autre espece`() {
        val melange = listOf(prete(0, 0, "tournesol"), prete(20, 0, "menthe"))
        val bulles = RecolteRapideEngine.bulles(melange)
        val tournesol = bulles.first { it.graineId == "tournesol" }
        RecolteRapideEngine.Portee.entries.forEach { portee ->
            val cles = RecolteRapideEngine.aRecolter(tournesol, bulles, portee)
            assertTrue(
                "$portee ramasse une autre espece",
                cles.all { it in melange.filter { p -> p.graineId == "tournesol" }.map { p -> p.cle } }
            )
        }
    }

    @Test
    fun `aucune cle n'est rendue deux fois`() {
        val champ = (0..3).map { prete(it, 0) }
        val bulles = RecolteRapideEngine.bulles(champ)
        val cles = RecolteRapideEngine.aRecolter(
            bulles.first(), bulles, RecolteRapideEngine.Portee.TOUT
        )
        assertEquals(cles.size, cles.toSet().size)
    }

    // --- Resume ---------------------------------------------------------------

    @Test
    fun `le resume donne le compte exact`() {
        // Sans compte, on ne sait pas si l'appui a fait ce qu'on croyait.
        assertEquals("Tournesol récolté", RecolteRapideEngine.resume("Tournesol", 1))
        assertEquals("8 × Tournesol récoltés", RecolteRapideEngine.resume("Tournesol", 8))
    }
}
