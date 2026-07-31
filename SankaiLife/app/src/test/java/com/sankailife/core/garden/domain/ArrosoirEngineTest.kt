package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrosoirEngineTest {

    private val centre = ExpansionEngine.cle(ExpansionEngine.CENTRE, ExpansionEngine.CENTRE)

    @Test
    fun `la zone grandit avec le niveau`() {
        val tailles = (1..ArrosoirEngine.NIVEAU_MAX).map {
            ArrosoirEngine.zone(it, centre).size
        }
        assertEquals(listOf(1, 3, 4, 9), tailles)
    }

    @Test
    fun `la case visee est toujours couverte`() {
        // Sinon viser une parcelle assoiffée pourrait l'oublier, ce qui serait
        // incompréhensible pour le joueur.
        for (niveau in 1..ArrosoirEngine.NIVEAU_MAX) {
            assertTrue(ArrosoirEngine.zone(niveau, centre).contains(centre))
        }
    }

    @Test
    fun `la zone ne contient jamais deux fois la meme case`() {
        for (niveau in 1..ArrosoirEngine.NIVEAU_MAX) {
            val zone = ArrosoirEngine.zone(niveau, centre)
            assertEquals(zone.size, zone.toSet().size)
        }
    }

    @Test
    fun `la zone ne deborde pas de la grille`() {
        // Au coin, une zone 3 × 3 déborderait de deux côtés. Sans filtre, les
        // coordonnées négatives réapparaîtraient à l'autre bout du plan.
        val coin = ExpansionEngine.cle(0, 0)
        val zone = ArrosoirEngine.zone(ArrosoirEngine.NIVEAU_MAX, coin)
        assertEquals(4, zone.size)
        assertTrue(
            zone.all {
                ExpansionEngine.xDe(it) in 0 until ExpansionEngine.COTE &&
                    ExpansionEngine.yDe(it) in 0 until ExpansionEngine.COTE
            }
        )
    }

    @Test
    fun `un niveau hors bornes se rabat sur un niveau valide`() {
        assertEquals(
            ArrosoirEngine.zone(1, centre),
            ArrosoirEngine.zone(0, centre)
        )
        assertEquals(
            ArrosoirEngine.zone(ArrosoirEngine.NIVEAU_MAX, centre),
            ArrosoirEngine.zone(99, centre)
        )
    }

    @Test
    fun `le prix des ameliorations monte, puis s'arrete`() {
        val prix = (1 until ArrosoirEngine.NIVEAU_MAX).map {
            ArrosoirEngine.coutAmelioration(it)!!
        }
        assertEquals(prix.sorted(), prix)
        // Au dernier niveau, il n'y a plus rien à vendre.
        assertNull(ArrosoirEngine.coutAmelioration(ArrosoirEngine.NIVEAU_MAX))
    }

    @Test
    fun `chaque niveau a un libelle et une description`() {
        for (niveau in 1..ArrosoirEngine.NIVEAU_MAX) {
            assertTrue(ArrosoirEngine.libelle(niveau).isNotBlank())
            assertTrue(ArrosoirEngine.description(niveau).isNotBlank())
        }
    }
}
