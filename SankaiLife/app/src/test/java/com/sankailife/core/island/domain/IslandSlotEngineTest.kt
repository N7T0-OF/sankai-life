package com.sankailife.core.island.domain

import com.sankailife.core.island.domain.IslandSlotEngine.Etat
import com.sankailife.core.island.domain.IslandSlotEngine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandSlotEngineTest {

    private fun acheter(
        type: IslandTileType = IslandTileType.GRASS,
        dejaAchetee: Boolean = false,
        occupee: Boolean = false,
        niveau: Int = 10,
        possedees: Int = 5,
        pieces: Int = 100_000
    ) = IslandSlotEngine.peutAcheter(type, dejaAchetee, occupee, niveau, possedees, pieces)

    // --- Terrain --------------------------------------------------------------

    @Test
    fun `on ne vend jamais la mer ni la riviere`() {
        listOf(
            IslandTileType.DEEP_WATER, IslandTileType.SHALLOW_WATER,
            IslandTileType.RIVER, IslandTileType.POND
        ).forEach {
            assertTrue("$it devrait etre refuse", acheter(type = it) is Verdict.Non)
        }
    }

    @Test
    fun `la plage et le ponton ne se cultivent pas`() {
        // La plage donne la forme de l'ile. La couvrir de champs la ferait
        // disparaitre ; le ponton est le seul acces.
        assertTrue(acheter(type = IslandTileType.BEACH) is Verdict.Non)
        assertTrue(acheter(type = IslandTileType.DOCK) is Verdict.Non)
    }

    @Test
    fun `chaque refus de terrain donne une raison lisible`() {
        // « Impossible » ne fait pas comprendre qu'une plage se batit mais ne
        // se plante pas.
        listOf(
            IslandTileType.DEEP_WATER, IslandTileType.RIVER,
            IslandTileType.BEACH, IslandTileType.DOCK
        ).forEach {
            val v = acheter(type = it)
            assertTrue(v is Verdict.Non)
            val raison = (v as Verdict.Non).raison
            assertTrue("Raison vide pour $it", raison.length > 10)
            assertTrue("Raison peu explicite pour $it", !raison.equals("Impossible", true))
        }
    }

    @Test
    fun `la foret et le rocher sont vendables, moins cher`() {
        // Ce sont les seules parcelles bon marche ; les refuser priverait un
        // debutant de tout ce qu'il peut se payer.
        assertTrue(acheter(type = IslandTileType.FOREST) is Verdict.Oui)
        assertTrue(acheter(type = IslandTileType.ROCK) is Verdict.Oui)
        assertTrue(
            IslandSlotEngine.prixBase(IslandTileType.ROCK) <
                IslandSlotEngine.prixBase(IslandTileType.GRASS)
        )
    }

    @Test
    fun `la terre grasse coute plus cher que la plaine`() {
        assertTrue(
            IslandSlotEngine.prixBase(IslandTileType.FERTILE_GRASS) >
                IslandSlotEngine.prixBase(IslandTileType.GRASS)
        )
    }

    // --- Prix -----------------------------------------------------------------

    @Test
    fun `les premieres parcelles sont gratuites`() {
        // Demander de l'argent a quelqu'un qui n'en a pas encore gagne
        // bloquerait la premiere session.
        assertEquals(0, IslandSlotEngine.prix(IslandTileType.GRASS, 0))
        assertEquals(0, IslandSlotEngine.prix(IslandTileType.FERTILE_GRASS, 0))
        assertTrue(IslandSlotEngine.prix(IslandTileType.GRASS, IslandSlotEngine.GRATUITES) > 0)
    }

    @Test
    fun `le prix ne redescend jamais quand on possede davantage`() {
        var precedent = -1
        for (n in 0..40) {
            val p = IslandSlotEngine.prix(IslandTileType.GRASS, n)
            assertTrue("Prix en baisse a $n parcelles : $precedent puis $p", p >= precedent)
            precedent = p
        }
    }

    @Test
    fun `la terre grasse reste plus chere a tout moment de la partie`() {
        // C'est ce que garantit le choix « le terrain fixe la base, la
        // progression multiplie » plutot que deux baremes concurrents.
        for (n in IslandSlotEngine.GRATUITES..40) {
            assertTrue(
                "Egalite ou inversion a $n parcelles",
                IslandSlotEngine.prix(IslandTileType.FERTILE_GRASS, n) >
                    IslandSlotEngine.prix(IslandTileType.GRASS, n)
            )
        }
    }

    @Test
    fun `un nombre de parcelles negatif ne casse pas le prix`() {
        assertEquals(0, IslandSlotEngine.prix(IslandTileType.GRASS, -5))
    }

    @Test
    fun `les prix sont arrondis a la dizaine`() {
        for (n in 0..30) {
            assertEquals(0, IslandSlotEngine.prix(IslandTileType.FERTILE_GRASS, n) % 10)
        }
    }

    // --- Plafond par niveau ---------------------------------------------------

    @Test
    fun `le plafond suit les paliers annonces`() {
        assertEquals(4, IslandSlotEngine.plafond(1))
        assertEquals(6, IslandSlotEngine.plafond(2))
        assertEquals(9, IslandSlotEngine.plafond(3))
        assertEquals(14, IslandSlotEngine.plafond(5))
        assertEquals(22, IslandSlotEngine.plafond(8))
        assertEquals(32, IslandSlotEngine.plafond(12))
        assertEquals(50, IslandSlotEngine.plafond(20))
    }

    @Test
    fun `aucun niveau intermediaire n'est sterile`() {
        // Gagner un niveau sans rien debloquer decourage. Les paliers annonces
        // sautent les niveaux 4, 6, 7, 9, 10, 11… : ils sont interpoles.
        for (n in 1 until 20) {
            assertTrue(
                "Le niveau ${n + 1} n'apporte aucune parcelle",
                IslandSlotEngine.plafond(n + 1) > IslandSlotEngine.plafond(n)
            )
        }
    }

    @Test
    fun `le plafond ne descend jamais et se borne aux extremes`() {
        assertEquals(4, IslandSlotEngine.plafond(0))
        assertEquals(4, IslandSlotEngine.plafond(-3))
        assertEquals(50, IslandSlotEngine.plafond(999))
    }

    // --- Achat ----------------------------------------------------------------

    @Test
    fun `le plafond atteint refuse l'achat en expliquant pourquoi`() {
        val v = acheter(niveau = 1, possedees = 4)
        assertTrue(v is Verdict.Non)
        val raison = (v as Verdict.Non).raison
        assertTrue("La raison devrait citer la limite", raison.contains("4"))
        assertTrue("La raison devrait orienter vers la revision", raison.contains("Révise"))
    }

    @Test
    fun `des pieces insuffisantes disent combien il manque`() {
        val v = acheter(possedees = 20, pieces = 5)
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("manque"))
    }

    @Test
    fun `une case occupee ou deja achetee est refusee`() {
        assertTrue(acheter(occupee = true) is Verdict.Non)
        assertTrue(acheter(dejaAchetee = true) is Verdict.Non)
    }

    @Test
    fun `le terrain prime sur le niveau et sur l'argent`() {
        // On dit d'abord ce qui est definitif. Annoncer « il te manque des
        // pieces » sur de l'eau ferait croire qu'on pourra l'acheter un jour.
        val v = IslandSlotEngine.peutAcheter(
            IslandTileType.DEEP_WATER,
            dejaAchetee = false, occupee = false,
            niveauJoueur = 1, parcellesPossedees = 99, pieces = 0
        )
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("mer"))
    }

    @Test
    fun `un achat valide rend le prix exact`() {
        val v = acheter(type = IslandTileType.GRASS, possedees = 8, pieces = 100_000)
        assertTrue(v is Verdict.Oui)
        assertEquals(
            IslandSlotEngine.prix(IslandTileType.GRASS, 8),
            (v as Verdict.Oui).prix
        )
    }

    // --- Etat affiche ---------------------------------------------------------

    @Test
    fun `naturel et verrouille ne se confondent pas`() {
        // Le verrouille finira par s'ouvrir, le naturel jamais. Les afficher
        // pareil laisserait croire que l'ocean deviendra un champ.
        val eau = IslandSlotEngine.etat(
            IslandTileType.DEEP_WATER, false, false, false, niveauJoueur = 20,
            parcellesPossedees = 0
        )
        val herbe = IslandSlotEngine.etat(
            IslandTileType.GRASS, false, false, false, niveauJoueur = 1,
            parcellesPossedees = 4
        )
        assertEquals(Etat.NATUREL, eau)
        assertEquals(Etat.VERROUILLE, herbe)
    }

    @Test
    fun `l'etat suit l'occupation avant tout le reste`() {
        assertEquals(
            Etat.OCCUPEE,
            IslandSlotEngine.etat(IslandTileType.GRASS, true, true, true, 10, 0)
        )
        assertEquals(
            Etat.CULTIVEE,
            IslandSlotEngine.etat(IslandTileType.GRASS, true, false, true, 10, 0)
        )
        assertEquals(
            Etat.ACHETEE,
            IslandSlotEngine.etat(IslandTileType.GRASS, true, false, false, 10, 0)
        )
        assertEquals(
            Etat.DISPONIBLE,
            IslandSlotEngine.etat(IslandTileType.GRASS, false, false, false, 10, 0)
        )
    }

    // --- Coherence avec le generateur -----------------------------------------

    @Test
    fun `une ile generee offre assez de parcelles achetables pour le niveau 20`() {
        // Un plafond de 50 parcelles serait une promesse creuse si l'ile n'en
        // proposait pas autant.
        repeat(10) { i ->
            val (ile, _) = IslandGenerator.genererJouable(i * 1_299_709L + 17L)
            val achetables = ile.compter { IslandSlotEngine.terrainAchetable(it) }
            assertTrue(
                "seed $i : seulement $achetables cases achetables",
                achetables >= IslandSlotEngine.plafond(20)
            )
        }
    }
}
