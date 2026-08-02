package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.MimoEngine
import com.sankailife.core.garden.domain.MimoMondeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandMimoMondeEngineTest {

    private fun mimo(id: Long, type: MimoEngine.Type) =
        IslandMimoMondeEngine.Mimo(id, "Mimo$id", type)

    /** Terrain d'essai : tout est accueillant, sauf les parcelles cultivées. */
    private fun herbePartout(parcelles: List<IslandMimoMondeEngine.Parcelle> = emptyList()):
        (Int, Int) -> Boolean {
        val prises = parcelles.map { it.x to it.y }.toSet()
        return { x, y -> x in 0..31 && y in 0..31 && (x to y) !in prises }
    }

    // --- Placement ------------------------------------------------------------

    @Test
    fun `sans Mimo il n'y a rien a poser`() {
        assertTrue(IslandMimoMondeEngine.placer(emptyList()) { _, _ -> true }.isEmpty())
    }

    @Test
    fun `un Mimo ne se tient jamais sur une case refusee`() {
        // Le defaut evident : un Mimo pose sur l'eau, ou dans un mur.
        val places = IslandMimoMondeEngine.placer(
            mimos = listOf(mimo(1, MimoEngine.Type.ARROSEUR)),
            repli = 5 to 5
        ) { x, y -> x >= 10 }
        assertTrue("Mimo pose hors du terrain autorise", places.all { it.x >= 10 })
    }

    @Test
    fun `deux Mimos ne partagent jamais la meme case`() {
        // Sinon ils se superposent et forment une tache qu'on ne sait pas lire.
        val mimos = (1L..6L).map { mimo(it, MimoEngine.Type.ARROSEUR) }
        val places = IslandMimoMondeEngine.placer(mimos, repli = 8 to 8, accueillante = herbePartout())
        val cases = places.map { it.x to it.y }
        assertEquals("Deux Mimos au meme endroit", cases.size, cases.toSet().size)
    }

    @Test
    fun `le placement est deterministe`() {
        // Un Mimo qui change de place a chaque ouverture parait casse.
        val mimos = listOf(mimo(3, MimoEngine.Type.ARROSEUR), mimo(1, MimoEngine.Type.RECOLTEUR))
        val parcelles = listOf(IslandMimoMondeEngine.Parcelle(4, 4, aSoif = true))
        val a = IslandMimoMondeEngine.placer(mimos, parcelles, accueillante = herbePartout(parcelles))
        val b = IslandMimoMondeEngine.placer(
            mimos.reversed(), parcelles, accueillante = herbePartout(parcelles)
        )
        assertEquals("L'ordre de la requete ne doit rien changer", a, b)
    }

    @Test
    fun `les Mimos sont tries par profondeur`() {
        // Meme regle que les arbres : celui du bas passe devant.
        val mimos = (1L..5L).map { mimo(it, MimoEngine.Type.ARROSEUR) }
        val places = IslandMimoMondeEngine.placer(mimos, repli = 15 to 15, accueillante = herbePartout())
        assertEquals(places.map { it.y }.sorted(), places.map { it.y })
    }

    @Test
    fun `sans parcelle les Mimos se replient sur le ponton`() {
        val places = IslandMimoMondeEngine.placer(
            mimos = listOf(mimo(1, MimoEngine.Type.ARROSEUR)),
            repli = 20 to 3,
            accueillante = herbePartout()
        )
        val p = places.single()
        assertTrue("Trop loin du ponton : (${p.x},${p.y})",
            kotlin.math.abs(p.x - 20) <= 2 && kotlin.math.abs(p.y - 3) <= 2)
    }

    @Test
    fun `sans case libre nulle part le placement ne plante pas`() {
        val places = IslandMimoMondeEngine.placer(
            mimos = listOf(mimo(1, MimoEngine.Type.ARROSEUR)),
            repli = 5 to 5
        ) { _, _ -> false }
        assertEquals(1, places.size)
    }

    // --- Cibles ---------------------------------------------------------------

    @Test
    fun `un arroseur vise une parcelle assoiffee`() {
        val parcelles = listOf(
            IslandMimoMondeEngine.Parcelle(10, 10),
            IslandMimoMondeEngine.Parcelle(12, 10, aSoif = true)
        )
        val place = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)), parcelles,
            accueillante = herbePartout(parcelles)
        ).single()
        assertEquals(12 to 10, place.cible)
        assertEquals(MimoMondeEngine.Activite.ARROSE, place.activite)
    }

    @Test
    fun `un recolteur vise une parcelle mure`() {
        val parcelles = listOf(
            IslandMimoMondeEngine.Parcelle(10, 10, aSoif = true),
            IslandMimoMondeEngine.Parcelle(11, 10, prete = true)
        )
        val place = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.RECOLTEUR)), parcelles,
            accueillante = herbePartout(parcelles)
        ).single()
        assertEquals(11 to 10, place.cible)
        assertEquals(MimoMondeEngine.Activite.RECOLTE, place.activite)
    }

    @Test
    fun `un arroseur ignore une plante mure`() {
        // Elle n'a plus besoin d'eau : l'y envoyer ferait croire a un gaspillage,
        // et le moteur de travail ne l'arroserait pas non plus.
        val parcelles = listOf(IslandMimoMondeEngine.Parcelle(9, 9, aSoif = true, prete = true))
        val place = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)), parcelles,
            accueillante = herbePartout(parcelles)
        ).single()
        assertNull(place.cible)
        assertEquals(MimoMondeEngine.Activite.OISIF, place.activite)
    }

    @Test
    fun `deux arroseurs ne visent pas la meme parcelle`() {
        val parcelles = listOf(
            IslandMimoMondeEngine.Parcelle(10, 10, aSoif = true),
            IslandMimoMondeEngine.Parcelle(14, 10, aSoif = true)
        )
        val places = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR), mimo(2, MimoEngine.Type.ARROSEUR)),
            parcelles, accueillante = herbePartout(parcelles)
        )
        val cibles = places.mapNotNull { it.cible }
        assertEquals(2, cibles.size)
        assertEquals("Les deux visent le meme travail", 2, cibles.toSet().size)
    }

    @Test
    fun `un Mimo au travail se tient a cote de sa parcelle jamais dessus`() {
        // Le poser dessus masquerait la culture, c'est-a-dire exactement ce
        // qu'on est venu regarder.
        val parcelles = listOf(IslandMimoMondeEngine.Parcelle(10, 10, aSoif = true))
        val place = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)), parcelles,
            accueillante = herbePartout(parcelles)
        ).single()
        assertFalse("Mimo pose sur la culture", place.x == 10 && place.y == 10)
        assertTrue("Mimo trop loin de sa cible",
            kotlin.math.abs(place.x - 10) <= 1 && kotlin.math.abs(place.y - 10) <= 1)
    }

    @Test
    fun `une parcelle entouree ne bloque pas le placement`() {
        // Cas limite : la cible est valide mais rien n'est libre autour. Le Mimo
        // recule a sa station plutot que de disparaitre.
        val parcelles = (9..11).flatMap { x -> (9..11).map { y ->
            IslandMimoMondeEngine.Parcelle(x, y, aSoif = true)
        } }
        val places = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)), parcelles,
            accueillante = herbePartout(parcelles)
        )
        assertEquals(1, places.size)
        assertNotNull(places.single().cible)
    }

    // --- Metiers sans emploi --------------------------------------------------

    @Test
    fun `les metiers que l'ile n'applique pas restent oisifs`() {
        // L'ile ne connait ni caisses ni marchand : un transporteur n'y fait
        // rien. Le montrer affaire serait un mensonge sur la mecanique.
        val parcelles = listOf(
            IslandMimoMondeEngine.Parcelle(10, 10, aSoif = true, prete = true)
        )
        listOf(
            MimoEngine.Type.TRANSPORTEUR, MimoEngine.Type.VENDEUR, MimoEngine.Type.PLANTEUR
        ).forEach { type ->
            val place = IslandMimoMondeEngine.placer(
                listOf(mimo(1, type)), parcelles, accueillante = herbePartout(parcelles)
            ).single()
            assertNull("$type devrait etre sans cible", place.cible)
            assertEquals(MimoMondeEngine.Activite.OISIF, place.activite)
            assertTrue("$type devrait etre signale sans emploi", place.sansEmploi)
        }
    }

    @Test
    fun `les metiers actifs sont exactement ceux que le travail applique`() {
        // Ce test est le garde-fou du couple affichage/mecanique : si un metier
        // devient utile dans IslandMimoEngine sans etre declare ici, l'ecran
        // continuera de le dire inutile. Et l'inverse promettrait un travail
        // qui n'arrive jamais.
        // Une ile ou tout est a faire : de l'eau a donner et une plante mure.
        val aFaire = listOf(
            IslandMimoEngine.Vue(cle = 1, aSoif = true, prete = false),
            IslandMimoEngine.Vue(cle = 2, aSoif = false, prete = true)
        )

        MimoEngine.Type.entries.forEach { type ->
            val plan = IslandMimoEngine.planifier(
                types = listOf(type),
                minutesEcoulees = 10_000L,
                parcelles = aFaire,
                eauDisponible = 50
            )
            if (type in IslandMimoMondeEngine.METIERS_ACTIFS) {
                assertFalse("$type est declare actif mais ne fait rien", plan.vide)
            } else {
                assertTrue("$type fait quelque chose mais est declare inutile", plan.vide)
            }
        }
    }

    @Test
    fun `un metier sans emploi n'est pas signale pour les autres`() {
        val place = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR)), accueillante = herbePartout()
        ).single()
        assertFalse(place.sansEmploi)
    }

    // --- Nuit -----------------------------------------------------------------

    @Test
    fun `la nuit tout le monde dort et personne ne vise rien`() {
        val parcelles = listOf(IslandMimoMondeEngine.Parcelle(10, 10, aSoif = true))
        val places = IslandMimoMondeEngine.placer(
            listOf(mimo(1, MimoEngine.Type.ARROSEUR), mimo(2, MimoEngine.Type.RECOLTEUR)),
            parcelles, faitJour = false, accueillante = herbePartout(parcelles)
        )
        assertTrue(places.all { it.endormi })
        assertTrue(places.all { it.cible == null })
    }

    @Test
    fun `les dormeurs restent groupes autour du foyer`() {
        // Ils dorment a leur station, pas eparpilles sur l'ile entiere.
        val parcelles = listOf(IslandMimoMondeEngine.Parcelle(16, 16))
        val places = IslandMimoMondeEngine.placer(
            (1L..4L).map { mimo(it, MimoEngine.Type.ARROSEUR) },
            parcelles, faitJour = false, accueillante = herbePartout(parcelles)
        )
        assertTrue("Un dormeur s'est eloigne", places.all {
            kotlin.math.abs(it.x - 16) <= 3 && kotlin.math.abs(it.y - 16) <= 3
        })
    }
}
