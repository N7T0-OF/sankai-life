package com.sankailife.core.island.domain

import com.sankailife.core.island.domain.IslandBuildingEngine.Type
import com.sankailife.core.island.domain.IslandBuildingEngine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandBuildingEngineTest {

    /** Terrain d'essai : herbe partout, sauf ce qu'on remplace explicitement. */
    private fun terrain(
        exceptions: Map<Pair<Int, Int>, IslandTileType> = emptyMap(),
        taille: Int = 10
    ): (Int, Int) -> IslandTileType? = { x, y ->
        when {
            x !in 0 until taille || y !in 0 until taille -> null
            else -> exceptions[x to y] ?: IslandTileType.GRASS
        }
    }

    private fun batir(
        type: Type = Type.BOUTIQUE,
        x: Int = 2, y: Int = 2,
        niveau: Int = 10,
        pieces: Int = 10_000,
        dejaConstruit: Boolean = false,
        terrainDe: (Int, Int) -> IslandTileType? = terrain(),
        occupee: (Int, Int) -> Boolean = { _, _ -> false }
    ) = IslandBuildingEngine.peutBatir(
        type, x, y, niveau, pieces, dejaConstruit, terrainDe, occupee
    )

    // --- Emprise --------------------------------------------------------------

    @Test
    fun `un batiment deux par deux occupe quatre cases`() {
        val cases = IslandBuildingEngine.casesOccupees(Type.BOUTIQUE, 5, 7)
        assertEquals(setOf(5 to 7, 6 to 7, 5 to 8, 6 to 8), cases.toSet())
    }

    @Test
    fun `le centre d'une emprise paire tombe sur la frontiere`() {
        // Un batiment 2x2 se dessine a l'intersection des quatre cases, pas au
        // milieu de l'une d'elles. Meme regle que pour l'Arbre Sankai.
        val (cx, cy) = IslandBuildingEngine.centre(Type.BOUTIQUE, 4, 6)
        assertEquals(5f, cx, 0.001f)
        assertEquals(7f, cy, 0.001f)
    }

    @Test
    fun `les cles suivent la largeur de l'ile`() {
        val cles = IslandBuildingEngine.clesOccupees(Type.DEPOT, 1, 1, largeurIle = 32)
        assertEquals(setOf(33, 34, 65, 66), cles)
    }

    // --- Refus ----------------------------------------------------------------

    @Test
    fun `on ne batit pas sur l'eau`() {
        val v = batir(terrainDe = terrain(mapOf((3 to 3) to IslandTileType.DEEP_WATER)))
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("eau"))
    }

    @Test
    fun `une seule case d'eau suffit a refuser`() {
        // L'emprise entiere doit tenir : trois cases sur quatre ne suffisent
        // pas, et le dire evite de faire chercher laquelle bloque.
        listOf(2 to 2, 3 to 2, 2 to 3, 3 to 3).forEach { (cx, cy) ->
            val v = batir(terrainDe = terrain(mapOf((cx to cy) to IslandTileType.RIVER)))
            assertTrue("Case ($cx,$cy) devrait bloquer", v is Verdict.Non)
        }
    }

    @Test
    fun `un batiment ne depasse pas du bord de l'ile`() {
        val v = batir(x = 9, y = 9, terrainDe = terrain(taille = 10))
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("bord"))
    }

    @Test
    fun `un terrain non constructible est refuse`() {
        val v = batir(terrainDe = terrain(mapOf((2 to 2) to IslandTileType.FOREST)))
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("dégagé"))
    }

    @Test
    fun `une case occupee bloque la construction`() {
        val v = batir(occupee = { x, y -> x == 3 && y == 3 })
        assertTrue(v is Verdict.Non)
    }

    @Test
    fun `un second exemplaire est refuse`() {
        val v = batir(dejaConstruit = true)
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("déjà"))
    }

    @Test
    fun `le niveau est exige avant l'argent`() {
        // On annonce d'abord ce qui demande du temps, pas ce qui demande des
        // pieces : sinon le joueur economise pour rien.
        val v = batir(type = Type.DEPOT, niveau = 1, pieces = 0)
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("niveau"))
    }

    @Test
    fun `le terrain prime sur le niveau et sur l'argent`() {
        val v = batir(
            niveau = 1, pieces = 0,
            terrainDe = terrain(mapOf((2 to 2) to IslandTileType.DEEP_WATER))
        )
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("eau"))
    }

    @Test
    fun `des pieces insuffisantes disent combien il manque`() {
        val v = batir(pieces = 10)
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("manque"))
    }

    @Test
    fun `un emplacement valide rend le prix`() {
        val v = batir()
        assertTrue(v is Verdict.Oui)
        assertEquals(Type.BOUTIQUE.prix, (v as Verdict.Oui).prix)
    }

    // --- Accessibilite --------------------------------------------------------

    @Test
    fun `un batiment entoure d'eau n'est pas accessible`() {
        // On peut techniquement le poser sur une ile-caillou ; on ne pourrait
        // plus jamais y entrer.
        assertFalse(
            IslandBuildingEngine.accessible(Type.BOUTIQUE, 2, 2) { _, _ -> false }
        )
    }

    @Test
    fun `une seule case franchissable adjacente suffit`() {
        assertTrue(
            IslandBuildingEngine.accessible(Type.BOUTIQUE, 2, 2) { x, y -> x == 1 && y == 2 }
        )
    }

    @Test
    fun `les cases de l'emprise ne comptent pas comme un acces`() {
        // Sinon tout batiment serait declare accessible par lui-meme.
        assertFalse(
            IslandBuildingEngine.accessible(Type.BOUTIQUE, 2, 2) { x, y ->
                x in 2..3 && y in 2..3
            }
        )
    }

    // --- Contrainte cotiere ---------------------------------------------------

    @Test
    fun `le Port doit toucher l'eau`() {
        // Un port au milieu des terres n'aurait aucun sens, meme sur un
        // emplacement par ailleurs valide.
        val v = batir(type = Type.PORT, terrainDe = terrain(taille = 10))
        assertTrue(v is Verdict.Non)
        assertTrue((v as Verdict.Non).raison.contains("eau"))
    }

    @Test
    fun `le Port est accepte au bord de l'eau`() {
        val cote = terrain(
            (0 until 10).associate { (it to 5) to IslandTileType.SHALLOW_WATER },
            taille = 10
        )
        assertTrue(batir(type = Type.PORT, x = 2, y = 3, terrainDe = cote) is Verdict.Oui)
    }

    @Test
    fun `l'eau sous l'emprise ne compte pas comme une cote`() {
        // Une case mouillee dans l'emprise fait echouer plus tot : c'est bien
        // le voisinage qui doit etre mouille.
        val dedans = terrain(mapOf((2 to 2) to IslandTileType.SHALLOW_WATER), taille = 10)
        assertTrue(batir(type = Type.PORT, x = 2, y = 2, terrainDe = dedans) is Verdict.Non)
    }

    @Test
    fun `les batiments non cotiers ne sont pas soumis a cette regle`() {
        Type.entries.filter { !it.cotier }.forEach { t ->
            assertTrue(
                "$t refuse loin de l'eau",
                batir(type = t, x = 2, y = 2, terrainDe = terrain(taille = 10)) is Verdict.Oui
            )
        }
    }

    @Test
    fun `le Port occupe trois cases sur deux`() {
        assertEquals(6, IslandBuildingEngine.casesOccupees(Type.PORT, 0, 0).size)
        assertEquals(3, Type.PORT.largeur)
        assertEquals(2, Type.PORT.hauteur)
    }

    // --- Catalogue ------------------------------------------------------------

    @Test
    fun `chaque type se retrouve par son identifiant`() {
        Type.entries.forEach { assertEquals(it, Type.parId(it.id)) }
        assertEquals(null, Type.parId("serre"))
    }
}
