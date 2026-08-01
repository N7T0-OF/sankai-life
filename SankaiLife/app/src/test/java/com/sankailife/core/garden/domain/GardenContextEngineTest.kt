package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GardenContextEngineTest {

    @Test
    fun `la recolte passe avant l arrosage`() {
        assertEquals(
            GardenContextEngine.Action.HARVEST,
            GardenContextEngine.action(
                cultivable = true,
                ready = true,
                needsWater = true,
                waterAvailable = true
            )
        )
    }

    @Test
    fun `l arrosage exige une parcelle cultivable et de l eau`() {
        assertEquals(
            GardenContextEngine.Action.WATER,
            GardenContextEngine.action(true, false, true, true)
        )
        assertEquals(
            GardenContextEngine.Action.DETAILS,
            GardenContextEngine.action(true, false, true, false)
        )
        assertEquals(
            GardenContextEngine.Action.DETAILS,
            GardenContextEngine.action(false, false, true, true)
        )
    }

    @Test
    fun `les autres etats ouvrent les details`() {
        assertEquals(
            GardenContextEngine.Action.DETAILS,
            GardenContextEngine.action(true, false, false, true)
        )
    }
}
