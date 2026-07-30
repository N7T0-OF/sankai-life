package com.sankailife.core.garden.domain

/**
 * Ce que le joueur tient en main.
 *
 * Un outil sélectionné change le sens du glissement : sans outil, le doigt
 * déplace la caméra ; avec un outil, il l'applique aux parcelles traversées.
 * Ce basculement évite d'avoir à choisir entre naviguer et agir.
 */
sealed interface OutilJardin {

    /** Libellé affiché dans la barre d'outils. */
    val libelle: String
    val emoji: String

    /** Une graine prête à être semée. */
    data class Graine(val seed: Seed) : OutilJardin {
        override val libelle: String get() = seed.nom
        override val emoji: String get() = seed.emoji
    }

    data object Arrosoir : OutilJardin {
        override val libelle = "Arrosoir"
        override val emoji = "💧"
    }

    data object Panier : OutilJardin {
        override val libelle = "Panier"
        override val emoji = "🧺"
    }

    data object Pioche : OutilJardin {
        override val libelle = "Pioche"
        override val emoji = "⛏️"
    }

    /**
     * L'outil peut-il s'appliquer à cet état de parcelle ?
     *
     * Utilisé pour la surbrillance pendant le glissement : mettre en évidence
     * uniquement les cases valides évite au joueur de balayer dans le vide.
     */
    fun applicableA(etat: PlotState): Boolean = when (this) {
        is Graine -> etat == PlotState.EMPTY || etat == PlotState.PREPARED
        Arrosoir -> etat == PlotState.GROWING || etat == PlotState.PLANTED ||
                    etat == PlotState.NEEDS_CARE
        Panier -> etat == PlotState.READY_TO_HARVEST
        Pioche -> etat == PlotState.UNCLEARED
    }
}
