package com.sankailife.core.learning.domain

import com.sankailife.core.domain.engine.ErreursEngine

/**
 * La révision express : une session courte et équilibrée, prête en un appui.
 *
 * L'idée n'est pas de vider la file d'attente mais de **garder la main** :
 * deux notions qui résistent, une ancienne qui revient, une nouvelle qu'on
 * vient d'ajouter. Le moteur mélange donc les sources au lieu de prendre
 * seulement les cartes dues — une session qui ne contiendrait que des échecs
 * se lirait comme une punition, et une session qui ne contiendrait que des
 * nouveautés ne réviserait rien.
 *
 * Déterministe : deux appels identiques donnent la même sélection. Une
 * révision express qui change de composition entre deux affichages se
 * lirait comme un tirage au sort.
 */
object ExpressEngine {

    /** Taille cible d'une session express. Courte : c'est un rituel, pas une corvée. */
    const val TAILLE_SESSION = 5

    /** Combien de cartes difficiles entrent dans la composition. */
    const val DIFFICILES = 2

    /** Ce qu'on sait d'une carte pour composer la session. */
    data class Carte(
        val id: Long,
        val texte: String,
        val boite: Int,
        val revisions: Int,
        val reussites: Int,
        val prochaineRevisionMillis: Long,
        val profileId: Long
    )

    /**
     * Les cartes de la session express, dans l'ordre où les réviser.
     *
     * La composition respecte les proportions annoncées à l'écran : d'abord
     * les plus difficiles (le travail utile), puis la plus ancienne échue,
     * puis une carte jamais révisée, puis on complète avec les échéances
     * passées les plus en retard — sans jamais dépasser [TAILLE_SESSION].
     */
    fun composer(
        cartes: List<Carte>,
        maintenantMillis: Long,
        tailleSession: Int = TAILLE_SESSION,
        difficiles: Int = DIFFICILES
    ): List<Carte> {
        val prises = linkedSetOf<Long>()

        // 1. Les cartes qui résistent, de la plus fautive à la moins fautive.
        ErreursEngine.selectionner(
            cartes.map {
                ErreursEngine.Historique(
                    id = it.id, texte = it.texte, boite = it.boite,
                    revisions = it.revisions, reussites = it.reussites
                )
            },
            limite = difficiles
        ).mapNotNull { historique ->
            cartes.firstOrNull { it.id == historique.id }
        }.forEach { prises.add(it.id) }

        // 2. La carte échue la plus ancienne, si elle n'est pas déjà prise.
        cartes.asSequence()
            .filter { it.id !in prises }
            .filter { it.prochaineRevisionMillis <= maintenantMillis && it.prochaineRevisionMillis > 0 }
            .sortedBy { it.prochaineRevisionMillis }
            .firstOrNull()
            ?.let { prises.add(it.id) }

        // 3. Une carte jamais révisée : la nouveauté à découvrir.
        cartes.asSequence()
            .filter { it.id !in prises }
            .filter { it.revisions == 0 }
            .firstOrNull()
            ?.let { prises.add(it.id) }

        // 4. On complète avec ce qui mérite réellement une révision : les
        //    échéances passées restantes, puis les cartes jamais vues. Une
        //    carte déjà su (aucune échéance, déjà révisée) n'a rien à faire
        //    dans une session express — ce serait du travail pour rien.
        if (prises.size < tailleSession) {
            cartes.asSequence()
                .filter { it.id !in prises }
                .filter {
                    (it.prochaineRevisionMillis > 0 &&
                        it.prochaineRevisionMillis <= maintenantMillis) ||
                        it.revisions == 0
                }
                .sortedBy { it.prochaineRevisionMillis }
                .take(tailleSession - prises.size)
                .forEach { prises.add(it.id) }
        }

        val parId = cartes.associateBy { it.id }
        return prises.mapNotNull { parId[it] }.take(tailleSession)
    }
}
