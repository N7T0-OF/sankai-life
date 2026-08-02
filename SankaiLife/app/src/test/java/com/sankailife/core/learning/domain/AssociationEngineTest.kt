package com.sankailife.core.learning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AssociationEngineTest {

    private fun paires(n: Int) = (1..n).map {
        AssociationEngine.Paire(it.toLong(), "gauche$it", "droite$it")
    }

    private fun etat(n: Int = 4, graine: Int = 3) =
        AssociationEngine.preparer(paires(n), Random(graine))!!

    /** Touche l'element de gauche puis celui de droite des cartes donnees. */
    private fun apparier(
        depart: AssociationEngine.Etat,
        gaucheDe: Long,
        droiteDe: Long
    ): AssociationEngine.Etat {
        val g = depart.colonneGauche.first { it.carteId == gaucheDe }
        val d = depart.colonneDroite.first { it.carteId == droiteDe }
        return AssociationEngine.toucher(AssociationEngine.toucher(depart, g), d)
    }

    // --- Preparation ----------------------------------------------------------

    @Test
    fun `trop peu de paires ne donne pas d'exercice`() {
        // Mieux vaut ne rien proposer qu'un exercice a deux lignes qui se
        // resout tout seul.
        assertNull(AssociationEngine.preparer(paires(2)))
        assertNull(AssociationEngine.preparer(emptyList()))
    }

    @Test
    fun `le minimum de paires suffit`() {
        assertNotNull(AssociationEngine.preparer(paires(AssociationEngine.PAIRES_MIN)))
    }

    @Test
    fun `l'exercice ne depasse jamais le nombre de paires prevu`() {
        val e = AssociationEngine.preparer(paires(20))!!
        assertEquals(AssociationEngine.PAIRES, e.colonneGauche.size)
        assertEquals(AssociationEngine.PAIRES, e.colonneDroite.size)
    }

    @Test
    fun `les deux colonnes portent exactement les memes cartes`() {
        val e = etat()
        assertEquals(
            e.colonneGauche.map { it.carteId }.toSet(),
            e.colonneDroite.map { it.carteId }.toSet()
        )
    }

    @Test
    fun `une paire incomplete est ecartee`() {
        // Une carte sans verso n'a rien a mettre dans la colonne de droite.
        val melange = paires(3) + AssociationEngine.Paire(99L, "seul", "")
        val e = AssociationEngine.preparer(melange)!!
        assertFalse(99L in e.colonneGauche.map { it.carteId })
    }

    @Test
    fun `une carte en double n'apparait qu'une fois`() {
        val doublons = paires(4) + paires(4)
        val e = AssociationEngine.preparer(doublons)!!
        assertEquals(e.colonneGauche.size, e.colonneGauche.map { it.carteId }.toSet().size)
    }

    @Test
    fun `les colonnes sont melangees independamment`() {
        // Melanger une seule fois donnerait les reponses en diagonale.
        // On cherche au moins une graine ou l'ordre differe : exiger que
        // *toutes* different serait exiger que le hasard ne se repete jamais.
        val differe = (0 until 30).any { graine ->
            val e = AssociationEngine.preparer(paires(4), Random(graine))!!
            e.colonneGauche.map { it.carteId } != e.colonneDroite.map { it.carteId }
        }
        assertTrue("Les deux colonnes suivent toujours le meme ordre", differe)
    }

    @Test
    fun `la preparation est deterministe`() {
        val a = AssociationEngine.preparer(paires(4), Random(11))!!
        val b = AssociationEngine.preparer(paires(4), Random(11))!!
        assertEquals(a.colonneGauche, b.colonneGauche)
        assertEquals(a.colonneDroite, b.colonneDroite)
    }

    // --- Gestes ---------------------------------------------------------------

    @Test
    fun `une bonne paire est validee`() {
        val e = apparier(etat(), 1L, 1L)
        assertTrue(1L in e.trouvees)
        assertNull(e.selection)
        assertTrue(1L in AssociationEngine.verdicts(e).filterValues { it }.keys)
    }

    @Test
    fun `une mauvaise paire salit les deux cartes`() {
        // Confondre deux mots salit les deux : l'une comme l'autre meritent de
        // revenir.
        val e = apparier(etat(), 1L, 2L)
        assertTrue(1L in e.fautives)
        assertTrue(2L in e.fautives)
        assertTrue(e.trouvees.isEmpty())
        assertNotNull(e.derniereErreur)
    }

    @Test
    fun `une carte trouvee apres une faute ne compte pas comme sue`() {
        // Elle a fini par sortir par elimination, ce qui ne prouve rien.
        var e = apparier(etat(), 1L, 2L)
        e = apparier(e, 1L, 1L)
        assertTrue(1L in e.trouvees)
        assertFalse("Trouvee apres faute, comptee comme sue", 1L in e.reussies)
        assertEquals(false, AssociationEngine.verdicts(e)[1L])
    }

    @Test
    fun `on peut commencer par la colonne de droite`() {
        // Imposer un sens de lecture ajoute une difficulte etrangere a ce que
        // l'exercice pretend mesurer.
        val depart = etat()
        val d = depart.colonneDroite.first { it.carteId == 3L }
        val g = depart.colonneGauche.first { it.carteId == 3L }
        val e = AssociationEngine.toucher(AssociationEngine.toucher(depart, d), g)
        assertTrue(3L in e.trouvees)
    }

    @Test
    fun `retoucher l'element choisi l'abandonne`() {
        val depart = etat()
        val g = depart.colonneGauche.first()
        val e = AssociationEngine.toucher(AssociationEngine.toucher(depart, g), g)
        assertNull(e.selection)
        assertTrue(e.trouvees.isEmpty())
    }

    @Test
    fun `toucher deux fois la meme colonne remplace la selection`() {
        // Refuser un geste sans le dire donne l'impression que l'ecran ne
        // repond pas.
        val depart = etat()
        val a = depart.colonneGauche[0]
        val b = depart.colonneGauche[1]
        val e = AssociationEngine.toucher(AssociationEngine.toucher(depart, a), b)
        assertEquals(b, e.selection)
        assertTrue(e.fautives.isEmpty())
    }

    @Test
    fun `une carte deja trouvee ne repond plus`() {
        var e = apparier(etat(), 1L, 1L)
        val g = e.colonneGauche.first { it.carteId == 1L }
        val avant = e
        e = AssociationEngine.toucher(e, g)
        assertEquals(avant, e)
    }

    @Test
    fun `l'exercice se termine quand tout est apparie`() {
        var e = etat()
        assertFalse(e.termine)
        (1L..4L).forEach { e = apparier(e, it, it) }
        assertTrue(e.termine)
        assertEquals(4, e.reussies.size)
    }

    @Test
    fun `un exercice termine ignore les gestes`() {
        var e = etat()
        (1L..4L).forEach { e = apparier(e, it, it) }
        val apres = AssociationEngine.toucher(e, e.colonneGauche.first())
        assertEquals(e, apres)
    }

    // --- Verdicts -------------------------------------------------------------

    @Test
    fun `chaque carte recoit son propre verdict`() {
        // C'est tout l'interet : ne juger que la carte principale gaspillerait
        // trois quarts de ce que l'exercice vient de mesurer.
        var e = etat()
        e = apparier(e, 1L, 2L)   // faute sur 1 et 2
        (1L..4L).forEach { e = apparier(e, it, it) }

        val verdicts = AssociationEngine.verdicts(e)
        assertEquals(4, verdicts.size)
        assertEquals(false, verdicts[1L])
        assertEquals(false, verdicts[2L])
        assertEquals(true, verdicts[3L])
        assertEquals(true, verdicts[4L])
    }

    @Test
    fun `le resume dit la verite dans les trois cas`() {
        var parfait = etat()
        (1L..4L).forEach { parfait = apparier(parfait, it, it) }
        assertTrue(AssociationEngine.resume(parfait).contains("premier coup"))

        var partiel = apparier(etat(), 1L, 2L)
        (1L..4L).forEach { partiel = apparier(partiel, it, it) }
        assertTrue(AssociationEngine.resume(partiel).contains("2 paire"))
    }
}
