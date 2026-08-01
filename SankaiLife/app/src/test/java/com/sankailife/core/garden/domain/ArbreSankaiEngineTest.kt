package com.sankailife.core.garden.domain

import com.sankailife.core.garden.domain.ArbreSankaiEngine.Case
import com.sankailife.core.garden.domain.ArbreSankaiEngine.Taille
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArbreSankaiEngineTest {

    // --- Emprise ------------------------------------------------------------

    @Test
    fun `chaque taille occupe le nombre de cases annonce`() {
        assertEquals(1, Taille.UNE.emprise.cases.size)
        assertEquals(2, Taille.DEUX.emprise.cases.size)
        assertEquals(3, Taille.TROIS.emprise.cases.size)
        assertEquals(4, Taille.QUATRE.emprise.cases.size)
    }

    @Test
    fun `l'emprise se decale avec l'origine`() {
        val cases = ArbreSankaiEngine.casesOccupees(Case(20, 18), Taille.QUATRE)
        assertEquals(
            setOf(Case(20, 18), Case(21, 18), Case(20, 19), Case(21, 19)),
            cases.toSet()
        )
    }

    @Test
    fun `les cles occupees suivent la convention de la grille`() {
        val cles = ArbreSankaiEngine.clesOccupees(Case(5, 7), Taille.DEUX)
        assertEquals(setOf(ExpansionEngine.cle(5, 7), ExpansionEngine.cle(6, 7)), cles)
    }

    // --- Ancrage : le defaut classique --------------------------------------

    @Test
    fun `sur deux cases le tronc tombe sur la frontiere, pas sur celle de gauche`() {
        // Coller l'arbre sur la case de gauche est le rate habituel d'un decor
        // a emprise paire.
        val (cx, _) = Taille.DEUX.emprise.centre
        assertEquals(1f, cx, 0.0001f)
    }

    @Test
    fun `sur une case le tronc tombe au milieu de la case`() {
        val (cx, cy) = Taille.UNE.emprise.centre
        assertEquals(0.5f, cx, 0.0001f)
        assertEquals(0.5f, cy, 0.0001f)
    }

    @Test
    fun `sur quatre cases le tronc tombe a leur intersection`() {
        val (cx, cy) = Taille.QUATRE.emprise.centre
        assertEquals(1f, cx, 0.0001f)
        assertEquals(1f, cy, 0.0001f)
    }

    @Test
    fun `le tronc a l'ecran suit l'origine et le pas`() {
        val tronc = ArbreSankaiEngine.troncEcran(Pair(100f, 200f), pas = 80f, Taille.QUATRE)
        assertEquals(100f + 80f, tronc.first, 0.001f)
        assertEquals(200f + 80f, tronc.second, 0.001f)
    }

    // --- Cadre de dessin ----------------------------------------------------

    @Test
    fun `le cadre place la base du tronc exactement sur le sol`() {
        // Le fichier laisse du vide sous le tronc : poser l'image sur la ligne
        // de sol ferait leviter l'arbre.
        val tronc = Pair(500f, 900f)
        val (gauche, haut, cote) = ArbreSankaiEngine.cadreDessin(tronc, pas = 80f, Taille.DEUX)

        assertEquals(tronc.first, gauche + ArbreSankaiEngine.ANCRAGE_X * cote, 0.001f)
        assertEquals(tronc.second, haut + ArbreSankaiEngine.ANCRAGE_Y * cote, 0.001f)
    }

    @Test
    fun `le dessin reste carre quelle que soit l'emprise`() {
        // L'etirer pour remplir une emprise rectangulaire deformerait la
        // couronne.
        val (_, _, coteDeux) = ArbreSankaiEngine.cadreDessin(Pair(0f, 0f), 80f, Taille.DEUX)
        val (_, _, coteQuatre) = ArbreSankaiEngine.cadreDessin(Pair(0f, 0f), 80f, Taille.QUATRE)
        // Deux cases de large, deux cases de cote : meme dimension majorante.
        assertEquals(coteDeux, coteQuatre, 0.001f)
    }

    @Test
    fun `la couronne deborde des cases reservees`() {
        // Un feuillage rond limite a son emprise a l'air taille au carre.
        val (_, _, cote) = ArbreSankaiEngine.cadreDessin(Pair(0f, 0f), pas = 80f, Taille.UNE)
        assertTrue("Le dessin devrait deborder de la case", cote > 80f)
    }

    @Test
    fun `l'ancrage n'est ni centre ni au bas du fichier`() {
        // Ces deux valeurs sont mesurees sur l'asset livre. Si quelqu'un les
        // remet a 0,5 et 1,0 « pour faire propre », ce test le dit.
        assertTrue(
            "Le tronc n'est pas au centre horizontal du fichier",
            ArbreSankaiEngine.ANCRAGE_X != 0.5f
        )
        assertTrue(
            "Le fichier laisse du vide sous le tronc",
            ArbreSankaiEngine.ANCRAGE_Y < 1f
        )
    }

    // --- Collisions ---------------------------------------------------------

    @Test
    fun `un placement entierement libre est accepte`() {
        val libres = (18..21).flatMap { x -> (18..21).map { y -> ExpansionEngine.cle(x, y) } }.toSet()
        assertTrue(ArbreSankaiEngine.placementValide(Case(18, 18), Taille.QUATRE, libres))
    }

    @Test
    fun `une seule case occupee suffit a refuser`() {
        val libres = (18..21).flatMap { x -> (18..21).map { y -> ExpansionEngine.cle(x, y) } }
            .toMutableSet()
        libres.remove(ExpansionEngine.cle(19, 19))
        assertFalse(ArbreSankaiEngine.placementValide(Case(18, 18), Taille.QUATRE, libres))
    }

    @Test
    fun `un arbre a cheval sur le brouillard est refuse`() {
        // Les cases inconnues sont absentes de l'ensemble : reserver du terrain
        // qui n'existe pas encore casserait l'extension suivante.
        val libres = setOf(ExpansionEngine.cle(18, 18), ExpansionEngine.cle(19, 18))
        assertTrue(ArbreSankaiEngine.placementValide(Case(18, 18), Taille.DEUX, libres))
        assertFalse(ArbreSankaiEngine.placementValide(Case(18, 18), Taille.QUATRE, libres))
    }

    // --- Progression --------------------------------------------------------

    @Test
    fun `l'arbre ne retrecit jamais quand l'arene monte`() {
        // Un arbre qui rapetisse se lirait comme une punition alors que rien
        // n'a ete perdu.
        val ordre = listOf(Taille.UNE, Taille.DEUX, Taille.TROIS, Taille.QUATRE)
        var precedent = -1
        for (arene in 1..8) {
            val rang = ordre.indexOf(ArbreSankaiEngine.taillePourArene(arene))
            assertTrue("Retrecissement a l'arene $arene", rang >= precedent)
            precedent = rang
        }
    }

    @Test
    fun `une arene hors bornes reste sur une taille valide`() {
        assertEquals(Taille.UNE, ArbreSankaiEngine.taillePourArene(0))
        assertEquals(Taille.UNE, ArbreSankaiEngine.taillePourArene(-5))
        assertEquals(Taille.QUATRE, ArbreSankaiEngine.taillePourArene(99))
    }

    @Test
    fun `une emprise incoherente est refusee a la construction`() {
        val doublon = runCatching {
            ArbreSankaiEngine.Emprise(listOf(Case(0, 0), Case(0, 0)))
        }
        assertTrue("Une case repetee devrait etre refusee", doublon.isFailure)

        val vide = runCatching { ArbreSankaiEngine.Emprise(emptyList()) }
        assertTrue("Une emprise vide devrait etre refusee", vide.isFailure)
    }
}
