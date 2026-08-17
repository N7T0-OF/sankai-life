package com.sankailife.core.culture

import org.junit.Assert.assertEquals
import org.junit.Test

class MomentCultureTest {

    @Test
    fun `le matin couvre 5h a 11h`() {
        (5..11).forEach { heure ->
            assertEquals(MomentDuJour.MATIN, MomentCulture.moment(heure))
        }
    }

    @Test
    fun `la journee couvre 12h a 17h`() {
        (12..17).forEach { heure ->
            assertEquals(MomentDuJour.JOURNEE, MomentCulture.moment(heure))
        }
    }

    @Test
    fun `le soir couvre 18h a 4h`() {
        (18..23).forEach { heure ->
            assertEquals(MomentDuJour.SOIR, MomentCulture.moment(heure))
        }
        (0..4).forEach { heure ->
            assertEquals(MomentDuJour.SOIR, MomentCulture.moment(heure))
        }
    }

    @Test
    fun `chaque moment a une famille preferee non vide`() {
        MomentDuJour.entries.forEach { moment ->
            assertEquals(false, MomentCulture.typesPreferees(moment).isEmpty())
        }
    }
}
