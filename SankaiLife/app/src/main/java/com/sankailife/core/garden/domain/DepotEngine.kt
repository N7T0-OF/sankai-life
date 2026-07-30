package com.sankailife.core.garden.domain

import kotlin.math.abs

/**
 * Circuit de la récolte : caisse → dépôt → inventaire → vente.
 *
 * Une plante récoltée ne devient pas de l'argent sur place. Elle produit une
 * caisse, qu'il faut ranger, et le stock ne se vend qu'au marchand. Trois
 * étapes là où une seule suffirait techniquement — c'est délibéré : sans elles
 * le dépôt n'a pas de fonction, et les Mimos transporteurs n'auront rien à
 * transporter quand ils arriveront.
 *
 * Le prix, lui, bouge d'un jour à l'autre. Il est calculé, pas tiré au sort :
 * hors ligne, un aléatoire stocké finirait par diverger entre deux appareils,
 * et un joueur qui relance l'application ne doit pas voir le cours changer.
 */
object DepotEngine {

    /**
     * Nombre de caisses tolérées avant que le terrain ne sature.
     *
     * La limite est la seule chose qui oblige à revenir au dépôt. Elle est
     * assez haute pour ne jamais gêner une session normale, assez basse pour
     * qu'une longue absence demande un rangement.
     */
    const val CAPACITE_CAISSES = 16

    /** Amplitude de variation du cours, en plus ou en moins. */
    private const val AMPLITUDE = 0.20f

    /** Clé de stock : une ligne par espèce et par qualité. */
    fun cle(seedId: String, qualite: HarvestQuality): String = "${seedId}_${qualite.name}"

    /**
     * Cours du jour pour une espèce, entre 0,80 et 1,20.
     *
     * Dérivé du couple (jour, espèce) : deux espèces ne montent pas le même
     * jour, et le même jour donne toujours le même cours.
     */
    fun cours(seedId: String, jourIso: String): Float {
        val graine = abs((seedId + jourIso).hashCode())
        val position = (graine % 1000) / 1000f      // 0f..1f
        return 1f + (position * 2f - 1f) * AMPLITUDE
    }

    /** Prix unitaire d'une récolte, cours du jour appliqué. */
    fun prixUnitaire(seed: Seed, qualite: HarvestQuality, jourIso: String): Int {
        val base = seed.rendementPieces * qualite.multiplicateur
        return (base * cours(seed.id, jourIso)).toInt().coerceAtLeast(1)
    }

    /** Étiquette du cours, pour signaler une bonne journée de vente. */
    fun libelleCours(valeur: Float): String = when {
        valeur >= 1.12f -> "Cours haut"
        valeur <= 0.88f -> "Cours bas"
        else -> "Cours stable"
    }

    /**
     * Combien de caisses peuvent encore être rangées.
     * Le dépôt n'a pas de limite propre : c'est le terrain qui sature, pas le
     * stock. Empêcher de ranger reviendrait à bloquer la seule action qui
     * libère de la place.
     */
    fun placeRestante(caissesPosees: Int): Int =
        (CAPACITE_CAISSES - caissesPosees).coerceAtLeast(0)

    fun terrainSature(caissesPosees: Int): Boolean = caissesPosees >= CAPACITE_CAISSES
}
