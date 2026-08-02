package com.sankailife.ui.screens.island

import androidx.compose.ui.graphics.Color
import com.sankailife.core.island.domain.IslandTileType

/**
 * Couleurs du terrain.
 *
 * Ce sont des **couleurs, pas des illustrations**, et c'est assumé pour
 * l'instant : elles permettent de juger la forme des îles et les réglages du
 * générateur tout de suite, sans attendre un jeu de textures complet. Chaque
 * type dessiné à plat pourra recevoir sa texture sans que rien d'autre bouge.
 */
object PaletteIle {

    // Les valeurs sont ecartees a dessein.
    //
    // Un premier jeu, plus « harmonieux », rendait le bois et la terre fertile
    // presque identiques, les rivieres se lisaient comme des fissures et les
    // rochers passaient pour du bruit d'affichage. Sur une carte ou chaque case
    // fait quelques millimetres, deux verts proches ne se distinguent pas : il
    // faut de l'ecart, pas de la nuance.
    fun couleur(type: IslandTileType): Color = when (type) {
        IslandTileType.DEEP_WATER -> Color(0xFF14495F)
        IslandTileType.SHALLOW_WATER -> Color(0xFF2E8CAE)
        IslandTileType.BEACH -> Color(0xFFE9D6A5)
        IslandTileType.GRASS -> Color(0xFF7FC95C)
        // Plus chaude que la plaine, pas seulement plus sombre : c'est ce qui
        // la distingue du bois au premier regard.
        IslandTileType.FERTILE_GRASS -> Color(0xFF57A22F)
        IslandTileType.FOREST -> Color(0xFF1E5B31)
        IslandTileType.ROCK -> Color(0xFF7C858F)
        // Nettement plus claire que l'eau peu profonde : une riviere doit se
        // suivre du regard jusqu'a la mer.
        IslandTileType.RIVER -> Color(0xFF6FD0EC)
        IslandTileType.POND -> Color(0xFF57BCDC)
        IslandTileType.PATH -> Color(0xFFC9A778)
        IslandTileType.BRIDGE -> Color(0xFF9C6B3C)
        IslandTileType.DOCK -> Color(0xFFB07C44)
    }

    /**
     * Légère variation par case, tirée de ses coordonnées.
     *
     * Un aplat uniforme sur des centaines de cases donne une carte en carton.
     * La variation vient des coordonnées et non d'un tirage : elle est donc
     * stable d'une frame à l'autre, sinon le terrain scintillerait.
     */
    fun variation(x: Int, y: Int): Float {
        var h = x * 73_856_093 xor y * 19_349_663
        h = h xor (h ushr 13)
        return ((h and 0xFF) / 255f - 0.5f) * 0.07f
    }

    fun couleurCase(type: IslandTileType, x: Int, y: Int): Color {
        val base = couleur(type)
        val v = variation(x, y)
        return Color(
            red = (base.red + v).coerceIn(0f, 1f),
            green = (base.green + v).coerceIn(0f, 1f),
            blue = (base.blue + v).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /** Nom lisible, pour la fiche d'une case. */
    fun nom(type: IslandTileType): String = when (type) {
        IslandTileType.DEEP_WATER -> "Océan"
        IslandTileType.SHALLOW_WATER -> "Eau peu profonde"
        IslandTileType.BEACH -> "Plage"
        IslandTileType.GRASS -> "Plaine"
        IslandTileType.FERTILE_GRASS -> "Terre fertile"
        // « Arbre » et non « Bois » : depuis que les arbres sont dessines,
        // la fiche doit nommer ce qu'on voit, pas le type de terrain.
        IslandTileType.FOREST -> "Arbre"
        IslandTileType.ROCK -> "Rocher"
        IslandTileType.RIVER -> "Rivière"
        IslandTileType.POND -> "Étang"
        IslandTileType.PATH -> "Chemin"
        IslandTileType.BRIDGE -> "Pont"
        IslandTileType.DOCK -> "Ponton"
    }
}
