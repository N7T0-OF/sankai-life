package com.sankailife.core.island.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandCodecTest {

    @Test
    fun `un aller-retour rend exactement le meme terrain`() {
        val ile = IslandGenerator.generer(4_242L)
        val decode = IslandCodec.decoder(IslandCodec.encoder(ile.tuiles))
        assertEquals(ile.tuiles, decode)
    }

    @Test
    fun `chaque type de case a son code`() {
        // Si un type ajoute plus tard n'a pas de lettre, l'encodage plante a
        // l'enregistrement — donc au pire moment. Le bloc d'initialisation le
        // refuse au chargement de la classe ; ce test le constate.
        IslandTileType.entries.forEach { type ->
            val encode = IslandCodec.encoder(listOf(type))
            assertEquals(1, encode.length)
            assertEquals(type, IslandCodec.decoder(encode).single())
        }
    }

    @Test
    fun `une lettre inconnue devient de l'eau au lieu de faire echouer la lecture`() {
        // Une sauvegarde ecrite par une version plus recente doit s'ouvrir :
        // refuser de charger reviendrait a effacer la partie.
        val decode = IslandCodec.decoder("gg?gg")
        assertEquals(5, decode.size)
        assertEquals(IslandTileType.DEEP_WATER, decode[2])
        assertEquals(IslandTileType.GRASS, decode[0])
    }

    @Test
    fun `la taille annoncee est verifiee`() {
        // Une donnee tronquee se lirait comme une ile plus petite et decalerait
        // tout le terrain d'une ligne a l'autre.
        val ile = IslandGenerator.generer(7L)
        val donnees = IslandCodec.encoder(ile.tuiles)
        assertTrue(IslandCodec.tailleCoherente(donnees, ile.largeur, ile.hauteur))
        assertFalse(IslandCodec.tailleCoherente(donnees.dropLast(1), ile.largeur, ile.hauteur))
        assertFalse(IslandCodec.tailleCoherente(donnees, 0, 0))
    }

    @Test
    fun `l'empreinte change des qu'une case change`() {
        val ile = IslandGenerator.generer(11L)
        val donnees = IslandCodec.encoder(ile.tuiles)
        val altere = donnees.substring(0, 500) + 'W' + donnees.substring(501)
        assertNotEquals(IslandCodec.empreinte(donnees), IslandCodec.empreinte(altere))
        assertEquals(IslandCodec.empreinte(donnees), IslandCodec.empreinte(donnees))
    }

    @Test
    fun `une ile complete tient dans un kilo-octet`() {
        // C'est ce qui justifie la chaine plutot que mille lignes Room.
        val ile = IslandGenerator.generer(3L)
        assertEquals(ile.largeur * ile.hauteur, IslandCodec.encoder(ile.tuiles).length)
        assertTrue(IslandCodec.encoder(ile.tuiles).length <= 1_024)
    }
}
