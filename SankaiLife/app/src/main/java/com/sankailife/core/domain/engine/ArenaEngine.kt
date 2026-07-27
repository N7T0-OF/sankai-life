package com.sankailife.core.domain.engine

import com.sankailife.core.domain.model.ALL_ARENAS
import com.sankailife.core.domain.model.Arena

/**
 * Position du joueur sur le parcours d'arènes.
 *
 * Fonctions pures : le calcul ne dépend que du niveau, ce qui le rend
 * testable et évite toute désynchronisation avec la base.
 */
object ArenaEngine {

    /** Arène actuellement atteinte. Toujours non nulle : la première est au niveau 1. */
    fun areneActuelle(niveau: Int): Arena =
        ALL_ARENAS.lastOrNull { niveau >= it.niveauRequis } ?: ALL_ARENAS.first()

    /** Arène suivante, ou null si le joueur est au sommet. */
    fun areneSuivante(niveau: Int): Arena? =
        ALL_ARENAS.firstOrNull { niveau < it.niveauRequis }

    /** true si l'arène est atteinte au niveau donné. */
    fun estAtteinte(arene: Arena, niveau: Int): Boolean = niveau >= arene.niveauRequis

    /**
     * Progression vers l'arène suivante, entre 0 et 1.
     *
     * Calculée en niveaux et non en XP : l'XP restant d'un niveau en cours
     * ferait osciller la barre à chaque gain, alors que le repère utile est
     * « combien de niveaux avant la prochaine arène ».
     * Renvoie 1 quand le sommet est atteint.
     */
    fun progressionVersSuivante(niveau: Int): Float {
        val suivante = areneSuivante(niveau) ?: return 1f
        val actuelle = areneActuelle(niveau)
        val portee = (suivante.niveauRequis - actuelle.niveauRequis).coerceAtLeast(1)
        val parcouru = (niveau - actuelle.niveauRequis).coerceAtLeast(0)
        return (parcouru.toFloat() / portee).coerceIn(0f, 1f)
    }

    /** Niveaux restants avant l'arène suivante, 0 au sommet. */
    fun niveauxRestants(niveau: Int): Int =
        areneSuivante(niveau)?.let { (it.niveauRequis - niveau).coerceAtLeast(0) } ?: 0

    /**
     * Arènes dont la récompense est atteinte mais pas encore réclamée.
     * Une progression hors ligne peut en franchir plusieurs d'un coup : elles
     * doivent toutes rester disponibles.
     */
    fun recompensesAReclamer(niveau: Int, dejaReclamees: Set<Int>): List<Arena> =
        ALL_ARENAS.filter { estAtteinte(it, niveau) && it.id !in dejaReclamees }

    /** Niveau maximum du parcours, pour dessiner la graduation. */
    val niveauMaximum: Int get() = ALL_ARENAS.maxOf { it.niveauRequis }
}
