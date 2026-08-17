package com.sankailife.core.island.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandTimeEngineTest {

    private val minute = 60_000L

    @Test
    fun `le meme intervalle est capture avant l'avancement des curseurs`() {
        val maintenant = 10_000L * minute
        val reperesAvant = listOf(maintenant - 180 * minute, maintenant - 30 * minute)

        assertEquals(
            180L,
            IslandTimeEngine.minutesDepuisDerniereVisite(reperesAvant, maintenant)
        )
        assertEquals(
            0L,
            IslandTimeEngine.minutesDepuisDerniereVisite(listOf(maintenant, maintenant), maintenant)
        )
    }

    @Test
    fun `un retour de l'horloge ne produit aucun temps`() {
        assertEquals(0L, IslandTimeEngine.minutesRetenues(2_000L * minute, 1_000L * minute))
    }

    @Test
    fun `une avance abusive est bornee a vingt quatre heures`() {
        assertEquals(
            IslandTimeEngine.MAX_RATTRAPAGE_MINUTES,
            IslandTimeEngine.minutesRetenues(minute, 365L * 24L * 60L * minute)
        )
    }
}
