package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WeatherEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun instant(jour: Int, heure: Int) =
        LocalDateTime.of(2026, 5, jour, heure, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `le meme jour donne toujours la meme meteo`() {
        // Hors ligne, relancer l'application ne doit pas changer le temps
        // qu'il fait : ça ressemblerait à un défaut, pas à de la variété.
        assertEquals(
            WeatherEngine.meteoDuJour("2026-05-12"),
            WeatherEngine.meteoDuJour("2026-05-12")
        )
    }

    @Test
    fun `les jours ne se ressemblent pas tous`() {
        val trente = (1..30).map { WeatherEngine.meteoDuJour("2026-05-%02d".format(it)) }
        assertTrue("la meteo doit varier", trente.toSet().size >= 3)
    }

    @Test
    fun `le beau temps domine et la pluie reste minoritaire`() {
        // La pluie arrose gratuitement. Trop fréquente, elle rendrait
        // l'arrosage facultatif — donc la révision qui produit l'eau aussi.
        val annee = (1..365).map {
            WeatherEngine.meteoDuJour(java.time.LocalDate.ofYearDay(2026, it).toString())
        }
        val pluvieux = annee.count { it.pleut }
        assertTrue("trop de pluie : $pluvieux", pluvieux < annee.size / 2)
        assertTrue("pas assez de pluie : $pluvieux", pluvieux > 0)
    }

    @Test
    fun `seules les meteos pluvieuses apportent de l'eau`() {
        for (meteo in WeatherEngine.Meteo.entries) {
            if (meteo.pleut) assertTrue(meteo.pluieParHeure > 0f)
            else assertEquals(0f, meteo.pluieParHeure, 0.0001f)
        }
    }

    @Test
    fun `la canicule assèche plus vite que le ciel couvert`() {
        assertTrue(
            WeatherEngine.Meteo.CANICULE.facteurEvaporation >
                WeatherEngine.Meteo.NUAGEUX.facteurEvaporation
        )
        assertTrue(
            WeatherEngine.Meteo.PLUIE.facteurEvaporation <
                WeatherEngine.Meteo.SOLEIL.facteurEvaporation
        )
    }

    @Test
    fun `une absence dans la journee ne fait qu'un segment`() {
        val segments = WeatherEngine.segments(instant(10, 9), instant(10, 17), zone)
        assertEquals(1, segments.size)
        assertEquals(480L, segments.first().minutes)
    }

    @Test
    fun `une absence a cheval sur deux jours donne deux segments`() {
        // Appliquer la météo du retour à toute la période ferait pleuvoir
        // rétroactivement sur une journée qui avait été sèche.
        val segments = WeatherEngine.segments(instant(10, 20), instant(11, 8), zone)
        assertEquals(2, segments.size)
        assertEquals(240L, segments[0].minutes)   // 20 h → minuit
        assertEquals(480L, segments[1].minutes)   // minuit → 8 h
    }

    @Test
    fun `le total des segments couvre exactement l'absence`() {
        val debut = instant(9, 13)
        val fin = instant(12, 7)
        val total = WeatherEngine.segments(debut, fin, zone).sumOf { it.minutes }
        assertEquals((fin - debut) / 60_000, total)
    }

    @Test
    fun `un intervalle nul ou inverse ne donne aucun segment`() {
        assertTrue(WeatherEngine.segments(instant(10, 9), instant(10, 9), zone).isEmpty())
        assertTrue(WeatherEngine.segments(instant(10, 14), instant(10, 9), zone).isEmpty())
    }

    @Test
    fun `la pluie remonte un sol sec, le soleil le fait descendre`() {
        val sec = 0.2f
        val sousPluie = MoistureEngine.apresEcoulement(
            sec, 240, SoilType.TERRE, meteo = WeatherEngine.Meteo.PLUIE
        )
        val sousSoleil = MoistureEngine.apresEcoulement(
            sec, 240, SoilType.TERRE, meteo = WeatherEngine.Meteo.SOLEIL
        )
        assertTrue("la pluie doit mouiller", sousPluie > sec)
        assertTrue("le soleil doit assecher", sousSoleil < sec)
    }

    @Test
    fun `meme sous l'orage l'humidite reste bornee`() {
        val v = MoistureEngine.apresEcoulement(
            0.9f, 6000, SoilType.HUMIDE, meteo = WeatherEngine.Meteo.ORAGE
        )
        assertTrue(v <= 1f)
    }

    @Test
    fun `les previsions couvrent les jours demandes sans doublon de date`() {
        val p = WeatherEngine.previsions(5, zone)
        assertEquals(5, p.size)
        assertEquals(5, p.map { it.first }.toSet().size)
        assertNotEquals(p.first().first, p.last().first)
    }
}
