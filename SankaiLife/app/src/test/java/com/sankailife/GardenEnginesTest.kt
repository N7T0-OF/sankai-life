package com.sankailife

import com.sankailife.core.garden.domain.CropGrowthEngine
import com.sankailife.core.garden.domain.HarvestQuality
import com.sankailife.core.garden.domain.LearningRewardEngine
import com.sankailife.core.garden.domain.LearningRewardEngine.StatutCarte
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.garden.domain.TrustedTimeEngine
import com.sankailife.core.garden.domain.TrustedTimeEngine.Verdict
import com.sankailife.core.garden.domain.TrustedTimeState
import com.sankailife.core.garden.domain.seedParId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du noyau du jardin.
 *
 * Ces trois moteurs décident de ce que le joueur gagne. Une erreur ici ne
 * provoque aucun plantage : elle donne simplement des ressources fausses,
 * dans un sens ou dans l'autre, sans que personne ne s'en aperçoive.
 */
class GardenEnginesTest {

    private val minute = 60_000L

    // --- Horloge de confiance --------------------------------------------

    @Test
    fun `premier lancement ne credite aucune croissance`() {
        val r = TrustedTimeEngine.evaluer(TrustedTimeState(), 1_000_000L, 5_000L)
        assertEquals(Verdict.COHERENT, r.verdict)
        assertEquals(0L, r.minutesRetenues)
    }

    @Test
    fun `deux horloges concordantes creditent le temps ecoule`() {
        val avant = TrustedTimeState(derniereHeureMurale = 0L, dernierElapsedRealtime = 0L)
        // 60 minutes des deux côtés.
        val r = TrustedTimeEngine.evaluer(
            avant.copy(derniereHeureMurale = 1000L, dernierElapsedRealtime = 1000L),
            heureMurale = 1000L + 60 * minute,
            elapsedRealtime = 1000L + 60 * minute
        )
        assertEquals(Verdict.COHERENT, r.verdict)
        assertEquals(60L, r.minutesRetenues)
    }

    @Test
    fun `horloge reculee ne credite rien`() {
        val r = TrustedTimeEngine.evaluer(
            TrustedTimeState(derniereHeureMurale = 100 * minute, dernierElapsedRealtime = 100 * minute),
            heureMurale = 50 * minute,
            elapsedRealtime = 101 * minute
        )
        assertEquals(Verdict.RECUL, r.verdict)
        assertEquals(0L, r.minutesRetenues)
    }

    @Test
    fun `bond en avant de l'horloge est ramene au temps reel ecoule`() {
        // L'utilisateur avance l'horloge de 10 jours, mais seules 2 minutes
        // se sont réellement écoulées.
        val r = TrustedTimeEngine.evaluer(
            TrustedTimeState(derniereHeureMurale = 1000L, dernierElapsedRealtime = 1000L),
            heureMurale = 1000L + 14_400 * minute,
            elapsedRealtime = 1000L + 2 * minute
        )
        assertEquals(Verdict.BOND_EN_AVANT, r.verdict)
        assertEquals(2L, r.minutesRetenues)
    }

    @Test
    fun `redemarrage est tolere mais borne`() {
        // elapsedRealtime est reparti de zéro : plus petit qu'avant.
        val r = TrustedTimeEngine.evaluer(
            TrustedTimeState(derniereHeureMurale = 1000L, dernierElapsedRealtime = 999_000L),
            heureMurale = 1000L + 5000 * minute,
            elapsedRealtime = 10 * minute
        )
        assertEquals(Verdict.REDEMARRAGE, r.verdict)
        assertEquals(TrustedTimeEngine.PLAFOND_HORS_LIGNE_MINUTES, r.minutesRetenues)
    }

    @Test
    fun `une tres longue absence est plafonnee a 24 heures`() {
        val r = TrustedTimeEngine.evaluer(
            TrustedTimeState(derniereHeureMurale = 1000L, dernierElapsedRealtime = 1000L),
            heureMurale = 1000L + 10_000 * minute,
            elapsedRealtime = 1000L + 10_000 * minute
        )
        assertEquals(TrustedTimeEngine.PLAFOND_HORS_LIGNE_MINUTES, r.minutesRetenues)
    }

    // --- Croissance -------------------------------------------------------

    @Test
    fun `sans eau la croissance ralentit mais ne s'arrete pas`() {
        val acquises = CropGrowthEngine.minutesAcquises(
            minutesEcoulees = 100, minutesArrosees = 0
        )
        assertTrue("la croissance doit continuer", acquises > 0)
        assertTrue("mais plus lentement", acquises < 100)
    }

    @Test
    fun `une plante arrosee pousse a pleine vitesse`() {
        assertEquals(100L, CropGrowthEngine.minutesAcquises(100, 100))
    }

