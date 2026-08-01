package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.MimoEngine
import com.sankailife.core.island.domain.IslandMimoEngine.Vue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandMimoEngineTest {

    private fun parcelles(nombre: Int, soif: Boolean = false, prete: Boolean = false) =
        (0 until nombre).map { Vue(cle = it, aSoif = soif, prete = prete) }

    // --- Sans Mimo ------------------------------------------------------------

    @Test
    fun `sans Mimo rien ne se fait`() {
        val plan = IslandMimoEngine.planifier(
            emptyList(), 10_000L, parcelles(10, soif = true, prete = true), eauDisponible = 100
        )
        assertTrue(plan.vide)
        assertNull(IslandMimoEngine.resume(plan))
    }

    @Test
    fun `sans temps ecoule rien ne se fait`() {
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 0L, parcelles(5, soif = true), eauDisponible = 100
        )
        assertTrue(plan.vide)
    }

    // --- Eau ------------------------------------------------------------------

    @Test
    fun `un Mimo consomme l'eau du joueur`() {
        // Le laisser arroser gratuitement romprait le lien entre reviser et
        // faire pousser, qui est tout l'interet de l'application.
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L,
            parcelles(5, soif = true), eauDisponible = 100
        )
        assertEquals(plan.aArroser.size, plan.eauConsommee)
        assertTrue(plan.eauConsommee > 0)
    }

    @Test
    fun `un Mimo ne creuse jamais de dette d'eau`() {
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L,
            parcelles(20, soif = true), eauDisponible = 3
        )
        assertEquals(3, plan.aArroser.size)
    }

    @Test
    fun `sans eau aucun arrosage`() {
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L,
            parcelles(10, soif = true), eauDisponible = 0
        )
        assertTrue(plan.aArroser.isEmpty())
    }

    @Test
    fun `une eau negative ne fait pas planter le calcul`() {
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L,
            parcelles(10, soif = true), eauDisponible = -5
        )
        assertTrue(plan.aArroser.isEmpty())
    }

    // --- Ordre ----------------------------------------------------------------

    @Test
    fun `une plante prete n'est pas arrosee`() {
        // Une plante mure n'a plus besoin d'eau : l'arroser gaspillerait une
        // goutte gagnee en revisant.
        val mures = (0 until 5).map { Vue(it, aSoif = true, prete = true) }
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR, MimoEngine.Type.RECOLTEUR),
            100_000L, mures, eauDisponible = 100
        )
        assertTrue(plan.aArroser.isEmpty())
        assertTrue(plan.aRecolter.isNotEmpty())
    }

    @Test
    fun `chaque Mimo ne fait que son metier`() {
        val vues = listOf(Vue(1, aSoif = true, prete = false), Vue(2, aSoif = false, prete = true))

        val arroseur = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L, vues, 100
        )
        assertEquals(listOf(1), arroseur.aArroser)
        assertTrue(arroseur.aRecolter.isEmpty())

        val recolteur = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.RECOLTEUR), 100_000L, vues, 100
        )
        assertEquals(listOf(2), recolteur.aRecolter)
        assertTrue(recolteur.aArroser.isEmpty())
    }

    // --- Plafond --------------------------------------------------------------

    @Test
    fun `une longue absence ne vide pas l'ile d'un coup`() {
        // Revenir apres trois semaines ne doit pas recolter cinquante parcelles
        // ni vider la reserve : un rattrapage demesure se lit comme un bug,
        // meme quand il est merite.
        val troisSemaines = 21L * 24 * 60
        val actions = IslandMimoEngine.actions(MimoEngine.Type.ARROSEUR, troisSemaines)
        assertEquals(IslandMimoEngine.PLAFOND_ACTIONS, actions)
    }

    @Test
    fun `une absence courte ne produit aucune action`() {
        // La cadence doit etre atteinte : dix minutes ne suffisent pas.
        assertEquals(0, IslandMimoEngine.actions(MimoEngine.Type.ARROSEUR, 10L))
    }

    @Test
    fun `un temps negatif ne produit rien`() {
        assertEquals(0, IslandMimoEngine.actions(MimoEngine.Type.RECOLTEUR, -500L))
    }

    @Test
    fun `deux Mimos du meme metier travaillent deux fois plus`() {
        val un = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 200L, parcelles(20, soif = true), 100
        )
        val deux = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR, MimoEngine.Type.ARROSEUR),
            200L, parcelles(20, soif = true), 100
        )
        assertEquals(un.aArroser.size * 2, deux.aArroser.size)
    }

    // --- Compte rendu ---------------------------------------------------------

    @Test
    fun `le compte rendu annonce l'eau depensee`() {
        // Decouvrir une reserve vide sans explication ferait croire a une perte.
        val plan = IslandMimoEngine.planifier(
            listOf(MimoEngine.Type.ARROSEUR), 100_000L, parcelles(4, soif = true), 100
        )
        val resume = IslandMimoEngine.resume(plan)!!
        assertTrue(resume.contains("💧"))
        assertTrue(resume.contains("arros"))
    }

    @Test
    fun `un plan vide ne dit rien`() {
        assertNull(IslandMimoEngine.resume(IslandMimoEngine.Plan()))
    }
}
