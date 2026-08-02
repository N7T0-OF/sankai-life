package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.ArbreSankaiEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandForetEngineTest {

    /** Grille d'essai décrite en texte : `T` = bois. */
    private fun grille(vararg lignes: String): Triple<Int, Int, (Int, Int) -> Boolean> {
        val h = lignes.size
        val l = lignes.maxOf { it.length }
        return Triple(l, h, { x: Int, y: Int ->
            y in lignes.indices && x < lignes[y].length && lignes[y][x] == 'T'
        })
    }

    private fun decouper(vararg lignes: String): List<IslandForetEngine.Arbre> {
        val (l, h, bois) = grille(*lignes)
        return IslandForetEngine.decouper(l, h, bois)
    }

    // --- Couverture -----------------------------------------------------------

    @Test
    fun `aucune case boisee n'est laissee de cote`() {
        // Une case oubliee laisserait un trou vert au milieu d'une foret.
        val arbres = decouper(
            "TTT",
            "TT.",
            ".TT"
        )
        val couvertes = arbres.flatMap {
            ArbreSankaiEngine.casesOccupees(ArbreSankaiEngine.Case(it.x, it.y), it.taille)
        }.map { it.x to it.y }.toSet()

        val attendues = setOf(
            0 to 0, 1 to 0, 2 to 0,
            0 to 1, 1 to 1,
            1 to 2, 2 to 2
        )
        assertTrue("Cases non couvertes : ${attendues - couvertes}", couvertes.containsAll(attendues))
    }

    @Test
    fun `aucun arbre ne deborde sur une case non boisee`() {
        val arbres = decouper(
            "TT.",
            "TT.",
            "..."
        )
        val couvertes = arbres.flatMap {
            ArbreSankaiEngine.casesOccupees(ArbreSankaiEngine.Case(it.x, it.y), it.taille)
        }.map { it.x to it.y }.toSet()
        assertTrue("Debordement hors du bois", couvertes.all { (x, y) -> x < 2 && y < 2 })
    }

    @Test
    fun `deux arbres ne se recouvrent jamais`() {
        val arbres = decouper(
            "TTTT",
            "TTTT",
            "TTTT",
            "TTTT"
        )
        val toutes = arbres.flatMap {
            ArbreSankaiEngine.casesOccupees(ArbreSankaiEngine.Case(it.x, it.y), it.taille)
        }
        assertEquals("Une case est prise deux fois", toutes.size, toutes.toSet().size)
    }

    // --- Agglomeration --------------------------------------------------------

    @Test
    fun `un bosquet carre donne un grand arbre`() {
        // Dessiner quatre arbres identiques cote a cote donnerait une haie
        // reguliere, qui se lit comme un motif plutot que comme une foret.
        val arbres = decouper(
            "TT",
            "TT"
        )
        assertEquals(1, arbres.size)
        assertEquals(ArbreSankaiEngine.Taille.QUATRE, arbres.single().taille)
    }

    @Test
    fun `deux cases cote a cote donnent un arbre double`() {
        val arbres = decouper("TT")
        assertEquals(1, arbres.size)
        assertEquals(ArbreSankaiEngine.Taille.DEUX, arbres.single().taille)
    }

    @Test
    fun `une case isolee donne un petit arbre`() {
        val arbres = decouper(
            "T.",
            ".."
        )
        assertEquals(1, arbres.size)
        assertEquals(ArbreSankaiEngine.Taille.UNE, arbres.single().taille)
    }

    @Test
    fun `les grands blocs sont formes avant les petits`() {
        // L'ordre du balayage n'est pas une optimisation : commencer par les
        // petites laisserait des cases orphelines et aucun grand arbre ne
        // pourrait plus se former.
        val arbres = decouper(
            "TTT",
            "TTT"
        )
        assertTrue(
            "Aucun grand arbre forme",
            arbres.any { it.taille == ArbreSankaiEngine.Taille.QUATRE }
        )
    }

    // --- Stabilite ------------------------------------------------------------

    @Test
    fun `le decoupage est deterministe`() {
        // Sinon le feuillage changerait de place a chaque rendu.
        val a = decouper("TTT", "T.T", "TTT")
        val b = decouper("TTT", "T.T", "TTT")
        assertEquals(a, b)
    }

    @Test
    fun `les arbres sont tries par profondeur`() {
        // Un arbre du bas doit passer devant celui du haut, sinon un feuillage
        // lointain recouvre un tronc proche.
        val arbres = decouper("T.T", "...", "T.T")
        val y = arbres.map { it.y }
        assertEquals(y.sorted(), y)
    }

    @Test
    fun `l'echelle varie d'un arbre a l'autre mais reste stable`() {
        val a = IslandForetEngine.echelle(3, 7)
        assertEquals(a, IslandForetEngine.echelle(3, 7), 0.0001f)
        assertTrue("Echelle hors bornes : $a", a in 0.9f..1.15f)

        val differentes = (0 until 40).map { IslandForetEngine.echelle(it, it * 3) }.toSet()
        assertTrue("Toutes les echelles identiques", differentes.size > 5)
    }

    // --- Cas limites ----------------------------------------------------------

    @Test
    fun `une grille sans bois ne donne aucun arbre`() {
        assertTrue(decouper("...", "...").isEmpty())
    }

    @Test
    fun `une grille vide ne fait pas planter`() {
        assertTrue(IslandForetEngine.decouper(0, 0) { _, _ -> true }.isEmpty())
        assertTrue(IslandForetEngine.decouper(-3, 5) { _, _ -> true }.isEmpty())
    }
}
