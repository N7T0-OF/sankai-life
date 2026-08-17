package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSourceEngineTest {

    @Test
    fun `la premiere occurrence rapporte le plein`() {
        val source = ProgressSourceEngine.Source.CALENDRIER
        assertEquals(
            ProgressSourceEngine.regle(source).xpInitial,
            ProgressSourceEngine.xpPour(source, 0, 0)
        )
    }

    @Test
    fun `les occurrences suivantes rapportent moins`() {
        val source = ProgressSourceEngine.Source.CONCENTRATION
        val premiere = ProgressSourceEngine.xpPour(source, 0, 0)
        val deuxieme = ProgressSourceEngine.xpPour(source, premiere, 1)
        val troisieme = ProgressSourceEngine.xpPour(source, premiere + deuxieme, 2)
        assertTrue("deuxieme=$deuxieme doit etre < premiere=$premiere", deuxieme < premiere)
        assertTrue("troisieme=$troisieme doit etre < deuxieme=$deuxieme", troisieme < deuxieme)
    }

    @Test
    fun `la sequence suit les paliers 20 15 10 5`() {
        val source = ProgressSourceEngine.Source.CALENDRIER
        assertEquals(20, ProgressSourceEngine.xpPour(source, 0, 0))
        assertEquals(15, ProgressSourceEngine.xpPour(source, 20, 1))
        assertEquals(10, ProgressSourceEngine.xpPour(source, 35, 2))
        assertEquals(5, ProgressSourceEngine.xpPour(source, 45, 3))
        // Plancher : la valeur reste à 1 tant que le plafond n'est pas atteint.
        assertEquals(1, ProgressSourceEngine.xpPour(source, 50, 4))
    }

    @Test
    fun `le plafond quotidien bloque la source`() {
        val source = ProgressSourceEngine.Source.APPRENTISSAGE
        val plafond = ProgressSourceEngine.regle(source).plafondQuotidien
        assertEquals(0, ProgressSourceEngine.xpPour(source, plafond, 5))
        assertEquals(0, ProgressSourceEngine.xpPour(source, plafond + 100, 6))
    }

    @Test
    fun `le gain ne depasse jamais le plafond`() {
        val source = ProgressSourceEngine.Source.CALENDRIER
        val plafond = ProgressSourceEngine.regle(source).plafondQuotidien
        var accorde = 0
        var occurrences = 0
        var tours = 0
        while (accorde < plafond && tours < 200) {
            val gain = ProgressSourceEngine.xpPour(source, accorde, occurrences)
            accorde += gain
            occurrences++
            tours++
        }
        assertEquals(plafond, accorde)
    }

    @Test
    fun `la decouverte rapporte une seule fois par jour`() {
        val source = ProgressSourceEngine.Source.DECOUVERTE
        assertEquals(5, ProgressSourceEngine.xpPour(source, 0, 0))
        assertEquals(0, ProgressSourceEngine.xpPour(source, 5, 1))
    }

    @Test
    fun `chaque occurrence reste au moins a 1 xp jusqu'au plafond`() {
        val source = ProgressSourceEngine.Source.CONCENTRATION
        var accorde = 0
        var occurrences = 0
        var tours = 0
        while (tours < 100) {
            val gain = ProgressSourceEngine.xpPour(source, accorde, occurrences)
            if (gain == 0) break
            assertTrue("gain=$gain doit rester >= 1", gain >= 1)
            accorde += gain
            occurrences++
            tours++
        }
        assertTrue("la source s'epuise avant 100 tours", tours < 100)
        assertEquals(
            ProgressSourceEngine.regle(source).plafondQuotidien,
            accorde
        )
    }

    @Test
    fun `les regles par defaut sont modestes`() {
        val apprentissage = ProgressSourceEngine.regle(ProgressSourceEngine.Source.APPRENTISSAGE)
        val calendrier = ProgressSourceEngine.regle(ProgressSourceEngine.Source.CALENDRIER)
        // L'apprentissage, cœur de Sankai, plafonne au-dessus des autres :
        // c'est là qu'on veut que le temps soit passé.
        assertTrue(apprentissage.plafondQuotidien > calendrier.plafondQuotidien)
    }
}
