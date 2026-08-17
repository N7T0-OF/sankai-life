package com.sankailife.core.concentration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcentrationIntegrationTest {

    @Test
    fun `une fin de minuteur Google est reconnue`() {
        assertTrue(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.google.android.deskclock",
                canal = "timer",
                ongoing = false
            )
        )
    }

    @Test
    fun `la fin d un minuteur Samsung est reconnue`() {
        assertTrue(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.sec.android.app.clockpackage",
                canal = "timer",
                ongoing = false
            )
        )
    }

    @Test
    fun `un reveil n est pas une fin de minuteur`() {
        assertFalse(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.google.android.deskclock",
                canal = "alarm",
                ongoing = false
            )
        )
    }

    @Test
    fun `un chronometre en marche n est pas une fin de minuteur`() {
        assertFalse(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.google.android.deskclock",
                canal = "stopwatch",
                ongoing = true
            )
        )
    }

    @Test
    fun `un minuteur encore en marche n est pas terminé`() {
        assertFalse(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.google.android.deskclock",
                canal = "timer",
                ongoing = true
            )
        )
    }

    @Test
    fun `une application inconnue est ignoree`() {
        assertFalse(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.example.app",
                canal = "timer",
                ongoing = false
            )
        )
    }

    @Test
    fun `sans canal rien n est reconnu`() {
        assertFalse(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.google.android.deskclock",
                canal = null,
                ongoing = false
            )
        )
    }

    @Test
    fun `le canal resiste a la casse`() {
        assertTrue(
            ConcentrationIntegration.estMinuteurFini(
                paquet = "com.android.deskclock",
                canal = "Timer",
                ongoing = false
            )
        )
    }

    @Test
    fun `une fin de minuteur deja creditee ne rapporte pas deux fois`() {
        val deja = setOf("cle-1")
        val minutes = listOf(
            ConcentrationIntegration.MinuteurFini("cle-1"),
            ConcentrationIntegration.MinuteurFini("cle-2")
        )
        assertEquals(
            listOf("cle-2"),
            ConcentrationIntegration.aCrediter(deja, minutes).map { it.cle }
        )
    }

    @Test
    fun `la meme notification relue ne compte qu une fois`() {
        val minutes = listOf(
            ConcentrationIntegration.MinuteurFini("cle-1"),
            ConcentrationIntegration.MinuteurFini("cle-1")
        )
        assertEquals(
            listOf("cle-1"),
            ConcentrationIntegration.aCrediter(emptySet(), minutes).map { it.cle }
        )
    }

    @Test
    fun `sans fin de minuteur rien n est a crediter`() {
        assertEquals(
            emptyList<ConcentrationIntegration.MinuteurFini>(),
            ConcentrationIntegration.aCrediter(emptySet(), emptyList())
        )
    }
}
