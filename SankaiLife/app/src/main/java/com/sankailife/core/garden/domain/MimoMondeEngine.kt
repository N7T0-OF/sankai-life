package com.sankailife.core.garden.domain

import kotlin.math.abs

/**
 * Où se tiennent les Mimos dans le jardin.
 *
 * **Ce n'est pas une simulation.** Leur travail est reconstitué à l'ouverture
 * depuis le temps écoulé — voir [MimoEngine] — et rien ne tourne quand
 * l'application est fermée. Un Mimo qu'on voit arroser n'est pas en train
 * d'arroser : il indique qu'une parcelle a soif et que c'est son métier.
 *
 * Le choix aurait pu être de ne rien montrer. Mais un jardin où cinq employés
 * n'existent que dans une liste est un jardin mort, et le joueur ne comprend
 * pas ce qu'il a acheté. Montrer leur état réel — occupé, oisif, endormi —
 * dit la vérité sur le jardin sans mentir sur la mécanique.
 */
object MimoMondeEngine {

    enum class Activite(val emoji: String, val libelle: String) {
        ARROSE("💧", "Arrose"),
        RECOLTE("🌾", "Récolte"),
        TRANSPORTE("📦", "Transporte"),
        VEND("🪙", "Au marché"),
        PLANTE("🌱", "Plante"),
        /** Rien à faire : le Mimo attend à sa station. */
        OISIF("", "Sans tâche"),
        /** La nuit, ou faute de compost. */
        DORT("💤", "Dort")
    }

    /**
     * Un Mimo tel qu'on le voit.
     *
     * [cible] est la parcelle qui justifie son activité, ou null s'il n'a rien
     * à faire. L'affichage l'utilise pour le placer entre sa station et son
     * travail — un déplacement décoratif, pas un trajet réel.
     */
    data class MimoUi(
        val id: Long,
        val nom: String,
        val type: MimoEngine.Type,
        val activite: Activite,
        val cible: Int?,
        val station: Int
    ) {
        val endormi: Boolean get() = activite == Activite.DORT
        val actif: Boolean get() = cible != null
    }

    /** Ce dont le placement a besoin, sans dépendre de la base. */
    data class EtatJardin(
        val parcellesDebloquees: List<Int> = emptyList(),
        val parcellesSeches: List<Int> = emptyList(),
        val parcellesPretes: List<Int> = emptyList(),
        val parcellesLibres: List<Int> = emptyList(),
        val caissesPosees: Int = 0,
        val stockVendable: Boolean = false,
        val compost: Int = 0,
        val faitJour: Boolean = true
    )

    /**
     * Station d'un Mimo : sa place au repos.
     *
     * Dérivée de son identifiant, donc stable d'une ouverture à l'autre — un
     * Mimo qui change de place à chaque lancement paraîtrait cassé. Les
     * stations se répartissent autour du centre du terrain possédé.
     */
    fun station(mimoId: Long, parcelles: List<Int>): Int {
        if (parcelles.isEmpty()) {
            return ExpansionEngine.cle(ExpansionEngine.CENTRE, ExpansionEngine.CENTRE)
        }
        return parcelles[(abs(mimoId.hashCode())) % parcelles.size]
    }

    /**
     * Place chaque Mimo selon ce que le jardin réclame vraiment.
     *
     * Deux Mimos du même métier ne visent pas la même parcelle : chacun prend
     * la suivante dans la liste. Sans ça, cinq arroseurs se superposeraient sur
     * une case et on croirait à un bug d'affichage.
     */
    fun placer(
        mimos: List<Triple<Long, String, MimoEngine.Type>>,
        etat: EtatJardin
    ): List<MimoUi> {
        // Curseurs par métier, pour répartir les cibles.
        val pris = mutableMapOf<MimoEngine.Type, Int>()

        return mimos.map { (id, nom, type) ->
            val station = station(id, etat.parcellesDebloquees)
            val index = pris.getOrDefault(type, 0)

            val cible: Int? = when {
                // La nuit, personne ne travaille : c'est la règle du cycle
                // jour / nuit, elle doit se voir.
                !etat.faitJour -> null
                // Sans compost, aucun Mimo n'agit — même règle que le moteur.
                etat.compost <= 0 -> null
                type == MimoEngine.Type.ARROSEUR -> etat.parcellesSeches.getOrNull(index)
                type == MimoEngine.Type.RECOLTEUR -> etat.parcellesPretes.getOrNull(index)
                type == MimoEngine.Type.PLANTEUR -> etat.parcellesLibres.getOrNull(index)
                type == MimoEngine.Type.TRANSPORTEUR ->
                    if (etat.caissesPosees > 0) station else null
                type == MimoEngine.Type.VENDEUR ->
                    if (etat.stockVendable) station else null
                else -> null
            }
            if (cible != null) pris[type] = index + 1

            val activite = when {
                cible != null -> when (type) {
                    MimoEngine.Type.ARROSEUR -> Activite.ARROSE
                    MimoEngine.Type.RECOLTEUR -> Activite.RECOLTE
                    MimoEngine.Type.TRANSPORTEUR -> Activite.TRANSPORTE
                    MimoEngine.Type.VENDEUR -> Activite.VEND
                    MimoEngine.Type.PLANTEUR -> Activite.PLANTE
                }
                // Un Mimo sans tâche ne marche pas au hasard : il s'arrête, et
                // il s'endort si l'heure ou le compost l'y obligent. C'est ce
                // qui rend son inactivité lisible d'un coup d'œil.
                !etat.faitJour || etat.compost <= 0 -> Activite.DORT
                else -> Activite.OISIF
            }

            MimoUi(id, nom, type, activite, cible, station)
        }
    }

    /** Phrase affichée dans la fiche d'un Mimo. */
    fun resume(mimo: MimoUi, compost: Int): String = when {
        mimo.activite == Activite.DORT && compost <= 0 ->
            "Plus de compost. Récolte pour en produire."
        mimo.activite == Activite.DORT -> "Les Mimos dorment la nuit."
        mimo.activite == Activite.OISIF -> "Rien à faire dans son métier."
        else -> "${mimo.activite.libelle} — ${mimo.type.role}"
    }
}
