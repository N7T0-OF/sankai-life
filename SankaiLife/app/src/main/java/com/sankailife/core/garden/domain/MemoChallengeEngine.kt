package com.sankailife.core.garden.domain

import kotlin.random.Random

/**
 * Défi souvenir : « quelle phrase as-tu reçue ? »
 *
 * C'est la mécanique qui relie réellement les notifications au jardin. Sans
 * elle, un mémo reçu est une information passive ; avec elle, il devient une
 * micro-révision de dix secondes qui rapporte de l'eau.
 *
 * Contrainte principale : **un défi ne peut être réclamé qu'une fois**. Sans
 * ça, il suffirait de rouvrir l'application en boucle pour accumuler des
 * ressources sans rien apprendre.
 */
object MemoChallengeEngine {

    /** Nombre total de propositions affichées. */
    const val NOMBRE_OPTIONS = 4

    /** Au-delà, la notification est trop ancienne pour qu'on s'en souvienne. */
    const val VALIDITE_HEURES = 12L

    data class Defi(
        val challengeId: Long,
        val nomModule: String,
        /** La phrase réellement envoyée. */
        val bonneReponse: String,
        /** Propositions mélangées, bonne réponse comprise. */
        val options: List<String>
    )

    /**
     * Construit les propositions.
     *
     * Les leurres viennent du **même module** : piocher dans un autre rendrait
     * la bonne réponse évidente par le simple ton de la phrase, et le défi ne
     * testerait plus rien.
     *
     * @param autresPhrases phrases du module hors bonne réponse.
     */
    fun construireOptions(
        bonneReponse: String,
        autresPhrases: List<String>,
        alea: Random = Random.Default
    ): List<String> {
        val leurres = autresPhrases
            .filter { it != bonneReponse }
            .distinct()
            .shuffled(alea)
            .take(NOMBRE_OPTIONS - 1)

        return (leurres + bonneReponse).shuffled(alea)
    }

    /**
     * Un défi est-il encore proposable ?
     * Un défi déjà réclamé ou trop ancien ne doit plus apparaître.
     */
    fun estProposable(
        dejaReclame: Boolean,
        envoyeALeMillis: Long,
        maintenantMillis: Long
    ): Boolean {
        if (dejaReclame) return false
        val ageHeures = (maintenantMillis - envoyeALeMillis) / 3_600_000
        return ageHeures in 0 until VALIDITE_HEURES
    }

    /**
     * Récompense d'un défi réussi.
     * Volontairement modeste : le défi est un bonus d'attention, pas une
     * source principale de ressources.
     */
    data class Recompense(val eau: Int, val pieces: Int)

    fun recompense(reussi: Boolean): Recompense =
        if (reussi) Recompense(LearningRewardEngine.EAU_SOUVENIR, LearningRewardEngine.PIECES_SOUVENIR)
        // Une erreur ne rapporte rien mais ne retire rien : se tromper de
        // souvenir est normal, le punir découragerait de tenter.
        else Recompense(0, 0)
}
