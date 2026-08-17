package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRewardEngineTest {

    @Test
    fun `cinq minutes ne recoivent plus la recompense de vingt cinq minutes`() {
        val courte = FocusRewardEngine.pourMinutes(5)

        assertEquals(10, courte.xp)
        assertEquals(2, courte.pieces)
    }

    @Test
    fun `les reperes historiques de vingt cinq et quarante cinq minutes restent vrais`() {
        val classique = FocusRewardEngine.pourMinutes(25)
        val longue = FocusRewardEngine.pourMinutes(45)

        assertEquals(XpEngine.XP_FOCUS_25MIN, classique.xp)
        assertEquals(10, classique.pieces)
        assertEquals(XpEngine.XP_FOCUS_LONG, longue.xp)
    }

    @Test
    fun `une duree invalide ne rapporte rien et les longues sessions sont plafonnees`() {
        assertEquals(FocusRewardEngine.Recompense(), FocusRewardEngine.pourMinutes(4))
        assertEquals(
            FocusRewardEngine.pourMinutes(120),
            FocusRewardEngine.pourMinutes(10_000)
        )
    }
}
