package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DayNightEngineTest {

    private fun h(heure: Int) = LocalTime.of(heure, 0)

    @Test
    fun `les quatre phases couvrent les vingt-quatre heures`() {
        // Aucune heure ne doit tomber dans un trou : un `when` incomplet
        // lèverait ici plutôt qu'à l'exécution sur le téléphone.
        for (heure in 0..23) {
            DayNightEngine.phase(h(heure))
        }
    }

    @Test
    fun `le matin est le jour, le milieu de nuit est la nuit`() {
        assertEquals(DayNightEngine.Phase.AUBE, DayNightEngine.phase(h(6)))
        assertEquals(DayNightEngine.Phase.JOUR, DayNightEngine.phase(h(12)))
        assertEquals(DayNightEngine.Phase.CREPUSCULE, DayNightEngine.phase(h(19)))
        assertEquals(DayNightEngine.Phase.NUIT, DayNightEngine.phase(h(3)))
    }

    @Test
    fun `le magasin ouvre a huit heures et ferme a vingt`() {
        assertFalse(DayNightEngine.magasinOuvert(h(7)))
        assertTrue(DayNightEngine.magasinOuvert(h(8)))
        assertTrue(DayNightEngine.magasinOuvert(LocalTime.of(19, 59)))
        assertFalse(DayNightEngine.magasinOuvert(h(20)))
    }

    @Test
    fun `le delai de reouverture reste positif de chaque cote de minuit`() {
        // Le calcul enjambe le changement de jour : une soustraction naïve
        // donnerait un délai négatif après 20 h.
        assertTrue(DayNightEngine.messageMagasinFerme(h(22)).contains("10 h"))
        assertTrue(DayNightEngine.messageMagasinFerme(h(2)).contains("6 h"))
    }

    @Test
    fun `la nuit assombrit, le jour pas du tout`() {
        assertEquals(0f, DayNightEngine.intensiteNuit(h(12)), 0.001f)
        assertTrue(DayNightEngine.intensiteNuit(h(2)) > DayNightEngine.intensiteNuit(h(19)))
        // Jamais opaque : le terrain doit rester lisible la nuit.
        assertTrue(DayNightEngine.intensiteNuit(h(2)) < 0.8f)
    }
}
