package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.ALL_SEEDS
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.island.domain.IslandCultureEngine.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandCultureEngineTest {

    private fun actions(
        etat: PlotState = PlotState.EMPTY,
        aDegager: Boolean = false,
        besoinEau: Boolean = false,
        prete: Boolean = false
    ) = IslandCultureEngine.actionsPossibles(etat, aDegager, besoinEau, prete)

    // --- Ordre du cycle -------------------------------------------------------

    @Test
    fun `une parcelle boisee ne propose que le degagement`() {
        assertEquals(listOf(Action.DEGAGER), actions(aDegager = true))
    }

    @Test
    fun `une parcelle vide se prepare avant d'etre semee`() {
        assertEquals(listOf(Action.PREPARER), actions(etat = PlotState.EMPTY))
        assertNotNull(
            IslandCultureEngine.raisonImpossible(
                Action.SEMER, PlotState.EMPTY, aDegager = false,
                besoinEau = false, prete = false
            )
        )
    }

    @Test
    fun `une parcelle preparee accepte le semis`() {
        assertEquals(listOf(Action.SEMER), actions(etat = PlotState.PREPARED))
    }

    @Test
    fun `on ne seme pas sur une culture en cours`() {
        assertNotNull(
            IslandCultureEngine.raisonImpossible(
                Action.SEMER, PlotState.GROWING, false, false, false
            )
        )
    }

    // --- Arrosage -------------------------------------------------------------

    @Test
    fun `arroser une plante qui n'a pas soif est refuse`() {
        // L'eau se gagne en revisant : la gaspiller sans rien dire serait
        // pire que de refuser.
        val raison = IslandCultureEngine.raisonImpossible(
            Action.ARROSER, PlotState.GROWING, false, besoinEau = false, prete = false
        )
        assertNotNull(raison)
        assertTrue(raison!!.contains("assez d'eau"))
    }

    @Test
    fun `arroser est possible des que la plante a soif`() {
        assertNull(
            IslandCultureEngine.raisonImpossible(
                Action.ARROSER, PlotState.NEEDS_CARE, false, besoinEau = true, prete = false
            )
        )
    }

    @Test
    fun `on n'arrose pas une parcelle vide`() {
        assertNotNull(
            IslandCultureEngine.raisonImpossible(
                Action.ARROSER, PlotState.EMPTY, false, besoinEau = true, prete = false
            )
        )
    }

    // --- Recolte --------------------------------------------------------------

    @Test
    fun `on ne recolte pas avant maturite`() {
        assertNotNull(
            IslandCultureEngine.raisonImpossible(
                Action.RECOLTER, PlotState.GROWING, false, false, prete = false
            )
        )
    }

    @Test
    fun `une culture prete propose la recolte`() {
        assertTrue(
            actions(etat = PlotState.READY_TO_HARVEST, prete = true).contains(Action.RECOLTER)
        )
    }

    @Test
    fun `une plante prete ne reclame pas d'eau`() {
        // Sinon on arrose au lieu de recolter, et la recompense attend.
        assertEquals(
            PlotState.READY_TO_HARVEST,
            IslandCultureEngine.etatApres(prete = true, besoinEau = true)
        )
    }

    @Test
    fun `l'etat suit la soif quand la plante n'est pas prete`() {
        assertEquals(
            PlotState.NEEDS_CARE,
            IslandCultureEngine.etatApres(prete = false, besoinEau = true)
        )
        assertEquals(
            PlotState.GROWING,
            IslandCultureEngine.etatApres(prete = false, besoinEau = false)
        )
    }

    // --- Sol ------------------------------------------------------------------

    @Test
    fun `chaque graine exige son propre sol`() {
        // Les sols ne forment pas une hierarchie : un cactus veut du sable, pas
        // de la « meilleure » terre.
        val cactus = ALL_SEEDS.first { it.id == "cactus" }
        assertTrue(IslandCultureEngine.grainePlantable(cactus, SoilType.SABLE))
        assertFalse(IslandCultureEngine.grainePlantable(cactus, SoilType.TERRE))
    }

    // --- Minutes arrosees -----------------------------------------------------

    @Test
    fun `sans arrosage aucune minute n'est comptee comme arrosee`() {
        assertEquals(0L, IslandCultureEngine.minutesArrosees(0L, 1_000L, 100_000L))
    }

    @Test
    fun `l'eau tient un temps borne`() {
        val minute = 60_000L
        val arrosage = 1_000_000L
        assertEquals(
            60L,
            IslandCultureEngine.minutesArrosees(arrosage, arrosage, arrosage + 60 * minute)
        )
        // Fenetre plus longue que la tenue de l'eau : bornee a la tenue.
        assertEquals(
            IslandCultureEngine.TENUE_EAU_MINUTES,
            IslandCultureEngine.minutesArrosees(arrosage, arrosage, arrosage + 48 * 60 * minute)
        )
    }

    @Test
    fun `une fenetre anterieure a l'arrosage ne compte pas`() {
        val arrosage = 1_000_000L
        assertEquals(0L, IslandCultureEngine.minutesArrosees(arrosage, 0L, arrosage - 1L))
    }

    @Test
    fun `une fenetre inversee ne rend jamais de minutes negatives`() {
        // Une horloge qui recule ne doit pas retirer de la croissance.
        assertEquals(0L, IslandCultureEngine.minutesArrosees(1_000L, 500_000L, 400_000L))
    }
}
