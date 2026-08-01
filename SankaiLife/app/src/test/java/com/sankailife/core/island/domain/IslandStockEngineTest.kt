package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.ALL_SEEDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandStockEngineTest {

    private val graine = ALL_SEEDS.first { it.id == "tournesol" }

    // --- Capacite -------------------------------------------------------------

    @Test
    fun `le depot augmente la capacite sans etre obligatoire`() {
        assertTrue(IslandStockEngine.capacite(aDepot = false) > 0)
        assertTrue(
            IslandStockEngine.capacite(aDepot = true) >
                IslandStockEngine.capacite(aDepot = false)
        )
    }

    // --- Prix -----------------------------------------------------------------

    @Test
    fun `on peut toujours vendre, meme sans Boutique`() {
        // C'est le piege que ce moteur existe pour eviter : si les recoltes ne
        // se monnayaient qu'a la Boutique, et que la Boutique coute des pieces,
        // un joueur qui commence ne pourrait jamais en gagner.
        assertTrue(IslandStockEngine.prixUnitaire(graine, aBoutique = false) > 0)
    }

    @Test
    fun `la Boutique ameliore le prix`() {
        assertTrue(
            IslandStockEngine.prixUnitaire(graine, aBoutique = true) >
                IslandStockEngine.prixUnitaire(graine, aBoutique = false)
        )
    }

    @Test
    fun `la valeur totale suit la quantite`() {
        val unitaire = IslandStockEngine.prixUnitaire(graine, aBoutique = true)
        assertEquals(unitaire * 7, IslandStockEngine.valeurTotale(graine, 7, true))
        assertEquals(0, IslandStockEngine.valeurTotale(graine, 0, true))
        assertEquals(0, IslandStockEngine.valeurTotale(graine, -4, true))
    }

    // --- Rangement ------------------------------------------------------------

    @Test
    fun `une recolte entre en stock quand il y a de la place`() {
        val d = IslandStockEngine.ranger(graine, 3, stockActuel = 0, capacite = 20, aBoutique = false)
        assertEquals(3, d.entrepose)
        assertEquals(0, d.vendueDOffice)
        assertEquals(0, d.pieces)
    }

    @Test
    fun `un stock plein vend le surplus au lieu de le perdre`() {
        // Perdre une recolte punirait quelqu'un qui a bien joue : il a seme,
        // arrose et attendu.
        val d = IslandStockEngine.ranger(graine, 5, stockActuel = 18, capacite = 20, aBoutique = false)
        assertEquals(2, d.entrepose)
        assertEquals(3, d.vendueDOffice)
        assertEquals(3 * graine.rendementPieces, d.pieces)
    }

    @Test
    fun `un stock deja plein vend tout`() {
        val d = IslandStockEngine.ranger(graine, 4, stockActuel = 20, capacite = 20, aBoutique = false)
        assertEquals(0, d.entrepose)
        assertEquals(4, d.vendueDOffice)
        assertTrue(d.pieces > 0)
    }

    @Test
    fun `le surplus ne profite jamais du bonus de la Boutique`() {
        // Ce n'est pas une vente choisie : la recompenser comme telle
        // supprimerait toute raison d'agrandir le depot.
        val avec = IslandStockEngine.ranger(graine, 4, 20, 20, aBoutique = true)
        val sans = IslandStockEngine.ranger(graine, 4, 20, 20, aBoutique = false)
        assertEquals(sans.pieces, avec.pieces)
    }

    @Test
    fun `une capacite incoherente ne rend jamais de negatif`() {
        val d = IslandStockEngine.ranger(graine, 3, stockActuel = 50, capacite = 20, aBoutique = false)
        assertEquals(0, d.entrepose)
        assertEquals(3, d.vendueDOffice)
    }

    @Test
    fun `une quantite nulle ou negative ne fait rien`() {
        listOf(0, -5).forEach { q ->
            val d = IslandStockEngine.ranger(graine, q, 0, 20, false)
            assertEquals(0, d.entrepose)
            assertEquals(0, d.vendueDOffice)
            assertEquals(0, d.pieces)
        }
    }

    // --- Message --------------------------------------------------------------

    @Test
    fun `la vente forcee est toujours annoncee`() {
        // Vendre sans le dire ferait croire a une recolte disparue.
        val plein = IslandStockEngine.ranger(graine, 3, 20, 20, false)
        assertTrue(IslandStockEngine.resumeRecolte(graine, plein).contains("vendue"))

        val partiel = IslandStockEngine.ranger(graine, 3, 19, 20, false)
        val message = IslandStockEngine.resumeRecolte(graine, partiel)
        assertTrue(message.contains("Stock plein"))
        assertTrue(message.contains("🪙"))
    }

    @Test
    fun `une recolte rangee sans surplus ne parle pas de vente`() {
        val normal = IslandStockEngine.ranger(graine, 2, 0, 20, false)
        assertTrue(!IslandStockEngine.resumeRecolte(graine, normal).contains("vendue"))
    }
}
