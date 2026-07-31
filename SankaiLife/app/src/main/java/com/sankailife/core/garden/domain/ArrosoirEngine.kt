package com.sankailife.core.garden.domain

/**
 * L'arrosoir et ses améliorations.
 *
 * Un arrosoir de meilleur niveau couvre plusieurs cases d'un geste. Il ne
 * **crée** jamais d'eau : chaque case arrosée coûte toujours une unité. Ce
 * qu'on achète, ce sont des gestes en moins, pas une ressource gratuite.
 *
 * La distinction n'est pas cosmétique. Rendre l'eau gratuite viderait de son
 * sens la boucle éducative — l'eau vient des révisions, c'est la seule chose
 * qui relie le jeu à l'apprentissage.
 */
object ArrosoirEngine {

    const val NIVEAU_MIN = 1
    const val NIVEAU_MAX = 4

    /**
     * Les cases couvertes depuis une case visée.
     *
     * Niveau 1 : la case seule.
     * Niveau 2 : une ligne de trois, horizontale.
     * Niveau 3 : un carré de 2 × 2, ancré sur la case visée.
     * Niveau 4 : un carré de 3 × 3 centré.
     *
     * Les positions hors grille sont écartées ici ; le dépôt écarte ensuite
     * celles qui ne sont pas débloquées. Deux filtres, parce qu'ils ne
     * protègent pas de la même chose.
     */
    fun zone(niveau: Int, cle: Int): List<Int> {
        val x = ExpansionEngine.xDe(cle)
        val y = ExpansionEngine.yDe(cle)

        val decalages: List<Pair<Int, Int>> = when (niveau.coerceIn(NIVEAU_MIN, NIVEAU_MAX)) {
            1 -> listOf(0 to 0)
            2 -> listOf(-1 to 0, 0 to 0, 1 to 0)
            3 -> listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
            else -> (-1..1).flatMap { dx -> (-1..1).map { dy -> dx to dy } }
        }

        return decalages
            .map { (dx, dy) -> x + dx to y + dy }
            .filter { (px, py) ->
                px in 0 until ExpansionEngine.COTE && py in 0 until ExpansionEngine.COTE
            }
            .map { (px, py) -> ExpansionEngine.cle(px, py) }
    }

    /** Prix de passage au niveau suivant, ou null au niveau maximum. */
    fun coutAmelioration(niveauActuel: Int): Int? = when (niveauActuel) {
        1 -> 450
        2 -> 1200
        3 -> 3000
        else -> null
    }

    fun libelle(niveau: Int): String = when (niveau.coerceIn(NIVEAU_MIN, NIVEAU_MAX)) {
        1 -> "Arrosoir simple • 1 case"
        2 -> "Arrosoir long • ligne de 3"
        3 -> "Arrosoir large • carré de 2 × 2"
        else -> "Arrosoir de maître • zone de 3 × 3"
    }

    fun description(niveau: Int): String = when (niveau.coerceIn(NIVEAU_MIN, NIVEAU_MAX)) {
        1 -> "Une parcelle à la fois."
        2 -> "Trois parcelles alignées d'un seul geste."
        3 -> "Quatre parcelles en carré."
        else -> "Neuf parcelles autour de la case visée."
    }
}
