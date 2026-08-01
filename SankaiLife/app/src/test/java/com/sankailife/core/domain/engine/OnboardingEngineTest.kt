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
    fun `chaque page est complete`() {
        OnboardingEngine.pages.forEach { p ->
            assertTrue(p.titre.isNotBlank())
            assertTrue(p.texte.isNotBlank())
            assertTrue("un bouton sans libelle bloque le tutoriel", p.action.isNotBlank())
            assertTrue(p.emoji.isNotBlank())
        }
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
    fun `le lien entre reviser et jardiner est explique`() {
        // C'est la seule chose qu'on ne peut pas deviner en touchant l'écran,
        // et donc la seule qui justifie vraiment un tutoriel.
        val texte = OnboardingEngine.pages.joinToString(" ") { it.titre + " " + it.texte }
            .lowercase()
        assertTrue(texte.contains("eau"))
        assertTrue(texte.contains("révis"))
    }
}
