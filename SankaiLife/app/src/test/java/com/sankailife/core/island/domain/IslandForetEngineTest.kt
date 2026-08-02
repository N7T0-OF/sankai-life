package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.ArbreSankaiEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- Feuillage : le defaut signale apres usage -----------------------------

    @Test
    fun `la case au-dessus d'un arbre est reservee`() {
        // Le defaut : un arbre ne reservait que son emprise, alors que sa
        // couronne recouvre largement la case du dessus. Elle restait achetable
        // et cultivable, donc on pouvait y semer sans jamais voir pousser.
        val arbre = IslandForetEngine.Arbre(5, 5, ArbreSankaiEngine.Taille.UNE)
        val masquees = IslandForetEngine.casesMasquees(arbre)
        assertTrue("La case du dessus devrait etre masquee", (5 to 4) in masquees)
    }

    @Test
    fun `la case sous le tronc reste libre`() {
        // Le bas du dessin n'est qu'un tronc etroit : rien n'y est cache.
        val arbre = IslandForetEngine.Arbre(5, 5, ArbreSankaiEngine.Taille.UNE)
        assertFalse((5 to 6) in IslandForetEngine.casesMasquees(arbre))
    }

    @Test
    fun `une case eloignee n'est jamais reservee`() {
        val arbre = IslandForetEngine.Arbre(5, 5, ArbreSankaiEngine.Taille.UNE)
        val masquees = IslandForetEngine.casesMasquees(arbre)
        assertFalse((5 to 0) in masquees)
        assertFalse((12 to 5) in masquees)
    }

    @Test
    fun `un grand arbre masque davantage qu'un petit`() {
        val petit = IslandForetEngine.casesMasquees(
            IslandForetEngine.Arbre(10, 10, ArbreSankaiEngine.Taille.UNE)
        )
        val grand = IslandForetEngine.casesMasquees(
            IslandForetEngine.Arbre(10, 10, ArbreSankaiEngine.Taille.QUATRE)
        )
        assertTrue("Un arbre 4 cases devrait masquer plus", grand.size > petit.size)
    }

    @Test
    fun `l'emprise n'est jamais comptee deux fois`() {
        val arbre = IslandForetEngine.Arbre(3, 3, ArbreSankaiEngine.Taille.QUATRE)
        val emprise = ArbreSankaiEngine
            .casesOccupees(ArbreSankaiEngine.Case(3, 3), ArbreSankaiEngine.Taille.QUATRE)
            .map { it.x to it.y }.toSet()
        val masquees = IslandForetEngine.casesMasquees(arbre)
        assertTrue("Emprise et masquage se recouvrent", masquees.intersect(emprise).isEmpty())
    }

    @Test
    fun `les cases reservees contiennent toujours l'emprise`() {
        val arbres = decouper("T.", "..")
        val reservees = IslandForetEngine.casesReservees(arbres)
        assertTrue("L'emprise doit etre reservee", (0 to 0) in reservees)
    }

    @Test
    fun `une couverture d'exactement la moitie ne reserve pas`() {
        // Cas limite reel, trouve en ecrivant les tests : a l'echelle minimale,
        // la couronne d'un arbre d'une case couvre exactement la moitie de la
        // case au-dessus. La regle dit « plus de la moitie », donc elle reste
        // libre — et c'est defendable : a moitie cachee, on voit encore ce
        // qu'on y fait.
        //
        // Ce test existe pour que le seuil ne derive pas en silence.
        val minimal = IslandForetEngine.Arbre(0, 0, ArbreSankaiEngine.Taille.UNE)
        assertFalse((0 to -1) in IslandForetEngine.casesMasquees(minimal))

        // Le meme arbre place la ou l'echelle est plus grande, lui, masque.
        val plusGrand = IslandForetEngine.Arbre(5, 5, ArbreSankaiEngine.Taille.UNE)
        assertTrue((5 to 4) in IslandForetEngine.casesMasquees(plusGrand))
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
