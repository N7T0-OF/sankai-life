package com.sankailife.core.garden.domain

/**
 * Croissance d'une culture, calculée à l'ouverture depuis des horodatages.
 *
 * Aucun service ne tourne en arrière-plan : la plante n'« avance » pas, on
 * recalcule où elle en serait. C'est ce qui permet à une culture de progresser
 * application fermée sans consommer de batterie.
 *
 * Règle non négociable du projet : **une plante ne meurt jamais**. Le manque
 * d'eau ou une longue absence ralentissent, ils ne détruisent pas.
 */
object CropGrowthEngine {

    /** Vitesse quand la plante manque d'eau. Elle ralentit, elle ne s'arrête pas. */
    private const val FACTEUR_SANS_EAU = 0.35f

    /** Au-delà, la culture passe en repos et son rendement baisse un peu. */
    const val SEUIL_REPOS_MINUTES = 48L * 60L

    /** Réduction maximale de rendement après une longue absence. */
    private const val REDUCTION_REPOS_MAX = 0.25f

    data class Etat(
        val stage: CropStage,
        /** Progression dans le cycle complet, de 0 à 1. */
        val progression: Float,
        val minutesRestantes: Long,
        val prete: Boolean,
        val enRepos: Boolean,
        val besoinEau: Boolean
    )

    /**
     * Minutes de croissance effectivement acquises.
     *
     * @param minutesEcoulees temps retenu par [TrustedTimeEngine], déjà borné.
     * @param minutesArrosees part de ce temps où la plante avait de l'eau.
     * @param bonusMinutes minutes offertes par l'apprentissage.
     */
    fun minutesAcquises(
        minutesEcoulees: Long,
        minutesArrosees: Long,
        bonusMinutes: Long = 0L
    ): Long {
        val arrosees = minutesArrosees.coerceIn(0L, minutesEcoulees)
        val seches = minutesEcoulees - arrosees
        return arrosees + (seches * FACTEUR_SANS_EAU).toLong() + bonusMinutes
    }

    /** Durée totale d'une culture, sol pris en compte. */
    fun dureeTotaleMinutes(seed: Seed, sol: SoilType): Long =
        (seed.dureeMinutes / sol.vitesse).toLong().coerceAtLeast(1L)

    /**
     * Où en est la culture.
     *
     * @param minutesCumulees minutes de croissance déjà acquises.
     * @param minutesDepuisArrosage temps écoulé depuis le dernier arrosage.
     */
    fun etat(
        seed: Seed,
        sol: SoilType,
        minutesCumulees: Long,
        minutesDepuisArrosage: Long
    ): Etat {
        val total = dureeTotaleMinutes(seed, sol)
        val progression = (minutesCumulees.toFloat() / total).coerceIn(0f, 1f)

        // Les cinq étapes se répartissent uniformément sur le cycle.
        val index = (progression * (CropStage.entries.size - 1)).toInt()
            .coerceIn(0, CropStage.entries.size - 1)
        val stage = CropStage.entries[index]

        val prete = progression >= 1f
        val besoinEau = minutesDepuisArrosage > (total / 3).coerceAtLeast(60L)
        val enRepos = minutesDepuisArrosage > SEUIL_REPOS_MINUTES

        return Etat(
            stage = stage,
            progression = progression,
            minutesRestantes = (total - minutesCumulees).coerceAtLeast(0L),
            prete = prete,
            enRepos = enRepos,
            besoinEau = besoinEau && !prete
        )
    }

    /**
     * Qualité de la récolte.
     *
     * Elle récompense l'entretien et l'apprentissage, jamais la simple
     * patience : attendre sans rien faire donne une récolte normale, pas
     * une mauvaise.
     */
    fun qualite(
        arrosagesEffectues: Int,
        arrosagesAttendus: Int,
        revisionsPendantCulture: Int
    ): HarvestQuality {
        val attendus = arrosagesAttendus.coerceAtLeast(1)
        val tauxSoin = arrosagesEffectues.toFloat() / attendus
        return when {
            tauxSoin >= 1f && revisionsPendantCulture >= 20 -> HarvestQuality.PARFAITE
            tauxSoin >= 0.6f || revisionsPendantCulture >= 10 -> HarvestQuality.FLORISSANTE
            else -> HarvestQuality.NORMALE
        }
    }

    /**
     * Pièces rapportées par une récolte.
     * Une longue mise en repos réduit un peu le rendement, sans jamais l'annuler.
     */
    fun rendement(
        seed: Seed,
        qualite: HarvestQuality,
        enRepos: Boolean
    ): Int {
        val base = seed.rendementPieces * qualite.multiplicateur
        val penalite = if (enRepos) (1f - REDUCTION_REPOS_MAX) else 1f
        return (base * penalite).toInt().coerceAtLeast(1)
    }
}
