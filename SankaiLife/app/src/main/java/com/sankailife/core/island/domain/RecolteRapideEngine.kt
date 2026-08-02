package com.sankailife.core.island.domain

import kotlin.math.abs

/**
 * Les bulles de récolte, et ce qu'un appui dessus ramasse.
 *
 * **Récolter demandait quatre gestes pour un résultat évident.** Il fallait
 * toucher la parcelle, lire une fiche, trouver le bouton, appuyer, fermer. Sur
 * huit tournesols mûrs, cela fait trente-deux gestes pour une action dont
 * personne n'hésite jamais : oui, je veux récolter ma plante mûre.
 *
 * Une bulle au-dessus des plantes prêtes, un appui, c'est fait. Le reste de ce
 * moteur sert à décider **ce qu'un appui ramasse** — une plante, ou le groupe
 * autour d'elle — et surtout ce qu'il ne doit jamais ramasser sans qu'on
 * l'ait demandé.
 */
object RecolteRapideEngine {

    /** Une parcelle mûre, vue par le regroupement. */
    data class Prete(
        val cle: Int,
        val x: Int,
        val y: Int,
        val graineId: String
    )

    /** Une bulle affichée sur la carte. */
    data class Bulle(
        val graineId: String,
        /** Case où la bulle est dessinée : la plus haute et la plus à gauche. */
        val x: Int,
        val y: Int,
        /** Toutes les parcelles que cet appui ramasserait. */
        val cles: List<Int>
    ) {
        val quantite: Int get() = cles.size
        val groupee: Boolean get() = cles.size > 1
    }

    /**
     * Portée du regroupement, en cases.
     *
     * Deux, ce qui couvre un carré de cinq sur cinq autour d'une plante. Plus
     * large, une bulle ramasserait des cultures qu'on ne voit pas à côté d'elle
     * et l'appui deviendrait imprévisible ; plus étroit, un champ de neuf
     * parcelles afficherait encore neuf bulles.
     */
    const val PORTEE = 2

    /**
     * Regroupe les plantes mûres en bulles.
     *
     * Une bulle ne réunit **que la même espèce**. Mélanger deux cultures
     * ramasserait une plante qu'on gardait peut-être pour autre chose, et
     * l'appui ne serait plus lisible : on ne saurait pas ce qu'on récolte avant
     * de l'avoir récolté.
     *
     * Le regroupement est transitif à l'intérieur d'une espèce — deux parcelles
     * éloignées reliées par une troisième forment un seul champ, ce qui
     * correspond à ce qu'on voit.
     */
    fun bulles(pretes: List<Prete>, groupe: Boolean = true): List<Bulle> {
        if (pretes.isEmpty()) return emptyList()
        if (!groupe) {
            return pretes
                .sortedWith(compareBy({ it.y }, { it.x }))
                .map { Bulle(it.graineId, it.x, it.y, listOf(it.cle)) }
        }

        val restantes = pretes.sortedWith(compareBy({ it.y }, { it.x })).toMutableList()
        val bulles = mutableListOf<Bulle>()

        while (restantes.isNotEmpty()) {
            val depart = restantes.removeAt(0)
            val groupeCourant = mutableListOf(depart)
            var aGrandi = true
            while (aGrandi) {
                aGrandi = false
                val iterateur = restantes.iterator()
                while (iterateur.hasNext()) {
                    val candidate = iterateur.next()
                    if (candidate.graineId != depart.graineId) continue
                    val proche = groupeCourant.any {
                        abs(it.x - candidate.x) <= PORTEE && abs(it.y - candidate.y) <= PORTEE
                    }
                    if (proche) {
                        groupeCourant += candidate
                        iterateur.remove()
                        aGrandi = true
                    }
                }
            }
            // La bulle se pose sur la parcelle la plus haute, puis la plus à
            // gauche : un point d'ancrage stable, sinon elle sauterait d'une
            // case à l'autre à chaque récolte partielle.
            val ancre = groupeCourant.minWithOrNull(compareBy({ it.y }, { it.x }))!!
            bulles += Bulle(
                graineId = depart.graineId,
                x = ancre.x,
                y = ancre.y,
                cles = groupeCourant.sortedWith(compareBy({ it.y }, { it.x })).map { it.cle }
            )
        }
        return bulles.sortedWith(compareBy({ it.y }, { it.x }))
    }

    /**
     * La bulle touchée, s'il y en a une.
     *
     * La zone sensible déborde de la case, parce que la bulle est dessinée
     * au-dessus de la plante et qu'on vise ce qu'on voit. Elle reste bornée :
     * une zone trop large ferait récolter en voulant toucher la parcelle
     * voisine.
     */
    fun bulleTouchee(bulles: List<Bulle>, x: Int, y: Int): Bulle? =
        bulles.firstOrNull { it.x == x && (it.y == y || it.y == y + 1) }

    /**
     * Portée d'un appui, réglable.
     *
     * `GROUPE` est le défaut : c'est ce qu'on voit — les plantes autour de
     * celle qu'on touche. `ZONE` et `TOUT` demandent d'être choisis, parce
     * qu'ils ramassent des cultures hors de l'écran, et récolter ce qu'on ne
     * voit pas doit être une décision et non une surprise.
     */
    enum class Portee { GROUPE, ZONE, TOUT }

    /**
     * Les clés que cet appui ramasse.
     *
     * @param visible cases actuellement à l'écran, pour la portée `ZONE`.
     */
    fun aRecolter(
        touchee: Bulle,
        toutes: List<Bulle>,
        portee: Portee,
        visible: (Int, Int) -> Boolean = { _, _ -> true }
    ): List<Int> = when (portee) {
        Portee.GROUPE -> touchee.cles
        Portee.ZONE -> toutes
            .filter { it.graineId == touchee.graineId && visible(it.x, it.y) }
            .flatMap { it.cles }
        Portee.TOUT -> toutes
            .filter { it.graineId == touchee.graineId }
            .flatMap { it.cles }
    }.distinct()

    /**
     * Résumé affiché après coup.
     *
     * Le nombre exact, parce que la récolte groupée touche des parcelles qu'on
     * n'a pas designées une par une : sans compte, on ne sait pas si l'appui a
     * fait ce qu'on croyait.
     */
    fun resume(nom: String, quantite: Int): String =
        if (quantite <= 1) "$nom récolté" else "$quantite × $nom récoltés"
}
