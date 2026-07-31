package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class LightingEngineTest {

    private fun h(heure: Int, minute: Int = 0) = LocalTime.of(heure, minute)

    @Test
    fun `le plein jour n'applique aucun voile`() {
        assertEquals(0f, LightingEngine.ambiance(h(13)).opacite, 0.01f)
    }

    @Test
    fun `la nuit assombrit sans jamais rendre illisible`() {
        val nuit = LightingEngine.ambiance(h(2)).opacite
        assertTrue("la nuit doit se voir", nuit > 0.25f)
        // Au-delà de 45 %, le terrain devient illisible. Une application qu'on
        // ouvre surtout le soir ne peut pas se permettre d'être pénible.
        assertTrue("la nuit ne doit pas aveugler", nuit < 0.45f)
    }

    @Test
    fun `la transition est continue, sans saut d'une minute a l'autre`() {
        // C'est tout l'intérêt de l'interpolation : l'ancien voile changeait
        // d'un coup à heure fixe et on voyait le jardin sauter.
        var precedente = LightingEngine.ambiance(h(0)).opacite
        for (minutes in 1 until 24 * 60) {
            val courante = LightingEngine.ambiance(h(minutes / 60, minutes % 60)).opacite
            assertTrue(
                "saut d'opacite a ${minutes / 60}h${minutes % 60}",
                kotlin.math.abs(courante - precedente) < 0.02f
            )
            precedente = courante
        }
    }

    @Test
    fun `l'opacite reste dans les bornes toute la journee`() {
        for (minutes in 0 until 24 * 60) {
            val a = LightingEngine.ambiance(h(minutes / 60, minutes % 60))
            assertTrue(a.opacite in 0f..0.45f)
        }
    }

    @Test
    fun `le soir est chaud, la nuit est froide`() {
        // Le coucher de soleil doit tirer vers l'orange et la nuit vers le
        // bleu : c'est ce qui distingue les deux au premier regard.
        val soir = LightingEngine.ambiance(h(19, 30)).couleur
        val nuit = LightingEngine.ambiance(h(2)).couleur

        fun rouge(c: Long) = (c shr 16) and 0xFF
        fun bleu(c: Long) = c and 0xFF

        assertTrue("le coucher doit etre chaud", rouge(soir) > bleu(soir))
        assertTrue("la nuit doit etre froide", bleu(nuit) > rouge(nuit))
    }

    @Test
    fun `les lanternes s'allument le soir et pas a midi`() {
        assertFalse(LightingEngine.ambiance(h(13)).lanternes)
        assertTrue(LightingEngine.ambiance(h(21)).lanternes)
    }

    @Test
    fun `les etoiles ne sortent que la nuit`() {
        assertFalse(LightingEngine.ambiance(h(13)).etoiles)
        assertFalse(LightingEngine.ambiance(h(19)).etoiles)
        assertTrue(LightingEngine.ambiance(h(23)).etoiles)
    }

    @Test
    fun `minuit et la fin de journee donnent la meme ambiance`() {
        // Sinon le jardin changerait brutalement au passage de minuit.
        val avant = LightingEngine.ambiance(h(23, 59))
        val apres = LightingEngine.ambiance(h(0, 0))
        assertEquals(avant.opacite, apres.opacite, 0.02f)
    }

    @Test
    fun `seules les meteos pluvieuses declenchent des particules`() {
        assertEquals(
            LightingEngine.IntensitePluie.AUCUNE,
            LightingEngine.intensitePluie(WeatherEngine.Meteo.SOLEIL)
        )
        assertEquals(
            LightingEngine.IntensitePluie.FORTE,
            LightingEngine.intensitePluie(WeatherEngine.Meteo.ORAGE)
        )
        assertTrue(
            LightingEngine.intensitePluie(WeatherEngine.Meteo.ORAGE).gouttes >
                LightingEngine.intensitePluie(WeatherEngine.Meteo.PLUIE).gouttes
        )
    }

    @Test
    fun `le mode animations reduites coupe toutes les gouttes`() {
        // Réglage d'accessibilité, pas préférence esthétique : une pluie
        // animée peut provoquer des nausées.
        assertEquals(
            0,
            LightingEngine.nombreGouttes(
                LightingEngine.IntensitePluie.FORTE, animationsReduites = true
            )
        )
        assertTrue(
            LightingEngine.nombreGouttes(
                LightingEngine.IntensitePluie.FORTE, animationsReduites = false
            ) > 0
        )
    }
}
