package com.sankailife.core.island.domain

/**
 * Borne le rattrapage hors ligne de l'ile sans faire confiance aveuglement a
 * l'horloge murale.
 *
 * L'ile ne possede pas encore de repere monotone persistant. On applique donc
 * deux protections sans migration : un retour en arriere ne produit rien et
 * une avance, meme enorme, ne credite jamais plus de vingt-quatre heures.
 */
object IslandTimeEngine {

    const val MAX_RATTRAPAGE_MINUTES = 24L * 60L

    fun minutesRetenues(depuisMillis: Long, maintenantMillis: Long): Long {
        if (depuisMillis <= 0L || maintenantMillis <= depuisMillis) return 0L
        return ((maintenantMillis - depuisMillis) / 60_000L)
            .coerceAtMost(MAX_RATTRAPAGE_MINUTES)
    }

    /**
     * Intervalle commun donne aux Mimos avant que les curseurs des parcelles
     * ne soient avances. La parcelle la plus anciennement calculee fait foi.
     */
    fun minutesDepuisDerniereVisite(
        reperesMillis: Iterable<Long>,
        maintenantMillis: Long
    ): Long = reperesMillis.maxOfOrNull {
        minutesRetenues(it, maintenantMillis)
    } ?: 0L
}
