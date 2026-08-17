package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MimoEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun instant(jour: Int, heure: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 3, jour, heure, minute)
            .atZone(zone).toInstant().toEpochMilli()

    // --- Heures ouvrées ----------------------------------------------------

    @Test
    fun `une absence en pleine journee compte entierement`() {
        val minutes = MimoEngine.minutesOuvrees(instant(10, 9), instant(10, 12), zone)
        assertEquals(180L, minutes)
    }

    @Test
    fun `une absence de nuit ne compte pas`() {
        // C'est ce qui donne une conséquence au cycle jour / nuit : sans cette
        // borne, dormir rapporterait autant que vivre.
        assertEquals(0L, MimoEngine.minutesOuvrees(instant(10, 21), instant(11, 6), zone))
    }

    @Test
    fun `une absence a cheval ne compte que la part ouvree`() {
        // 6 h → 10 h : seules 8 h → 10 h sont ouvrées.
        assertEquals(120L, MimoEngine.minutesOuvrees(instant(10, 6), instant(10, 10), zone))
        // 18 h → 22 h : seules 18 h → 20 h le sont.
        assertEquals(120L, MimoEngine.minutesOuvrees(instant(10, 18), instant(10, 22), zone))
    }

    @Test
    fun `une absence sur deux jours additionne les deux journees`() {
        // 14 h le jour 10 → 10 h le jour 11 : 6 h + 2 h.
        assertEquals(480L, MimoEngine.minutesOuvrees(instant(10, 14), instant(11, 10), zone))
    }

    @Test
    fun `une absence de vingt-quatre heures est bornee par les horaires`() {
        val minutes = MimoEngine.minutesOuvrees(instant(10, 12), instant(11, 12), zone)
        // 12 h → 20 h, puis 8 h → 12 h : 8 h + 4 h = 12 h.
        assertEquals(720L, minutes)
        // Jamais plus que les heures d'ouverture d'une journée entière.
        assertTrue(minutes <= 24 * 60)
    }

    @Test
    fun `un intervalle nul ou inverse ne compte rien`() {
        assertEquals(0L, MimoEngine.minutesOuvrees(instant(10, 12), instant(10, 12), zone))
        assertEquals(0L, MimoEngine.minutesOuvrees(instant(10, 14), instant(10, 9), zone))
    }

    // --- Actions -----------------------------------------------------------

    @Test
    fun `le compost limite les actions autant que le temps`() {
        val type = MimoEngine.Type.ARROSEUR
        // Beaucoup de temps, un seul compost : une seule action.
        assertEquals(1, MimoEngine.actions(type, 600, 1))
        // Beaucoup de compost, peu de temps : le temps décide.
        assertEquals(1, MimoEngine.actions(type, type.cadenceMinutes, 50))
    }

    @Test
    fun `sans compost aucun Mimo ne travaille`() {
        assertEquals(0, MimoEngine.actions(MimoEngine.Type.RECOLTEUR, 10_000, 0))
    }

    @Test
    fun `le plafond empeche de vider le jardin apres une longue absence`() {
        // Une semaine de temps et de compost : le plafond doit tenir, sinon
        // il ne resterait plus rien à faire au joueur à son retour.
        val actions = MimoEngine.actions(MimoEngine.Type.ARROSEUR, 100_000, 9999)
        assertEquals(MimoEngine.ACTIONS_MAX_PAR_OUVERTURE, actions)
    }

    @Test
    fun `le compost borne toute l'equipe apres multiplication`() {
        val type = MimoEngine.Type.ARROSEUR

        // Regression : l'ancien calcul bornait un Mimo a une action puis
        // multipliait par cinq, ce qui depensait cinq compost quand il n'y en
        // avait qu'un.
        assertEquals(1, MimoEngine.actionsEquipe(type, 10_000, effectif = 5, compostDisponible = 1))
        assertEquals(3, MimoEngine.actionsEquipe(type, 10_000, effectif = 5, compostDisponible = 3))
    }

    @Test
    fun `chaque Mimo conserve son plafond dans le budget d'equipe`() {
        val max = MimoEngine.ACTIONS_MAX_PAR_OUVERTURE
        assertEquals(
            max * 2,
            MimoEngine.actionsEquipe(
                MimoEngine.Type.RECOLTEUR,
                minutesOuvrees = 100_000,
                effectif = 2,
                compostDisponible = 10_000
            )
        )
    }

    // --- Rapport -----------------------------------------------------------

    @Test
    fun `un rapport vide ne dit rien`() {
        assertNull(MimoEngine.resume(MimoEngine.Rapport()))
    }

    @Test
    fun `un rapport vide par manque de compost le signale`() {
        val texte = MimoEngine.resume(MimoEngine.Rapport(compostManquant = true))
        assertNotNull(texte)
        assertTrue(texte!!.contains("compost"))
    }

    @Test
    fun `un rapport rempli enumere les actions faites`() {
        val texte = MimoEngine.resume(
            MimoEngine.Rapport(arrosages = 2, ventes = 1, piecesGagnees = 40)
        )
        assertNotNull(texte)
        assertTrue(texte!!.contains("2 arrosage"))
        assertTrue(texte.contains("40"))
        // Ce qui n'a pas eu lieu n'est pas mentionné.
        assertTrue(!texte.contains("récolte"))
    }

    @Test
    fun `chaque type a un nom retrouvable`() {
        for (type in MimoEngine.Type.entries) {
            assertEquals(type, MimoEngine.Type.parNom(type.name))
        }
        assertNull(MimoEngine.Type.parNom("INCONNU"))
    }
}
