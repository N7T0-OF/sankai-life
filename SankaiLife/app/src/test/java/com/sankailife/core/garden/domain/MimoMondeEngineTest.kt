package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimoMondeEngineTest {

    private val parcelles = (0 until 9).map {
        ExpansionEngine.cle(ExpansionEngine.CENTRE + it % 3, ExpansionEngine.CENTRE + it / 3)
    }

    private fun mimo(id: Long, type: MimoEngine.Type) = Triple(id, "Pim$id", type)

    @Test
    fun `la station d'un Mimo ne bouge pas d'une ouverture a l'autre`() {
        // Un Mimo qui change de place à chaque lancement paraîtrait cassé.
        assertEquals(
            MimoMondeEngine.station(7L, parcelles),
            MimoMondeEngine.station(7L, parcelles)
        )
    }

    @Test
    fun `deux Mimos differents ne partagent pas forcement la meme station`() {
        val stations = (1L..8L).map { MimoMondeEngine.station(it, parcelles) }
        assertTrue("les stations doivent se repartir", stations.toSet().size > 1)
    }

    @Test
    fun `sans parcelle, la station retombe au centre`() {
        assertEquals(
            ExpansionEngine.cle(ExpansionEngine.CENTRE, ExpansionEngine.CENTRE),
            MimoMondeEngine.station(3L, emptyList())
        )
    }

    @Test
    fun `un arroseur vise une parcelle seche`() {
        val seche = parcelles[4]
        val place = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)),
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles,
                parcellesSeches = listOf(seche),
                compost = 5
            )
        ).first()

        assertEquals(seche, place.cible)
        assertEquals(MimoMondeEngine.Activite.ARROSE, place.activite)
    }

    @Test
    fun `deux arroseurs ne visent pas la meme parcelle`() {
        // Sinon cinq personnages se superposeraient sur une case et on
        // croirait à un bug d'affichage.
        val places = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR), mimo(2, MimoEngine.Type.ARROSEUR)),
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles,
                parcellesSeches = listOf(parcelles[0], parcelles[1]),
                compost = 5
            )
        )
        assertNotEquals(places[0].cible, places[1].cible)
    }

    @Test
    fun `un arroseur de trop reste sans cible`() {
        val places = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR), mimo(2, MimoEngine.Type.ARROSEUR)),
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles,
                parcellesSeches = listOf(parcelles[0]),
                compost = 5
            )
        )
        assertEquals(1, places.count { it.cible == null })
        assertEquals(MimoMondeEngine.Activite.OISIF, places.first { it.cible == null }.activite)
    }

    @Test
    fun `la nuit, tout le monde dort`() {
        val places = MimoMondeEngine.placer(
            MimoEngine.Type.entries.mapIndexed { i, t -> mimo(i.toLong(), t) },
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles,
                parcellesSeches = parcelles,
                parcellesPretes = parcelles,
                caissesPosees = 5,
                stockVendable = true,
                compost = 50,
                faitJour = false
            )
        )
        assertTrue(places.all { it.endormi })
        assertTrue(places.all { it.cible == null })
    }

    @Test
    fun `sans compost, personne ne travaille`() {
        // Même règle que le moteur de travail : l'affichage ne doit pas
        // promettre une action que la mécanique refusera.
        val places = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.RECOLTEUR)),
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles,
                parcellesPretes = parcelles,
                compost = 0
            )
        )
        assertNull(places.first().cible)
        assertTrue(places.first().endormi)
    }

    @Test
    fun `un transporteur ne s'active que s'il y a des caisses`() {
        val sansCaisse = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.TRANSPORTEUR)),
            MimoMondeEngine.EtatJardin(parcellesDebloquees = parcelles, compost = 5)
        ).first()
        assertEquals(MimoMondeEngine.Activite.OISIF, sansCaisse.activite)

        val avecCaisse = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.TRANSPORTEUR)),
            MimoMondeEngine.EtatJardin(
                parcellesDebloquees = parcelles, caissesPosees = 3, compost = 5
            )
        ).first()
        assertEquals(MimoMondeEngine.Activite.TRANSPORTE, avecCaisse.activite)
    }

    @Test
    fun `chaque metier a une activite propre quand il travaille`() {
        val activites = MimoEngine.Type.entries.map { type ->
            MimoMondeEngine.placer(
                listOf(mimo(1, type)),
                MimoMondeEngine.EtatJardin(
                    parcellesDebloquees = parcelles,
                    parcellesSeches = parcelles,
                    parcellesPretes = parcelles,
                    parcellesLibres = parcelles,
                    caissesPosees = 2,
                    stockVendable = true,
                    compost = 10
                )
            ).first().activite
        }
        // Aucun métier ne doit rester oisif dans un jardin qui a tout à faire.
        assertTrue(activites.none { it == MimoMondeEngine.Activite.OISIF })
        assertEquals(MimoEngine.Type.entries.size, activites.toSet().size)
    }

    @Test
    fun `le resume explique pourquoi un Mimo ne fait rien`() {
        val dort = MimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)),
            MimoMondeEngine.EtatJardin(parcellesDebloquees = parcelles, compost = 0)
        ).first()
        assertTrue(MimoMondeEngine.resume(dort, 0).contains("compost"))
    }
}
