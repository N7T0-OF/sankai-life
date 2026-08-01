package com.sankailife.core.island.domain

/**
 * Nature d'une case de l'île.
 *
 * Les trois propriétés sont portées par l'énuméré plutôt que dispersées dans
 * des `when` à travers le code. Une règle écrite à un seul endroit ne peut pas
 * diverger d'elle-même : le jour où le sable devient cultivable pour les
 * cactus, il y a une ligne à changer, pas sept.
 */
enum class IslandTileType(
    /** De l'eau : rien ne s'y construit, rien ne s'y marche. */
    val estEau: Boolean,
    /** Peut devenir une parcelle achetable. */
    val cultivable: Boolean,
    /** Un Mimo peut y passer. */
    val franchissable: Boolean,
    /** Peut recevoir un bâtiment, une fois dégagée si besoin. */
    val constructible: Boolean
) {
    /** Le large. Borne la caméra et n'est jamais atteint à pied. */
    DEEP_WATER(estEau = true, cultivable = false, franchissable = false, constructible = false),

    /** Bordure claire entre le large et la plage. Un pont peut l'enjamber. */
    SHALLOW_WATER(estEau = true, cultivable = false, franchissable = false, constructible = false),

    /**
     * Sable. Franchissable et constructible, mais non cultivable en l'état.
     *
     * La plage est protégée : y bâtir est permis, y planter ne l'est pas. Sans
     * cette distinction, un joueur couvrirait sa côte de champs et l'île
     * perdrait la forme qui la rend reconnaissable.
     */
    BEACH(estEau = false, cultivable = false, franchissable = true, constructible = true),

    GRASS(estEau = false, cultivable = true, franchissable = true, constructible = true),

    /** Herbe grasse : mêmes droits, meilleur rendement, prix plus élevé. */
    FERTILE_GRASS(estEau = false, cultivable = true, franchissable = true, constructible = true),

    /** Bois. Franchissable, mais il faut l'abattre avant de cultiver. */
    FOREST(estEau = false, cultivable = false, franchissable = true, constructible = false),

    /** Rocher. Bloque le passage tant qu'il n'est pas dégagé. */
    ROCK(estEau = false, cultivable = false, franchissable = false, constructible = false),

    /** Cours d'eau. Se traverse par un pont, jamais à pied. */
    RIVER(estEau = true, cultivable = false, franchissable = false, constructible = false),

    /** Étang intérieur. */
    POND(estEau = true, cultivable = false, franchissable = false, constructible = false),

    /** Chemin posé par le joueur. */
    PATH(estEau = false, cultivable = false, franchissable = true, constructible = false),

    /** Pont posé par le joueur au-dessus d'une rivière. */
    BRIDGE(estEau = false, cultivable = false, franchissable = true, constructible = false),

    /**
     * Ponton d'arrivée. Point d'entrée de l'île, et plus tard des marchands.
     *
     * Non constructible : c'est le seul accès garanti, et le laisser bâtir
     * permettrait de s'enfermer.
     */
    DOCK(estEau = false, cultivable = false, franchissable = true, constructible = false);

    val estTerre: Boolean get() = !estEau
}
