package com.sankailife.core.domain.engine

/** Recompense une duree reelle sans transformer cinq minutes en session de 25. */
object FocusRewardEngine {

    const val DUREE_MIN_VALIDE = 5
    const val DUREE_REFERENCE = 25
    const val DUREE_LONGUE = 45
    const val DUREE_MAX_RECOMPENSEE = 120

    data class Recompense(
        val xp: Int = 0,
        val pieces: Int = 0
    )

    /**
     * Conserve les anciens reperes (50 XP a 25 min, 80 XP a 45 min) tout en
     * rendant les sessions courtes proportionnelles. Au-dela de 45 minutes,
     * XP et pieces sont plafonnes : la concentration longue ne devient pas un
     * moyen de farmer la gamification.
     */
    fun pourMinutes(minutesReelles: Int): Recompense {
        if (minutesReelles < DUREE_MIN_VALIDE) return Recompense()
        val minutes = minutesReelles.coerceAtMost(DUREE_MAX_RECOMPENSEE)

        val xp = when {
            minutes <= DUREE_REFERENCE ->
                XpEngine.XP_FOCUS_25MIN * minutes / DUREE_REFERENCE
            minutes < DUREE_LONGUE ->
                XpEngine.XP_FOCUS_25MIN +
                    (XpEngine.XP_FOCUS_LONG - XpEngine.XP_FOCUS_25MIN) *
                    (minutes - DUREE_REFERENCE) / (DUREE_LONGUE - DUREE_REFERENCE)
            else -> XpEngine.XP_FOCUS_LONG
        }
        val pieces = (10 * minutes / DUREE_REFERENCE).coerceAtMost(10)

        // Le Focus n'alimente plus coffres ni défis : ces boucles appartiennent
        // au Jardin facultatif, jamais au cœur de concentration.
        return Recompense(xp = xp, pieces = pieces)
    }
}
