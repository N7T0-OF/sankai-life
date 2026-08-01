package com.sankailife.core.island.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Écrit quelques îles en texte pour qu'on puisse les regarder.
 *
 * Une suite verte ne prouve pas qu'une île est agréable : elle prouve qu'elle
 * respecte les règles qu'on a pensé à écrire. Ce fichier existe pour que l'œil
 * garde le dernier mot sur la forme du relief.
 */
class IslandApercuTest {

    private fun symbole(t: IslandTileType): Char = when (t) {
        IslandTileType.DEEP_WATER -> '~'
        IslandTileType.SHALLOW_WATER -> '-'
        IslandTileType.BEACH -> '.'
        IslandTileType.GRASS -> 'v'
        IslandTileType.FERTILE_GRASS -> '#'
        IslandTileType.FOREST -> 'T'
        IslandTileType.ROCK -> 'o'
        IslandTileType.RIVER -> '='
        IslandTileType.POND -> 'O'
        IslandTileType.PATH -> '+'
        IslandTileType.BRIDGE -> 'H'
        IslandTileType.DOCK -> 'D'
    }

    @Test
    fun `apercu de quelques iles`() {
        val sortie = StringBuilder()
        listOf(1L, 42L, 777L, 20_260_801L).forEach { graine ->
            val (ile, rapport) = IslandGenerator.genererJouable(graine)
            sortie.appendLine("=== graine demandee $graine -> retenue ${ile.seed} ===")
            sortie.appendLine(
                "cultivables=${rapport.cultivables}  terres=${rapport.terres}  " +
                    "ponton=${ile.ponton}  depart=${ile.zoneDepart}"
            )
            for (y in 0 until ile.hauteur) {
                val ligne = StringBuilder()
                for (x in 0 until ile.largeur) {
                    val z = ile.zoneDepart
                    val dansDepart = z != null &&
                        x in z.x..z.x + 3 && y in z.y..z.y + 3
                    ligne.append(if (dansDepart) '@' else symbole(ile.type(x, y)))
                }
                sortie.appendLine(ligne)
            }
            sortie.appendLine()
        }

        val fichier = File("build/apercu-iles.txt")
        fichier.parentFile?.mkdirs()
        fichier.writeText(sortie.toString())
        assertTrue("L'apercu devrait etre ecrit", fichier.length() > 0)
    }
}
