package com.sankailife.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MotDuJourNotifEngineTest {

    private val huitHeures = LocalDateTime.of(2026, 8, 18, 8, 0)
    private val dixHeures = LocalDateTime.of(2026, 8, 18, 10, 0)

    @Test
    fun `avant l'heure, la notification tombe aujourd'hui`() {
        val cible = MotDuJourNotifEngine.prochaineHeure(9 * 60, huitHeures)
        assertEquals(LocalDateTime.of(2026, 8, 18, 9, 0), cible)
    }

    @Test
    fun `apres l'heure, la notification tombe demain`() {
        val cible = MotDuJourNotifEngine.prochaineHeure(9 * 60, dixHeures)
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 0), cible)
    }

    @Test
    fun `a l'heure pile, on ne reprogramme pas sur soi-meme`() {
        val pile = LocalDateTime.of(2026, 8, 18, 9, 0)
        val cible = MotDuJourNotifEngine.prochaineHeure(9 * 60, pile)
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 0), cible)
    }

    @Test
    fun `une heure non pile est respectee a la minute pres`() {
        val cible = MotDuJourNotifEngine.prochaineHeure(9 * 60 + 15, huitHeures)
        assertEquals(LocalDateTime.of(2026, 8, 18, 9, 15), cible)
    }

    @Test
    fun `une heure hors limites est ramenee dans la journee`() {
        // -5 min est ramené à minuit, déjà passé à 8 h : demain minuit.
        assertEquals(
            LocalDateTime.of(2026, 8, 19, 0, 0),
            MotDuJourNotifEngine.prochaineHeure(-5, huitHeures)
        )
        // 24 h est ramené à 23 h 59, pas encore passé à 8 h : aujourd'hui.
        assertEquals(
            LocalDateTime.of(2026, 8, 18, 23, 59),
            MotDuJourNotifEngine.prochaineHeure(24 * 60, huitHeures)
        )
    }

    @Test
    fun `la fin de journee deborde sur le lendemain`() {
        val presqueMinuit = LocalDateTime.of(2026, 8, 18, 23, 50)
        val cible = MotDuJourNotifEngine.prochaineHeure(9 * 60, presqueMinuit)
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 0), cible)
    }
}
