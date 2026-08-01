package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.MimoEngine

/**
 * Ce que les Mimos ont fait pendant l'absence du joueur.
 *
 * Rien n'est simulé en direct : aucun service ne tourne, aucune animation ne
 * représente un travail réel. On calcule à l'ouverture ce qu'ils **auraient**
 * accompli, et on l'applique d'un coup. C'est ce qui permet à l'île d'avancer
 * application fermée sans consommer de batterie — et c'est aussi la raison pour
 * laquelle l'écran ne doit jamais prétendre montrer un Mimo en train de courir.
 *
 * Deux règles non négociables :
 *
 * un Mimo **consomme l'eau du joueur** comme lui. Le laisser arroser
 * gratuitement romprait le lien entre réviser et faire pousser, qui est tout
 * l'intérêt de l'application ;
 *
 * un Mimo **ne détruit jamais rien**. Il agit ou il attend.
 */
object IslandMimoEngine {

    /** Une parcelle, vue par un Mimo. */
    data class Vue(
        val cle: Int,
        val aSoif: Boolean,
        val prete: Boolean
    )

    /** Ce qui doit être appliqué en base. */
    data class Plan(
        val aArroser: List<Int> = emptyList(),
        val aRecolter: List<Int> = emptyList()
    ) {
        val vide: Boolean get() = aArroser.isEmpty() && aRecolter.isEmpty()
        val eauConsommee: Int get() = aArroser.size
    }

    /**
     * Nombre d'actions qu'un Mimo a pu accomplir.
     *
     * Borné par [plafondActions] : revenir après trois semaines ne doit pas
     * vider la réserve d'eau d'un coup ni récolter toute l'île en une fois. Un
     * rattrapage démesuré se lit comme un bug, même quand il est mérité.
     */
    fun actions(type: MimoEngine.Type, minutesEcoulees: Long): Int {
        if (minutesEcoulees <= 0L) return 0
        val brut = minutesEcoulees / type.cadenceMinutes
        return brut.coerceAtMost(PLAFOND_ACTIONS.toLong()).toInt()
    }

    /** Au-delà, on considère que le joueur reprend une partie, pas une session. */
    const val PLAFOND_ACTIONS = 12

    /**
     * Décide du travail accompli.
     *
     * L'ordre compte : on récolte avant d'arroser. Une plante mûre n'a plus
     * besoin d'eau, et l'arroser d'abord gaspillerait une goutte gagnée en
     * révisant.
     *
     * @param eauDisponible réserve du joueur. Le plan s'arrête quand elle est
     *   épuisée : un Mimo ne creuse pas de dette.
     * @param placeStock ce que le dépôt peut encore recevoir ; au-delà, le
     *   surplus sera vendu d'office par l'appelant, jamais perdu.
     */
    fun planifier(
        types: List<MimoEngine.Type>,
        minutesEcoulees: Long,
        parcelles: List<Vue>,
        eauDisponible: Int
    ): Plan {
        if (types.isEmpty() || minutesEcoulees <= 0L) return Plan()

        val recolteur = types.count { it == MimoEngine.Type.RECOLTEUR }
        val arroseur = types.count { it == MimoEngine.Type.ARROSEUR }

        val aRecolter = if (recolteur == 0) emptyList() else {
            val budget = actions(MimoEngine.Type.RECOLTEUR, minutesEcoulees) * recolteur
            parcelles.filter { it.prete }.take(budget).map { it.cle }
        }

        val aArroser = if (arroseur == 0) emptyList() else {
            val budget = actions(MimoEngine.Type.ARROSEUR, minutesEcoulees) * arroseur
            parcelles
                // Une plante qu'on vient de récolter n'a plus soif : l'exclure
                // évite de dépenser une goutte pour une parcelle vide.
                .filter { it.aSoif && !it.prete }
                .take(minOf(budget, eauDisponible.coerceAtLeast(0)))
                .map { it.cle }
        }

        return Plan(aArroser = aArroser, aRecolter = aRecolter)
    }

    /**
     * Compte rendu du travail, à afficher au retour.
     *
     * Dire ce qui a été fait, y compris l'eau dépensée : découvrir une réserve
     * vide sans explication ferait croire à une perte.
     */
    fun resume(plan: Plan): String? = when {
        plan.vide -> null
        plan.aRecolter.isEmpty() ->
            "Tes Mimos ont arrosé ${plan.aArroser.size} parcelle(s) — " +
                "−${plan.eauConsommee} 💧"
        plan.aArroser.isEmpty() ->
            "Tes Mimos ont récolté ${plan.aRecolter.size} parcelle(s)."
        else ->
            "Tes Mimos ont récolté ${plan.aRecolter.size} parcelle(s) et arrosé " +
                "${plan.aArroser.size} — −${plan.eauConsommee} 💧"
    }
}
