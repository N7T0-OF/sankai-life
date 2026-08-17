package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingEngineTest {

    @Test
    fun `le tutoriel reste court`() {
        // Un tutoriel qui explique chaque bouton avant qu'on ait touché à quoi
        // que ce soit se fait passer, et on n'en retient rien.
        assertTrue("trop de pages", OnboardingEngine.pages.size <= 7)
        assertTrue("trop peu de pages", OnboardingEngine.pages.size >= 3)
    }

    @Test
    fun `chaque sujet apparait une seule fois`() {
        assertEquals(OnboardingEngine.pages.distinct(), OnboardingEngine.pages)
    }

    @Test
    fun `la navigation ne sort jamais des bornes`() {
        // Un tutoriel qui saute une page ou boucle est un tutoriel dont
        // personne ne sort.
        var i = 0
        repeat(20) { i = OnboardingEngine.suivante(i) }
        assertEquals(OnboardingEngine.derniere, i)

        repeat(20) { i = OnboardingEngine.precedente(i) }
        assertEquals(0, i)
    }

    @Test
    fun `les indices negatifs sont ramenes au debut`() {
        assertEquals(0, OnboardingEngine.borner(-1))
        assertEquals(0, OnboardingEngine.borner(Int.MIN_VALUE))
        assertEquals(0, OnboardingEngine.suivante(Int.MIN_VALUE))
        assertEquals(0, OnboardingEngine.precedente(-1))
        assertFalse(OnboardingEngine.estDerniere(Int.MIN_VALUE))
    }

    @Test
    fun `Int MAX VALUE reste sur la derniere page sans debordement`() {
        assertEquals(OnboardingEngine.derniere, OnboardingEngine.borner(Int.MAX_VALUE))
        assertEquals(OnboardingEngine.derniere, OnboardingEngine.suivante(Int.MAX_VALUE))
        assertEquals(OnboardingEngine.derniere, OnboardingEngine.precedente(Int.MAX_VALUE))
        assertTrue(OnboardingEngine.estDerniere(Int.MAX_VALUE))
    }

    @Test
    fun `chaque page mene a la suivante sans en sauter`() {
        var i = 0
        val visitees = mutableListOf(0)
        while (!OnboardingEngine.estDerniere(i)) {
            i = OnboardingEngine.suivante(i)
            visitees.add(i)
        }
        assertEquals(OnboardingEngine.pages.indices.toList(), visitees)
    }

    @Test
    fun `seule la derniere page est reconnue comme finale`() {
        OnboardingEngine.pages.indices.forEach { i ->
            assertEquals(i == OnboardingEngine.derniere, OnboardingEngine.estDerniere(i))
        }
        assertFalse(OnboardingEngine.estDerniere(0))
    }

    @Test
    fun `le temps quotidien est un choix explicite`() {
        assertTrue(OnboardingEngine.Topic.DAILY_TIME in OnboardingEngine.pages)
    }

    @Test
    fun `le jardin ne fait pas partie du parcours obligatoire`() {
        assertTrue(OnboardingEngine.pages.none { it.name.contains("GARDEN") })
    }
}
