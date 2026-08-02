package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.Seed

/**
 * Le stock de récoltes, sa capacité et le prix de vente.
 *
 * Le piège que ce moteur existe pour éviter : si les récoltes ne se
 * monnayaient qu'à la Boutique, et que la Boutique coûte des pièces, un joueur
 * qui commence ne pourrait jamais en gagner. L'économie se bloquerait au
 * premier jour, et pour tout le monde.
 *
 * La règle retenue est donc **vendre est toujours possible** ; la Boutique
 * améliore le prix, le Dépôt augmente la capacité. Les deux bâtiments ajoutent,
 * aucun n'est un péage.
 */
object IslandStockEngine {

    /** Ce qu'on peut entreposer sans dépôt : de quoi tenir quelques récoltes. */
    const val CAPACITE_BASE = 20

    /** Ce que le Dépôt ajoute. */
    const val CAPACITE_DEPOT = 60

    /** Majoration de prix quand la Boutique est bâtie. */
    const val BONUS_BOUTIQUE = 0.35f

    /**
     * Majoration supplémentaire apportée par le Port.
     *
     * Elle s'ajoute à celle de la Boutique au lieu de la remplacer : deux
     * bâtiments coûteux dont l'un annulerait l'autre seraient un piège.
     */
    const val BONUS_PORT = 0.25f

    fun capacite(aDepot: Boolean): Int =
        CAPACITE_BASE + if (aDepot) CAPACITE_DEPOT else 0

    /**
     * Prix de vente unitaire.
     *
     * Sans Boutique, on vend au passage d'un acheteur, au prix de base. Avec,
     * on vend mieux — c'est ce qui rend le bâtiment utile sans le rendre
     * obligatoire.
     */
    fun prixUnitaire(graine: Seed, aBoutique: Boolean, aPort: Boolean = false): Int {
        val base = graine.rendementPieces.coerceAtLeast(0)
        var facteur = 1f
        if (aBoutique) facteur += BONUS_BOUTIQUE
        if (aPort) facteur += BONUS_PORT
        return (base * facteur).toInt()
    }

    fun valeurTotale(
        graine: Seed,
        quantite: Int,
        aBoutique: Boolean,
        aPort: Boolean = false
    ): Int = prixUnitaire(graine, aBoutique, aPort) * quantite.coerceAtLeast(0)

    /** Ce qu'une récolte devient quand le stock est plein. */
    data class Depot(
        /** Ce qui entre réellement en stock. */
        val entrepose: Int,
        /** Ce qui a dû être vendu sur place faute de place. */
        val vendueDOffice: Int,
        /** Pièces créditées pour la partie vendue d'office. */
        val pieces: Int
    )

    /**
     * Range une récolte, en vendant le surplus plutôt qu'en le perdant.
     *
     * Perdre une récolte parce que le stock est plein punirait quelqu'un qui a
     * bien joué : il a semé, arrosé et attendu. Le surplus part donc au prix de
     * base — moins avantageux que la vente choisie, ce qui incite à agrandir le
     * dépôt sans jamais rien détruire.
     */
    fun ranger(
        graine: Seed,
        quantite: Int,
        stockActuel: Int,
        capacite: Int,
        aBoutique: Boolean
    ): Depot {
        val aRanger = quantite.coerceAtLeast(0)
        val place = (capacite - stockActuel).coerceAtLeast(0)
        val entrepose = minOf(aRanger, place)
        val surplus = aRanger - entrepose
        return Depot(
            entrepose = entrepose,
            vendueDOffice = surplus,
            // Toujours au prix de base : le surplus n'est pas une vente
            // choisie, il ne doit pas profiter du bonus de la Boutique.
            pieces = surplus * graine.rendementPieces.coerceAtLeast(0)
        )
    }

    /** Message rendant compte d'une récolte, sans cacher la vente forcée. */
    fun resumeRecolte(graine: Seed, depot: Depot): String = when {
        depot.vendueDOffice <= 0 -> "${graine.emoji} ${graine.nom} récoltée."
        depot.entrepose <= 0 ->
            "Stock plein : ${graine.nom} vendue sur place, +${depot.pieces} 🪙"
        else ->
            "${graine.nom} récoltée. Stock plein : ${depot.vendueDOffice} vendue(s), " +
                "+${depot.pieces} 🪙"
    }
}
