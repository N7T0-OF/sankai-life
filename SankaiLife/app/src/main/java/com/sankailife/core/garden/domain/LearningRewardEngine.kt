package com.sankailife.core.garden.domain

/**
 * Conversion de l'apprentissage en ressources du jardin.
 *
 * C'est la pièce la plus sensible de tout le mode jeu. Sans elle, réviser
 * cinquante fois la même carte facile produirait de l'eau à l'infini, et le
 * jardin récompenserait la manipulation plutôt que l'apprentissage — ce qui
 * viderait le concept de son sens.
 *
 * Trois garde-fous se cumulent :
 * 1. seule une carte réellement due rapporte le plein tarif ;
 * 2. une carte déjà révisée le même jour ne rapporte plus rien ;
 * 3. un plafond journalier borne l'ensemble.
 */
object LearningRewardEngine {

    /** Bonnes réponses nécessaires pour une unité d'eau. */
    const val REPONSES_PAR_EAU = 5

    /** Plafond journalier d'eau issue de l'apprentissage. */
    const val EAU_MAX_PAR_JOUR = 30

    /** Réserve maximale, pour éviter l'accumulation infinie. */
    const val CAPACITE_EAU = 120

    /** Récompense d'un défi souvenir, une fois par notification envoyée. */
    const val EAU_SOUVENIR = 2
    const val PIECES_SOUVENIR = 10

    /** Bonus de croissance offert par une session Focus terminée. */
    const val BONUS_FOCUS_MINUTES = 30L

    /** Minutes de croissance offertes par tranche de dix cartes révisées. */
    const val BONUS_REVISION_MINUTES = 20L

    /** Statut d'une carte au moment de la révision. */
    enum class StatutCarte {
        /** Échéance atteinte : la révision a une vraie valeur pédagogique. */
        DUE,
        /** Révisée en avance : utile, mais moins. */
        ANTICIPEE,
        /** Déjà révisée aujourd'hui : aucune valeur supplémentaire. */
        DEJA_VUE_AUJOURDHUI
    }

    data class Gain(
        val gouttes: Int,
        val eauCreditee: Int,
        val plafondAtteint: Boolean
    )

    /**
     * Gouttes rapportées par une réponse.
     *
     * Une réponse fausse ne rapporte rien mais ne retire rien : punir l'erreur
     * pousserait à répondre « correct » systématiquement, ce qui détruirait la
     * répétition espacée.
     */
    fun gouttesPourReponse(correcte: Boolean, statut: StatutCarte): Int = when {
        !correcte -> 0
        statut == StatutCarte.DUE -> 1
        statut == StatutCarte.ANTICIPEE -> 1
        else -> 0
    }

    /**
     * Convertit un total de gouttes en eau, plafond journalier compris.
     *
     * @param gouttesAccumulees gouttes en attente de conversion.
     * @param eauDejaGagneeAujourdhui eau déjà créditée dans la journée.
     */
    fun convertir(gouttesAccumulees: Int, eauDejaGagneeAujourdhui: Int): Gain {
        val eauPotentielle = gouttesAccumulees / REPONSES_PAR_EAU
        val marge = (EAU_MAX_PAR_JOUR - eauDejaGagneeAujourdhui).coerceAtLeast(0)
        val creditee = eauPotentielle.coerceAtMost(marge)

        return Gain(
            gouttes = gouttesAccumulees - (creditee * REPONSES_PAR_EAU),
            eauCreditee = creditee,
            plafondAtteint = eauPotentielle > creditee
        )
    }

    /** Ajoute de l'eau en respectant la capacité de la réserve. */
    fun ajouterEau(reserveActuelle: Int, ajout: Int): Int =
        (reserveActuelle + ajout).coerceIn(0, CAPACITE_EAU)

    /**
     * Minutes de croissance offertes par une session de révision.
     * Progression par paliers de dix cartes, pour que l'effort compte mais
     * qu'une session marathon ne fasse pas exploser l'équilibrage.
     */
    fun bonusCroissanceMinutes(cartesRevisees: Int): Long =
        (cartesRevisees / 10) * BONUS_REVISION_MINUTES

    /**
     * Message affiché quand le plafond est atteint.
     * Il doit encourager à continuer d'apprendre, pas donner l'impression
     * que réviser ne sert plus à rien.
     */
    fun messagePlafond(): String =
        "Réserve d'eau du jour complète. Tes révisions continuent de compter " +
        "pour ta progression et ta maîtrise."
}
