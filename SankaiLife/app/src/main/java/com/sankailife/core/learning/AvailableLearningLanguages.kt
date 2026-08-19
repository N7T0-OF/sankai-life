package com.sankailife.core.learning

import com.sankailife.core.data.db.SankaiDatabase

/**
 * Les langues réellement disponibles chez l'utilisateur.
 *
 * **Une seule source de vérité** entre les langues supportées par
 * l'application (le catalogue embarqué connaît le japonais, l'allemand,
 * l'italien…) et les langues que l'utilisateur a réellement installées ou
 * choisies (les profils Mémo portent leur langue BCP-47).
 *
 * Mot du jour, découverte culturelle, notifications et recommandations ne
 * doivent jamais proposer une langue que l'utilisateur n'a pas : un mot en
 * japonais pour quelqu'un qui n'apprend que le français et le portugais
 * serait un contenu hors de propos.
 *
 * Règle de repli : sans aucun profil déclarant une langue, le français est
 * utilisé par défaut — c'est la langue historique de l'application et le
 * catalogue de culture embarqué.
 */
object AvailableLearningLanguages {

    /**
     * Les codes courts (BCP-47, partie avant le tiret : « pt-BR » devient
     * « pt ») des langues présentes sur les profils Mémo de l'utilisateur.
     *
     * Le code court est volontaire : le catalogue du mot du jour et les
     * capsules déclarent « pt-BR » comme « pt », et le groupement « pt » /
     * « pt-BR » est déjà la règle ailleurs (statistiques par langue).
     */
    suspend fun pour(database: SankaiDatabase): Set<String> {
        val langues = database.memoDao().getAllProfilesOnce()
            .mapNotNull { profile ->
                profile.langue
                    .trim()
                    .lowercase()
                    .substringBefore('-')
                    .takeIf { it.isNotBlank() }
            }
            .toSet()
        return if (langues.isEmpty()) setOf("fr") else langues
    }
}
