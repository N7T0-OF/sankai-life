package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardRewardPolicyTest {

    @Test
    fun `une revision due conserve toutes ses recompenses`() {
        val recompense = FlashcardEngine.recompense(
            FlashcardEngine.ModeSession.REVISION_ECHEANCES
        )

        assertEquals(FlashcardEngine.XP_PAR_CARTE, recompense.xpParCarte)
        assertEquals(FlashcardEngine.XP_SESSION_TERMINEE, recompense.xpFin)
        assertEquals(FlashcardEngine.PIECES_SESSION_TERMINEE, recompense.piecesFin)
        assertTrue(recompense.alimenteJardin)
    }

    @Test
    fun `mes erreurs reste rejouable mais ne rapporte jamais`() {
        val recompense = FlashcardEngine.recompense(
            FlashcardEngine.ModeSession.ENTRAINEMENT_ERREURS
        )

        assertEquals(0, recompense.xpParCarte)
        assertEquals(0, recompense.xpFin)
        assertEquals(0, recompense.piecesFin)
        assertFalse(recompense.alimenteJardin)
    }
}
