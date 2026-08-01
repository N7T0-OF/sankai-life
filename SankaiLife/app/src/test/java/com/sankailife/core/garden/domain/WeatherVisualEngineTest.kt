package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherVisualEngineTest {

    private fun state(
        weather: WeatherEngine.Meteo = WeatherEngine.Meteo.NUAGEUX,
        phase: DayNightEngine.Phase = DayNightEngine.Phase.JOUR,
        quality: GraphicsQuality = GraphicsQuality.NORMAL,
        reduceMotion: Boolean = false
    ) = WeatherVisualEngine.state(
        weather = weather,
        phase = phase,
        quality = quality,
        dayId = "2026-08-01",
        reduceMotion = reduceMotion
    )

    @Test
    fun `un ciel clair coupe totalement les ombres`() {
        val clear = state(weather = WeatherEngine.Meteo.SOLEIL)
        assertFalse(clear.clouds.enabled)
        assertEquals(0f, clear.clouds.density, 0f)
        assertEquals(0f, clear.clouds.opacity, 0f)
        assertEquals(0f, clear.clouds.speed, 0f)
    }

    @Test
    fun `plus le temps se degrade, plus l'ombre est marquee`() {
        // Le défaut d'origine : l'orage (0,13) assombrissait moins qu'un ciel
        // simplement nuageux (0,14). Un orage plus lumineux qu'une journée
        // grise se lit comme un défaut d'affichage.
        val nuageux = state(weather = WeatherEngine.Meteo.NUAGEUX).clouds.opacity
        val pluie = state(weather = WeatherEngine.Meteo.PLUIE).clouds.opacity
        val orage = state(weather = WeatherEngine.Meteo.ORAGE).clouds.opacity

        assertTrue("pluie ($pluie) devrait depasser nuageux ($nuageux)", pluie > nuageux)
        assertTrue("orage ($orage) devrait depasser pluie ($pluie)", orage > pluie)
    }

    @Test
    fun `les ombres restent visibles sans noircir le jardin`() {
        // Bornes hautes et basses. Trop faible, l'ombre ne se voit pas ; trop
        // forte, elle passe pour une panne d'affichage.
        for (meteo in listOf(
            WeatherEngine.Meteo.NUAGEUX, WeatherEngine.Meteo.PLUIE, WeatherEngine.Meteo.ORAGE
        )) {
            val o = state(weather = meteo).clouds.opacity
            assertTrue("$meteo : ombre trop faible ($o)", o >= 0.14f)
            assertTrue("$meteo : ombre trop sombre ($o)", o <= 0.34f)
        }
    }

    @Test
    fun `la nuit attenue les ombres sans les supprimer`() {
        // Une ombre portée en pleine nuit n'aurait pas de source ; l'annuler
        // ferait toutefois disparaître le ciel couvert d'un coup.
        val jour = state(weather = WeatherEngine.Meteo.ORAGE).clouds.opacity
        val nuit = state(
            weather = WeatherEngine.Meteo.ORAGE, phase = DayNightEngine.Phase.NUIT
        ).clouds.opacity
        assertTrue(nuit < jour)
        assertTrue(nuit > 0f)
    }

    @Test
    fun `la qualite choisit exactement une deux ou trois couches`() {
        assertEquals(1, state(quality = GraphicsQuality.LOW).clouds.layers)
        assertEquals(2, state(quality = GraphicsQuality.NORMAL).clouds.layers)
        assertEquals(3, state(quality = GraphicsQuality.HIGH).clouds.layers)
    }

    @Test
    fun `le vent est stable pour une meme journee et reste normalise`() {
        val first = WeatherVisualEngine.wind("2026-08-01", WeatherEngine.Meteo.PLUIE)
        val second = WeatherVisualEngine.wind("2026-08-01", WeatherEngine.Meteo.PLUIE)
        assertEquals(first, second)
        assertTrue(first.directionDegrees in 0f..359f)
        assertTrue(first.strength in 0f..1f)
    }

    @Test
    fun `les ombres deviennent discretes la nuit`() {
        val day = state(phase = DayNightEngine.Phase.JOUR)
        val night = state(phase = DayNightEngine.Phase.NUIT)
        assertTrue(night.clouds.opacity < day.clouds.opacity)
    }

    @Test
    fun `reduire les animations immobilise les nuages sans les retirer`() {
        val reduced = state(reduceMotion = true)
        assertTrue(reduced.clouds.enabled)
        assertTrue(reduced.clouds.opacity > 0f)
        assertEquals(0f, reduced.clouds.speed, 0f)
    }

    @Test
    fun `la pluie et les nuages partagent exactement le meme vent`() {
        val rain = state(weather = WeatherEngine.Meteo.PLUIE)
        assertEquals(rain.wind.directionDegrees, rain.clouds.directionDegrees, 0f)
    }

    @Test
    fun `la qualite inconnue retombe sur le profil normal`() {
        assertEquals(GraphicsQuality.NORMAL, GraphicsQuality.parId("inconnue"))
    }
}
