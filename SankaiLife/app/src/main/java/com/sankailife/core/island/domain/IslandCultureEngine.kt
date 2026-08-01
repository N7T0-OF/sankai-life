package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.Seed
import com.sankailife.core.garden.domain.SoilType

/**
 * Ce qu'on peut faire d'une parcelle d'île, et pourquoi pas.
 *
 * Le cycle est celui du Jardin — dégager, préparer, semer, arroser, récolter —
 * mais la décision est reprise ici plutôt qu'appelée à distance : elle dépend
 * de l'état d'une parcelle d'île, qui n'a pas la même forme que celle du
 * Jardin. La croissance elle-même reste calculée par `CropGrowthEngine`, qui
 * ne connaît ni l'un ni l'autre.
 */
object IslandCultureEngine {

    /** Une action proposée sur une parcelle. */
    enum class Action { DEGAGER, PREPARER, SEMER, ARROSER, RECOLTER }

    /** Ce qui empêche une action, ou `null` si elle est possible. */
    fun raisonImpossible(
        action: Action,
        etat: PlotState,
        aDegager: Boolean,
        besoinEau: Boolean,
        prete: Boolean
    ): String? = when (action) {
        Action.DEGAGER ->
            if (!aDegager) "Il n'y a rien à dégager ici." else null

        Action.PREPARER -> when {
            aDegager -> "Il faut d'abord dégager la parcelle."
            etat != PlotState.EMPTY -> "Cette parcelle est déjà travaillée."
            else -> null
        }

        Action.SEMER -> when {
            aDegager -> "Il faut d'abord dégager la parcelle."
            etat == PlotState.EMPTY -> "La terre doit être préparée avant de semer."
            etat != PlotState.PREPARED -> "Quelque chose pousse déjà ici."
            else -> null
        }

        Action.ARROSER -> when {
            !enCulture(etat) -> "Il n'y a rien à arroser."
            // Arroser une plante qui n'a pas soif gaspillerait de l'eau
            // gagnée en révisant : mieux vaut le dire que l'accepter.
            !besoinEau -> "Cette plante a assez d'eau."
            else -> null
        }

        Action.RECOLTER ->
            if (!prete) "Cette culture n'est pas encore prête." else null
    }

    fun enCulture(etat: PlotState): Boolean =
        etat == PlotState.PLANTED || etat == PlotState.GROWING ||
            etat == PlotState.NEEDS_CARE || etat == PlotState.READY_TO_HARVEST

    /**
     * Actions à proposer, dans l'ordre où elles se présentent naturellement.
     *
     * Seules celles qui sont réellement possibles sont rendues : offrir un
     * bouton qui refuse ensuite revient à faire chercher l'erreur à
     * l'utilisateur.
     */
    fun actionsPossibles(
        etat: PlotState,
        aDegager: Boolean,
        besoinEau: Boolean,
        prete: Boolean
    ): List<Action> = Action.entries.filter {
        raisonImpossible(it, etat, aDegager, besoinEau, prete) == null
    }

    /**
     * La graine convient-elle au sol ?
     *
     * Les sols compatibles ne sont pas une hiérarchie : un cactus veut du
     * sable, pas de la « meilleure » terre.
     */
    fun grainePlantable(graine: Seed, sol: SoilType): Boolean = graine.solRequis == sol

    /**
     * État d'une parcelle après une avancée de croissance.
     *
     * `READY_TO_HARVEST` l'emporte sur tout le reste : une plante prête qui
     * afficherait « manque d'eau » ferait arroser au lieu de récolter.
     */
    fun etatApres(prete: Boolean, besoinEau: Boolean): PlotState = when {
        prete -> PlotState.READY_TO_HARVEST
        besoinEau -> PlotState.NEEDS_CARE
        else -> PlotState.GROWING
    }

    /**
     * Minutes réellement arrosées entre deux instants.
     *
     * Une plante arrosée compte tout le temps où l'eau tenait encore, pas
     * seulement l'instant de l'arrosage. Au-delà, elle sèche et le reste du
     * temps compte au ralenti — c'est `CropGrowthEngine` qui applique le
     * ralentissement, ici on ne fait que mesurer.
     */
    fun minutesArrosees(
        dernierArrosageMillis: Long,
        debutMillis: Long,
        finMillis: Long,
        tenueMinutes: Long = TENUE_EAU_MINUTES
    ): Long {
        if (dernierArrosageMillis <= 0L || finMillis <= debutMillis) return 0L
        val finEau = dernierArrosageMillis + tenueMinutes * 60_000L
        val debut = maxOf(debutMillis, dernierArrosageMillis)
        val fin = minOf(finMillis, finEau)
        if (fin <= debut) return 0L
        return (fin - debut) / 60_000L
    }

    /** Combien de temps un arrosage tient avant que la terre ne sèche. */
    const val TENUE_EAU_MINUTES = 6L * 60L
}
