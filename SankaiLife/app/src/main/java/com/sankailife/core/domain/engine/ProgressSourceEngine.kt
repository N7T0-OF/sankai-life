package com.sankailife.core.domain.engine

/**
 * L'XP par source d'activité, avec plafond quotidien et dégressivité.
 *
 * La philosophie de Sankai Life : **le téléphone fait l'action, Sankai
 * transforme l'action en progression**. Mais transformer des activités en XP
 * ouvre la porte au farm — créer cent tâches, les terminer, récolter. Ce
 * moteur rend le farm inutile :
 *
 * - chaque source a un **plafond quotidien** : au-delà, plus rien ;
 * - les occurrences rapportent de moins en moins (20, 15, 10, 5…) : faire
 *   trois vraies choses vaut mieux que gratter trente micro-actions, et
 *   gratter jusqu'au plafond demanderait des dizaines de vraies actions ;
 * - rien n'est jamais retiré, jamais pénalisé : Sankai accompagne, il ne
 *   juge pas.
 *
 * Pur et déterministe : toute la règle tient ici, testable sans base.
 */
object ProgressSourceEngine {

    /** Les sources d'activité reconnues par Sankai. */
    enum class Source(val libelle: String) {
        CALENDRIER("Calendrier"),
        CONCENTRATION("Concentration"),
        APPRENTISSAGE("Apprentissage"),
        DECOUVERTE("Découverte")
    }

    /** Ce que Sankai accorde par source, sans jamais dépasser. */
    data class Regle(
        val source: Source,
        /** XP de la première occurrence de la journée. */
        val xpInitial: Int,
        /** Plafond quotidien : au-delà, la source ne rapporte plus rien. */
        val plafondQuotidien: Int,
        /**
         * Occurrences pendant lesquelles la valeur descend d'un cran
         * (xpInitial, xpInitial-pas, …), avant de rester au plancher.
         */
        val paliers: Int = 4
    )

    /** Réglages par défaut. Modestes : la vraie vie ne se paie pas en XP. */
    val REGLES = listOf(
        Regle(Source.CALENDRIER, xpInitial = 20, plafondQuotidien = 100),
        Regle(Source.CONCENTRATION, xpInitial = 15, plafondQuotidien = 50),
        Regle(Source.APPRENTISSAGE, xpInitial = 10, plafondQuotidien = 150),
        Regle(Source.DECOUVERTE, xpInitial = 5, plafondQuotidien = 5)
    )

    fun regle(source: Source): Regle = REGLES.first { it.source == source }

    /** Le pas de décroissance : la valeur baisse d'autant à chaque palier. */
    private fun pas(regle: Regle): Int =
        (regle.xpInitial / regle.paliers.coerceAtLeast(2)).coerceAtLeast(1)

    /**
     * L'XP d'une occurrence, compte tenu de ce qui a déjà été accordé et du
     * nombre d'occurrences déjà consommées aujourd'hui.
     *
     * Dégressif par paliers : 20, 15, 10, 5, puis le plancher d'1 XP jusqu'au
     * plafond. Au plafond, plus rien — la source est épuisée pour la journée.
     */
    fun xpPour(source: Source, dejaAccorde: Int, occurrencesDeja: Int = 0): Int {
        val regle = regle(source)
        if (dejaAccorde >= regle.plafondQuotidien) return 0
        val index = occurrencesDeja.coerceAtLeast(0)
        val valeur = (regle.xpInitial - index * pas(regle)).coerceAtLeast(1)
        return valeur.coerceAtMost(regle.plafondQuotidien - dejaAccorde)
    }

    /** Alias de [xpPour] pour les appels qui ne suivent que l'XP. */
    fun gainPour(source: Source, dejaAccorde: Int): Int =
        xpPour(source, dejaAccorde)
}
