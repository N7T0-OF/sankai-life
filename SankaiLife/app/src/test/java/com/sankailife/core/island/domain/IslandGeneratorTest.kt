package com.sankailife.core.island.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandGeneratorTest {

    // --- Determinisme -------------------------------------------------------

    @Test
    fun `la meme graine rend exactement la meme ile`() {
        // C'est la promesse qui rend une île partageable par un simple code.
        val a = IslandGenerator.generer(123_456L)
        val b = IslandGenerator.generer(123_456L)
        assertEquals(a.tuiles, b.tuiles)
        assertEquals(a.ponton, b.ponton)
        assertEquals(a.zoneDepart, b.zoneDepart)
    }

    @Test
    fun `deux graines differentes rendent deux iles differentes`() {
        val a = IslandGenerator.generer(1L)
        val b = IslandGenerator.generer(2L)
        assertTrue("Deux graines ne devraient pas produire la meme carte", a.tuiles != b.tuiles)
    }

    @Test
    fun `la generation jouable reste reproductible`() {
        // Le rejet de graine ne doit pas introduire de hasard : la suite
        // essayée ne depend que de la graine initiale.
        val (a, _) = IslandGenerator.genererJouable(999L)
        val (b, _) = IslandGenerator.genererJouable(999L)
        assertEquals(a.seed, b.seed)
        assertEquals(a.tuiles, b.tuiles)
    }

    // --- Forme --------------------------------------------------------------

    @Test
    fun `l'ile ne touche jamais le bord de la carte`() {
        // Sinon la promesse « entouree d'eau de tous les cotes » tombe, et le
        // joueur voit la fin du decor.
        repeat(40) { i ->
            val ile = IslandGenerator.generer(i * 7_919L + 13L)
            for (x in 0 until ile.largeur) {
                assertTrue("seed $i : terre en haut", ile.type(x, 0).estEau)
                assertTrue("seed $i : terre en bas", ile.type(x, ile.hauteur - 1).estEau)
            }
            for (y in 0 until ile.hauteur) {
                assertTrue("seed $i : terre a gauche", ile.type(0, y).estEau)
                assertTrue("seed $i : terre a droite", ile.type(ile.largeur - 1, y).estEau)
            }
        }
    }

    @Test
    fun `chaque ile a de la terre et de l'eau`() {
        repeat(30) { i ->
            val ile = IslandGenerator.generer(i * 4_211L + 5L)
            assertTrue("seed $i : aucune terre", ile.compter { it.estTerre } > 0)
            assertTrue("seed $i : aucune eau", ile.compter { it.estEau } > 0)
        }
    }

    @Test
    fun `une coordonnee hors carte rend de l'eau profonde plutot qu'une erreur`() {
        val ile = IslandGenerator.generer(42L)
        assertEquals(IslandTileType.DEEP_WATER, ile.type(-1, 5))
        assertEquals(IslandTileType.DEEP_WATER, ile.type(5, -1))
        assertEquals(IslandTileType.DEEP_WATER, ile.type(9_999, 5))
    }

    @Test
    fun `une taille trop petite est refusee`() {
        // Sous seize cases, il ne reste pas de place pour une plage : mieux
        // vaut refuser que produire un caillou.
        assertTrue(runCatching { IslandGenerator.generer(1L, taille = 8) }.isFailure)
    }

    // --- Jouabilite ---------------------------------------------------------

    @Test
    fun `cent graines produisent une ile jouable`() {
        // Le test qui compte. Une graine sur cinquante qui echoue, c'est un
        // joueur sur cinquante qui abandonne au premier lancement.
        val echecs = mutableListOf<String>()
        repeat(100) { i ->
            val (ile, rapport) = IslandGenerator.genererJouable(i * 104_729L + 7L)
            if (!rapport.jouable) {
                echecs += "graine ${ile.seed} : ${rapport.manques.joinToString("; ")}"
            }
        }
        assertTrue(
            "${echecs.size} graines injouables sur 100 :\n" + echecs.take(5).joinToString("\n"),
            echecs.isEmpty()
        )
    }

    @Test
    fun `chaque ile jouable a un ponton et une zone de depart`() {
        repeat(30) { i ->
            val (ile, _) = IslandGenerator.genererJouable(i * 31_337L + 3L)
            assertNotNull("seed $i : pas de ponton", ile.ponton)
            assertNotNull("seed $i : pas de zone de depart", ile.zoneDepart)
        }
    }

    @Test
    fun `la zone de depart est entierement cultivable`() {
        // Le joueur doit pouvoir planter tout de suite, sans rien degager.
        repeat(30) { i ->
            val (ile, _) = IslandGenerator.genererJouable(i * 15_485_863L + 11L)
            val z = ile.zoneDepart!!
            for (dy in 0 until 4) {
                for (dx in 0 until 4) {
                    assertTrue(
                        "seed $i : case (${z.x + dx},${z.y + dy}) non cultivable",
                        ile.type(z.x + dx, z.y + dy).cultivable
                    )
                }
            }
        }
    }

    @Test
    fun `le ponton est pose sur la cote`() {
        repeat(20) { i ->
            val (ile, _) = IslandGenerator.genererJouable(i * 27_644_437L + 2L)
            val p = ile.ponton!!
            assertEquals(IslandTileType.DOCK, ile.type(p.x, p.y))
            val bordeEau = listOf(
                ile.type(p.x - 1, p.y), ile.type(p.x + 1, p.y),
                ile.type(p.x, p.y - 1), ile.type(p.x, p.y + 1)
            ).any { it.estEau }
            assertTrue("seed $i : ponton sans acces a l'eau", bordeEau)
        }
    }

    @Test
    fun `la generation jouable rend toujours une ile, meme en cas d'echec`() {
        // Une boucle non bornee gelerait l'application au premier lancement,
        // c'est-a-dire au pire moment possible.
        val (ile, rapport) = IslandGenerator.genererJouable(7L, maxTentatives = 1)
        assertNotNull(ile)
        assertNotNull(rapport)
        assertEquals(IslandGenerator.TAILLE_DEFAUT, ile.largeur)
    }

    // --- Variete : le defaut que la suite verte laissait passer ---------------

    @Test
    fun `le ponton ne se pose pas toujours au meme endroit`() {
        // Defaut reel du premier jet : le balayage prenait la premiere plage
        // rencontree, donc toujours vers le haut de la carte. Toutes les iles
        // avaient leur ponton en (15,3), et la ferme de depart avec.
        val pontons = (0 until 25)
            .map { IslandGenerator.genererJouable(it * 2_654_435_761L + 1L).first.ponton }
        assertTrue("Un ponton manque", pontons.all { it != null })
        val distincts = pontons.toSet().size
        assertTrue("Seulement $distincts pontons distincts sur 25", distincts >= 15)
    }

    @Test
    fun `deux iles n'ont pas la meme silhouette`() {
        // Une attenuation radiale nette produit toujours un disque : le bruit
        // ne fait que grignoter le bord, et toutes les graines se ressemblent.
        //
        // La mesure porte sur la silhouette de terre, pas sur la grille
        // entiere. Rapporter les differences au nombre total de cases laissait
        // l'ocean commun diluer le resultat : agrandir la carte faisait
        // mecaniquement baisser le pourcentage sans que les iles se
        // ressemblent davantage. Deux formes sont comparees sur leur union,
        // ce qui ne depend plus de la taille du monde.
        val iles = (0 until 8).map { IslandGenerator.genererJouable(it * 8_191L + 5L).first }
        for (i in iles.indices) {
            for (j in i + 1 until iles.size) {
                val a = iles[i]
                val b = iles[j]
                var union = 0
                var differences = 0
                a.tuiles.indices.forEach { k ->
                    val terreA = a.tuiles[k].estTerre
                    val terreB = b.tuiles[k].estTerre
                    if (terreA || terreB) union++
                    if (terreA != terreB) differences++
                }
                val part = if (union == 0) 0f else differences.toFloat() / union
                assertTrue(
                    "Iles $i et $j : silhouettes communes a ${((1 - part) * 100).toInt()} %",
                    part > 0.15f
                )
            }
        }
    }

    @Test
    fun `la zone de depart ne se pose pas toujours au meme endroit`() {
        val departs = (0 until 25)
            .map { IslandGenerator.genererJouable(it * 5_915_587_277L + 3L).first.zoneDepart }
        assertTrue("Une zone de depart manque", departs.all { it != null })
        assertTrue("Trop peu de zones de depart distinctes", departs.toSet().size >= 12)
    }

    // --- Contenu --------------------------------------------------------------

    @Test
    fun `l'ile n'est pas saturee d'obstacles`() {
        // Une ile couverte de rochers se lit comme une corvee de nettoyage
        // avant de pouvoir jouer.
        repeat(20) { i ->
            val (ile, _) = IslandGenerator.genererJouable(i * 6_700_417L + 19L)
            val terres = ile.compter { it.estTerre }.toFloat()
            val obstacles = ile.compter {
                it == IslandTileType.ROCK || it == IslandTileType.FOREST
            }
            assertTrue(
                "seed $i : ${(obstacles / terres * 100).toInt()} % d'obstacles",
                obstacles / terres < 0.45f
            )
        }
    }

    @Test
    fun `la version du generateur est exposee`() {
        // Elle sera lue pour ne jamais regenerer l'ile d'un joueur lors d'une
        // mise a jour du generateur.
        assertEquals(IslandGenerator.VERSION, IslandGenerator.generer(1L).version)
    }
}
