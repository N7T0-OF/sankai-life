package com.sankailife.core.island.domain

import com.sankailife.core.island.domain.AutotuilageEngine.Couche
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutotuilageEngineTest {

    /** Terrain d'essai decrit en texte : ~ eau profonde, - eau basse, . sable, # terre. */
    private fun terrain(vararg lignes: String): (Int, Int) -> IslandTileType {
        val table = mapOf(
            '~' to IslandTileType.DEEP_WATER,
            '-' to IslandTileType.SHALLOW_WATER,
            '.' to IslandTileType.BEACH,
            '#' to IslandTileType.GRASS
        )
        return { x, y ->
            if (y !in lignes.indices || x !in lignes[y].indices) IslandTileType.DEEP_WATER
            else table[lignes[y][x]] ?: IslandTileType.DEEP_WATER
        }
    }

    // --- Couches --------------------------------------------------------------

    @Test
    fun `l'ordre des couches suit la cote`() {
        // Le sable borde l'eau, l'herbe borde le sable. L'inverser ferait
        // deborder l'ocean sur la plage.
        assertTrue(Couche.PROFOND.niveau < Couche.BASSE.niveau)
        assertTrue(Couche.BASSE.niveau < Couche.SABLE.niveau)
        assertTrue(Couche.SABLE.niveau < Couche.TERRE.niveau)
    }

    @Test
    fun `chaque type de terrain a une couche`() {
        // Un type oublie retomberait sur la terre et se peindrait par-dessus
        // l'ocean : le defaut se verrait, mais loin de sa cause.
        IslandTileType.entries.forEach { type ->
            assertTrue("$type sans couche", AutotuilageEngine.couche(type).niveau in 0..3)
        }
    }

    @Test
    fun `l'eau est toujours sous la terre`() {
        IslandTileType.entries.filter { it.estEau }.forEach { eau ->
            IslandTileType.entries.filter { it.estTerre }.forEach { terre ->
                assertTrue(
                    "$eau devrait passer sous $terre",
                    AutotuilageEngine.couche(eau).niveau <= AutotuilageEngine.couche(terre).niveau
                )
            }
        }
    }

    // --- Codes ----------------------------------------------------------------

    @Test
    fun `une case entouree des siens porte le code plein`() {
        val t = terrain(
            "###",
            "###",
            "###"
        )
        assertEquals(15, AutotuilageEngine.code(1, 1, Couche.TERRE, t))
    }

    @Test
    fun `une case isolee ne porte aucun voisin`() {
        val t = terrain(
            "~~~",
            "~#~",
            "~~~"
        )
        assertEquals(0, AutotuilageEngine.code(1, 1, Couche.TERRE, t))
    }

    @Test
    fun `chaque direction met son propre bit`() {
        assertEquals(
            AutotuilageEngine.NORD,
            AutotuilageEngine.code(1, 1, Couche.TERRE, terrain("~#~", "~#~", "~~~"))
        )
        assertEquals(
            AutotuilageEngine.SUD,
            AutotuilageEngine.code(1, 1, Couche.TERRE, terrain("~~~", "~#~", "~#~"))
        )
        assertEquals(
            AutotuilageEngine.EST,
            AutotuilageEngine.code(1, 1, Couche.TERRE, terrain("~~~", "~##", "~~~"))
        )
        assertEquals(
            AutotuilageEngine.OUEST,
            AutotuilageEngine.code(1, 1, Couche.TERRE, terrain("~~~", "##~", "~~~"))
        )
    }

    @Test
    fun `un voisin de niveau superieur compte comme present`() {
        // Sinon l'herbe s'arreterait net au bord du sable alors qu'elle doit se
        // poser dessus, et une ligne d'eau apparaitrait entre les deux.
        val t = terrain(
            "~#~",
            "~.~",
            "~~~"
        )
        val code = AutotuilageEngine.code(1, 1, Couche.SABLE, t)
        assertTrue("Le voisin d'herbe devrait compter", code and AutotuilageEngine.NORD != 0)
    }

    @Test
    fun `un voisin de niveau inferieur ne compte pas`() {
        val t = terrain(
            "~.~",
            "~#~",
            "~~~"
        )
        val code = AutotuilageEngine.code(1, 1, Couche.TERRE, t)
        assertEquals("Le sable ne devrait pas porter l'herbe", 0, code and AutotuilageEngine.NORD)
    }

    @Test
    fun `le code reste dans les seize masques`() {
        val t = terrain("#.#", ".-.", "#.#")
        (0..2).forEach { y ->
            (0..2).forEach { x ->
                AutotuilageEngine.COUCHES_SUPERIEURES.forEach { couche ->
                    val c = AutotuilageEngine.code(x, y, couche, t)
                    assertTrue("Code $c hors bornes", c in 0 until AutotuilageEngine.MASQUES)
                }
            }
        }
    }

    @Test
    fun `hors de la carte le terrain se comporte comme une cote`() {
        // Les bords de la carte sont de l'ocean : une ile qui touche le bord
        // doit y avoir une cote, pas une coupure nette.
        val t = terrain("##", "##")
        assertEquals(
            AutotuilageEngine.EST or AutotuilageEngine.SUD,
            AutotuilageEngine.code(0, 0, Couche.TERRE, t)
        )
    }

    // --- Cases concernees -----------------------------------------------------

    @Test
    fun `une case d'herbe porte aussi les couches inferieures`() {
        // Sans cela, on verrait de l'eau a travers les bords irreguliers de
        // l'herbe : le sable qu'elle recouvre n'aurait pas ete peint.
        assertTrue(AutotuilageEngine.concernee(IslandTileType.GRASS, Couche.SABLE))
        assertTrue(AutotuilageEngine.concernee(IslandTileType.GRASS, Couche.BASSE))
        assertTrue(AutotuilageEngine.concernee(IslandTileType.GRASS, Couche.TERRE))
    }

    @Test
    fun `l'eau profonde ne porte aucune couche superieure`() {
        AutotuilageEngine.COUCHES_SUPERIEURES.forEach { couche ->
            assertFalse(AutotuilageEngine.concernee(IslandTileType.DEEP_WATER, couche))
        }
    }

    @Test
    fun `le sable ne porte pas la terre`() {
        assertTrue(AutotuilageEngine.concernee(IslandTileType.BEACH, Couche.SABLE))
        assertFalse(AutotuilageEngine.concernee(IslandTileType.BEACH, Couche.TERRE))
    }
}
