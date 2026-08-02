package com.sankailife.core.island.domain

/**
 * Fait disparaître la grille des côtes.
 *
 * L'île se dessinait case par case, chaque case étant un carré plein : une
 * côte était un escalier de carrés, et le terrain se lisait comme une grille
 * plutôt que comme un paysage. C'est le reproche « trop cubique ».
 *
 * Le remède est l'autotuilage. Au lieu de poser un carré de sable à côté d'un
 * carré d'herbe, on peint le sable **puis** l'herbe à travers un masque dont le
 * bord est irrégulier. La frontière cesse d'être une droite.
 *
 * Ce moteur ne dessine rien : il décide seulement, pour chaque case, quel
 * masque appliquer. C'est de l'arithmétique sur les voisins, donc vérifiable
 * sans appareil — et c'est là que les erreurs se logent, pas dans le dessin.
 */
object AutotuilageEngine {

    /**
     * Ordre de recouvrement des terrains.
     *
     * Le plus bas est peint en premier et sert de fond partout ; chaque niveau
     * supérieur vient par-dessus, à travers son masque. L'ordre n'est pas
     * esthétique mais physique : le sable borde l'eau, l'herbe borde le sable.
     * L'inverser ferait déborder l'océan sur la plage.
     */
    enum class Couche(val niveau: Int) {
        PROFOND(0),
        BASSE(1),
        SABLE(2),
        TERRE(3)
    }

    /**
     * Couche d'un type de terrain.
     *
     * Le rocher et la rivière comptent avec la terre et l'eau basse : ce sont
     * des variantes locales, pas des niveaux d'altitude. Leur donner une couche
     * à eux ajouterait deux passes de dessin pour une différence que personne
     * ne verrait.
     */
    fun couche(type: IslandTileType): Couche = when (type) {
        IslandTileType.DEEP_WATER -> Couche.PROFOND
        IslandTileType.SHALLOW_WATER, IslandTileType.RIVER, IslandTileType.POND -> Couche.BASSE
        IslandTileType.BEACH, IslandTileType.DOCK -> Couche.SABLE
        else -> Couche.TERRE
    }

    // Bits du code, dans l'ordre où les masques ont été générés.
    const val NORD = 1
    const val EST = 2
    const val SUD = 4
    const val OUEST = 8

    /** Nombre de masques : les seize combinaisons de quatre voisins. */
    const val MASQUES = 16

    /**
     * Code du masque d'une case pour une couche donnée.
     *
     * Un voisin **de niveau au moins égal** compte comme présent : sinon l'herbe
     * s'arrêterait net au bord du sable alors qu'elle doit se poser dessus.
     *
     * Hors des bornes, [typeDe] rend de l'eau profonde, donc les bords de la
     * carte se comportent comme une côte — ce qui est exactement ce qu'ils sont.
     */
    fun code(
        x: Int,
        y: Int,
        couche: Couche,
        typeDe: (Int, Int) -> IslandTileType
    ): Int {
        var code = 0
        if (couche(typeDe(x, y - 1)).niveau >= couche.niveau) code = code or NORD
        if (couche(typeDe(x + 1, y)).niveau >= couche.niveau) code = code or EST
        if (couche(typeDe(x, y + 1)).niveau >= couche.niveau) code = code or SUD
        if (couche(typeDe(x - 1, y)).niveau >= couche.niveau) code = code or OUEST
        return code
    }

    /**
     * Cette case doit-elle être peinte pour cette couche ?
     *
     * Toute case d'un niveau supérieur ou égal, pas seulement celles du niveau
     * exact : une case d'herbe doit aussi porter le sable qu'elle recouvre,
     * sinon on verrait de l'eau à travers les bords irréguliers de l'herbe.
     */
    fun concernee(type: IslandTileType, couche: Couche): Boolean =
        couche(type).niveau >= couche.niveau

    /** Les couches à peindre par-dessus le fond, dans l'ordre. */
    val COUCHES_SUPERIEURES: List<Couche> =
        listOf(Couche.BASSE, Couche.SABLE, Couche.TERRE)
}
