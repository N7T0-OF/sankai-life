package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionEngineTest {

    private val centre = ExpansionEngine.cle(ExpansionEngine.CENTRE, ExpansionEngine.CENTRE)

    @Test
    fun `la cle et les coordonnees sont reversibles`() {
        for (x in 0 until ExpansionEngine.COTE step 7) {
            for (y in 0 until ExpansionEngine.COTE step 5) {
                val cle = ExpansionEngine.cle(x, y)
                assertEquals(x, ExpansionEngine.xDe(cle))
                assertEquals(y, ExpansionEngine.yDe(cle))
            }
        }
    }

    @Test
    fun `une case du milieu a quatre voisines`() {
        assertEquals(4, ExpansionEngine.voisines(centre).size)
    }

    @Test
    fun `une case de bord n'a pas de voisine hors grille`() {
        // Sans cette borne, un déblocage au bord produirait des coordonnées
        // négatives qui reviendraient à l'autre extrémité du plan.
        assertEquals(2, ExpansionEngine.voisines(ExpansionEngine.cle(0, 0)).size)
        assertEquals(
            2,
            ExpansionEngine.voisines(
                ExpansionEngine.cle(ExpansionEngine.COTE - 1, ExpansionEngine.COTE - 1)
            ).size
        )
    }

    @Test
    fun `les diagonales ne sont pas voisines`() {
        val diagonale = ExpansionEngine.cle(ExpansionEngine.CENTRE + 1, ExpansionEngine.CENTRE + 1)
        assertFalse(ExpansionEngine.sontVoisines(centre, diagonale))
    }

    @Test
    fun `seules les cases adjacentes sont achetables`() {
        val possedees = setOf(centre)
        val voisine = ExpansionEngine.cle(ExpansionEngine.CENTRE, ExpansionEngine.CENTRE - 1)
        val lointaine = ExpansionEngine.cle(ExpansionEngine.CENTRE + 5, ExpansionEngine.CENTRE)

        assertTrue(ExpansionEngine.estAchetable(voisine, possedees))
        assertFalse(ExpansionEngine.estAchetable(lointaine, possedees))
        // Une case déjà possédée ne se rachète pas.
        assertFalse(ExpansionEngine.estAchetable(centre, possedees))
    }

    @Test
    fun `la frontiere exclut ce qui est deja possede`() {
        val possedees = ExpansionEngine.casesInitiales().toSet()
        val frontiere = ExpansionEngine.frontiere(possedees)

        assertTrue(frontiere.isNotEmpty())
        assertTrue(frontiere.none { it in possedees })
        // Chaque case de la frontière touche au moins une case possédée.
        assertTrue(frontiere.all { ExpansionEngine.estAchetable(it, possedees) })
    }

    @Test
    fun `le prix monte avec l'eloignement`() {
        val proche = ExpansionEngine.cle(ExpansionEngine.CENTRE + 1, ExpansionEngine.CENTRE)
        val loin = ExpansionEngine.cle(ExpansionEngine.CENTRE + 6, ExpansionEngine.CENTRE)
        val terrain = ExpansionEngine.Terrain.ORDINAIRE

        assertTrue(ExpansionEngine.cout(loin, terrain) > ExpansionEngine.cout(proche, terrain))
    }

    @Test
    fun `un terrain bon marche coute moins qu'un terrain fertile`() {
        val cle = ExpansionEngine.cle(ExpansionEngine.CENTRE + 2, ExpansionEngine.CENTRE)
        assertTrue(
            ExpansionEngine.cout(cle, ExpansionEngine.Terrain.ABANDONNE) <
                ExpansionEngine.cout(cle, ExpansionEngine.Terrain.FERTILE)
        )
    }

    @Test
    fun `le chantier dure plus longtemps sur un terrain a nettoyer`() {
        val cle = ExpansionEngine.cle(ExpansionEngine.CENTRE + 2, ExpansionEngine.CENTRE)
        assertTrue(
            ExpansionEngine.dureeChantierMinutes(cle, ExpansionEngine.Terrain.ROCHEUX) >
                ExpansionEngine.dureeChantierMinutes(cle, ExpansionEngine.Terrain.ORDINAIRE)
        )
    }

    @Test
    fun `le terrain d'une case ne change jamais`() {
        // Calculé, pas tiré au sort : rouvrir l'application ne doit pas changer
        // ce que le joueur croyait acheter.
        val cle = ExpansionEngine.cle(ExpansionEngine.CENTRE + 3, ExpansionEngine.CENTRE - 2)
        assertEquals(ExpansionEngine.terrainDe(cle), ExpansionEngine.terrainDe(cle))
    }

    @Test
    fun `les cases de depart sont contigues`() {
        val initiales = ExpansionEngine.casesInitiales()
        assertEquals(4, initiales.size)
        // Chacune touche au moins une autre, sinon le jardin commencerait
        // déjà morcelé.
        assertTrue(
            initiales.all { c -> initiales.any { it != c && ExpansionEngine.sontVoisines(c, it) } }
        )
    }

    @Test
    fun `la migration recentre les anciennes parcelles sans collision`() {
        // Formule reprise telle quelle de MIGRATION_12_13. Les nouvelles clés
        // doivent toutes dépasser les anciennes, sinon la réécriture des
        // identifiants écraserait des lignes en cours de route.
        val nouvelles = (0 until 16).map { i -> (18 + i / 4) * 40 + (18 + i % 4) }
        assertEquals(16, nouvelles.toSet().size)
        assertTrue(nouvelles.min() > 15)
        // Et elles forment bien un carré de 4 × 4 contigu.
        assertEquals(4, nouvelles.map { ExpansionEngine.xDe(it) }.toSet().size)
        assertEquals(4, nouvelles.map { ExpansionEngine.yDe(it) }.toSet().size)
    }
}
