package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotEngineTest {

    private val carotte = ALL_SEEDS.first()

    @Test
    fun `le cours reste dans une fourchette lisible`() {
        // Vingt jours, toutes les espèces : aucun cours aberrant ne doit
        // passer, sinon un légume vaudrait soudain dix fois son prix.
        for (jour in 1..20) {
            for (graine in ALL_SEEDS) {
                val cours = DepotEngine.cours(graine.id, "2026-01-%02d".format(jour))
                assertTrue("cours hors bornes : $cours", cours in 0.79f..1.21f)
            }
        }
    }

    @Test
    fun `le meme jour donne toujours le meme cours`() {
        // C'est ce qui permet de se passer de stockage : hors ligne, deux
        // lancements successifs ne doivent pas changer le prix affiché.
        val a = DepotEngine.cours("carotte", "2026-03-14")
        val b = DepotEngine.cours("carotte", "2026-03-14")
        assertEquals(a, b, 0.0001f)
    }

    @Test
    fun `deux jours differents donnent des cours differents`() {
        val a = DepotEngine.cours("carotte", "2026-03-14")
        val b = DepotEngine.cours("carotte", "2026-03-15")
        assertNotEquals(a, b)
    }

    @Test
    fun `la qualite augmente le prix`() {
        val jour = "2026-03-14"
        val normale = DepotEngine.prixUnitaire(carotte, HarvestQuality.NORMALE, jour)
        val parfaite = DepotEngine.prixUnitaire(carotte, HarvestQuality.PARFAITE, jour)
        assertTrue(parfaite > normale)
    }

    @Test
    fun `le prix ne tombe jamais a zero`() {
        // Une récolte doit toujours valoir quelque chose, même au cours le
        // plus bas sur l'espèce la moins chère.
        for (jour in 1..28) {
            val prix = DepotEngine.prixUnitaire(
                carotte, HarvestQuality.NORMALE, "2026-02-%02d".format(jour)
            )
            assertTrue(prix >= 1)
        }
    }

    @Test
    fun `la cle distingue espece et qualite`() {
        assertNotEquals(
            DepotEngine.cle("carotte", HarvestQuality.NORMALE),
            DepotEngine.cle("carotte", HarvestQuality.PARFAITE)
        )
    }

    @Test
    fun `la saturation se declenche a la capacite et pas avant`() {
        assertFalse(DepotEngine.terrainSature(DepotEngine.CAPACITE_CAISSES - 1))
        assertTrue(DepotEngine.terrainSature(DepotEngine.CAPACITE_CAISSES))
        assertEquals(0, DepotEngine.placeRestante(DepotEngine.CAPACITE_CAISSES + 5))
    }
}