    @Test
    fun `le sol modifie la duree totale`() {
        val graine = seedParId("tournesol")!!
        val surTerre = CropGrowthEngine.dureeTotaleMinutes(graine, SoilType.TERRE)
        val surCristal = CropGrowthEngine.dureeTotaleMinutes(graine, SoilType.CRISTALLIN)
        assertTrue("le sol cristallin accélère", surCristal < surTerre)
    }

    @Test
    fun `la culture atteint le dernier stade a cent pour cent`() {
        val graine = seedParId("menthe")!!
        val duree = CropGrowthEngine.dureeTotaleMinutes(graine, SoilType.TERRE)
        val etat = CropGrowthEngine.etat(graine, SoilType.TERRE, duree, 0)
        assertTrue(etat.prete)
        assertEquals(0L, etat.minutesRestantes)
    }

    @Test
    fun `une longue absence met en repos sans detruire`() {
        val graine = seedParId("menthe")!!
        val etat = CropGrowthEngine.etat(
            graine, SoilType.TERRE,
            minutesCumulees = 10,
            minutesDepuisArrosage = 5 * 24 * 60
        )
        assertTrue("la culture doit être en repos", etat.enRepos)
        // Aucune notion de mort : la progression est simplement faible.
        assertTrue(etat.progression >= 0f)
    }

    @Test
    fun `le repos reduit le rendement sans l'annuler`() {
        val graine = seedParId("tournesol")!!
        val normal = CropGrowthEngine.rendement(graine, HarvestQuality.NORMALE, enRepos = false)
        val repos = CropGrowthEngine.rendement(graine, HarvestQuality.NORMALE, enRepos = true)
        assertTrue(repos < normal)
        assertTrue("jamais zéro", repos > 0)
    }

    @Test
    fun `la qualite recompense le soin et les revisions`() {
        assertEquals(HarvestQuality.PARFAITE,
            CropGrowthEngine.qualite(arrosagesEffectues = 4, arrosagesAttendus = 4, revisionsPendantCulture = 25))
        assertEquals(HarvestQuality.NORMALE,
            CropGrowthEngine.qualite(arrosagesEffectues = 0, arrosagesAttendus = 4, revisionsPendantCulture = 0))
    }

    // --- Récompenses d'apprentissage -------------------------------------

    @Test
    fun `une carte deja revue aujourd'hui ne rapporte rien`() {
        assertEquals(0, LearningRewardEngine.gouttesPourReponse(true, StatutCarte.DEJA_VUE_AUJOURDHUI))
    }

    @Test
    fun `une mauvaise reponse ne rapporte ni ne retire`() {
        assertEquals(0, LearningRewardEngine.gouttesPourReponse(false, StatutCarte.DUE))
    }

    @Test
    fun `cinq bonnes reponses donnent une unite d'eau`() {
        val gain = LearningRewardEngine.convertir(gouttesAccumulees = 5, eauDejaGagneeAujourdhui = 0)
        assertEquals(1, gain.eauCreditee)
        assertEquals(0, gain.gouttes)
    }

    @Test
    fun `les gouttes restantes sont conservees`() {
        val gain = LearningRewardEngine.convertir(gouttesAccumulees = 7, eauDejaGagneeAujourdhui = 0)
        assertEquals(1, gain.eauCreditee)
        assertEquals(2, gain.gouttes)
    }

    @Test
    fun `le plafond journalier bloque le farm`() {
        // 500 bonnes réponses d'affilée : le plafond doit tenir.
        val gain = LearningRewardEngine.convertir(
            gouttesAccumulees = 500,
            eauDejaGagneeAujourdhui = 0
        )
        assertEquals(LearningRewardEngine.EAU_MAX_PAR_JOUR, gain.eauCreditee)
        assertTrue(gain.plafondAtteint)
    }

    @Test
    fun `aucune eau supplementaire une fois le plafond atteint`() {
        val gain = LearningRewardEngine.convertir(
            gouttesAccumulees = 50,
            eauDejaGagneeAujourdhui = LearningRewardEngine.EAU_MAX_PAR_JOUR
        )
        assertEquals(0, gain.eauCreditee)
        assertTrue(gain.plafondAtteint)
    }

    @Test
    fun `la reserve d'eau ne depasse jamais sa capacite`() {
        val r = LearningRewardEngine.ajouterEau(LearningRewardEngine.CAPACITE_EAU - 2, 50)
        assertEquals(LearningRewardEngine.CAPACITE_EAU, r)
    }

    @Test
    fun `le bonus de croissance progresse par paliers de dix cartes`() {
        assertEquals(0L, LearningRewardEngine.bonusCroissanceMinutes(9))
        assertEquals(LearningRewardEngine.BONUS_REVISION_MINUTES,
            LearningRewardEngine.bonusCroissanceMinutes(10))
        assertEquals(LearningRewardEngine.BONUS_REVISION_MINUTES * 2,
            LearningRewardEngine.bonusCroissanceMinutes(25))
    }
}
