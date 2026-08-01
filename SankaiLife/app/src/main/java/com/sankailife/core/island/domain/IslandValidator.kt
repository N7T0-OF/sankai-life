package com.sankailife.core.island.domain

/**
 * Décide si une île est jouable.
 *
 * Une génération procédurale produit toujours, de temps en temps, quelque chose
 * d'inacceptable : une bande de sable sans terre, une rivière qui coupe la ferme
 * du ponton, une île où l'on ne peut rien planter. Ces cas sont rares, mais un
 * joueur sur cinquante tomberait dessus au premier lancement — c'est-à-dire au
 * seul moment où il n'a aucune raison de rester.
 *
 * Le validateur les nomme un par un plutôt que de rendre un simple booléen :
 * un rejet muet ne dit pas quel réglage du générateur est en cause.
 */
object IslandValidator {

    /** Ce qui a été vérifié, et ce qui manque le cas échéant. */
    data class Rapport(
        val jouable: Boolean,
        val manques: List<String>,
        val cultivables: Int,
        val terres: Int
    )

    fun valider(ile: IslandGenerator.Ile): Rapport {
        val manques = mutableListOf<String>()

        val cultivables = ile.compter { it.cultivable }
        val terres = ile.compter { it.estTerre }

        if (cultivables < IslandGenerator.CULTIVABLES_MINIMUM) {
            manques += "seulement $cultivables cases cultivables " +
                "(minimum ${IslandGenerator.CULTIVABLES_MINIMUM})"
        }
        if (ile.ponton == null) manques += "aucune plage bordant l'eau pour poser le ponton"
        if (ile.zoneDepart == null) manques += "aucun carré 4×4 cultivable d'un seul tenant"

        if (!entoureeDEau(ile)) manques += "l'île touche le bord de la carte"
        if (!plagePresente(ile)) manques += "aucune plage entre l'eau et l'herbe"

        // Le contrôle qui compte vraiment : depuis le ponton, atteint-on la
        // ferme à pied ? Une rivière qui coupe l'île en deux produit une carte
        // qui a l'air correcte et qui ne se joue pas.
        if (ile.ponton != null && ile.zoneDepart != null && !relies(ile)) {
            manques += "la zone de départ n'est pas accessible depuis le ponton"
        }
        if (!placePourBatiment(ile)) manques += "aucun emplacement 2×2 pour la Boutique"

        return Rapport(manques.isEmpty(), manques, cultivables, terres)
    }

    /** Aucune terre ne doit toucher le cadre : l'océan doit faire le tour. */
    private fun entoureeDEau(ile: IslandGenerator.Ile): Boolean {
        for (x in 0 until ile.largeur) {
            if (ile.type(x, 0).estTerre) return false
            if (ile.type(x, ile.hauteur - 1).estTerre) return false
        }
        for (y in 0 until ile.hauteur) {
            if (ile.type(0, y).estTerre) return false
            if (ile.type(ile.largeur - 1, y).estTerre) return false
        }
        return true
    }

    /**
     * Une plage digne de ce nom.
     *
     * Le seuil est proportionnel : sur une petite île, quelques cases de sable
     * suffisent ; sur une grande, un liseré de dix cases signale un relief mal
     * réglé plutôt qu'une côte.
     */
    private fun plagePresente(ile: IslandGenerator.Ile): Boolean {
        val sable = ile.compter { it == IslandTileType.BEACH || it == IslandTileType.DOCK }
        return sable >= ile.largeur / 2
    }

    /** Un carré 2×2 constructible et libre, pour la Boutique. */
    private fun placePourBatiment(ile: IslandGenerator.Ile): Boolean {
        for (y in 0 until ile.hauteur - 1) {
            for (x in 0 until ile.largeur - 1) {
                val ok = ile.type(x, y).constructible &&
                    ile.type(x + 1, y).constructible &&
                    ile.type(x, y + 1).constructible &&
                    ile.type(x + 1, y + 1).constructible
                if (ok) return true
            }
        }
        return false
    }

    /**
     * Parcours en largeur du ponton vers la zone de départ.
     *
     * En largeur et non en profondeur : sur une grille de mille cases, une
     * récursion profonde risque de dérouler la pile, et une île générée ne doit
     * jamais pouvoir faire tomber l'application.
     */
    private fun relies(ile: IslandGenerator.Ile): Boolean {
        val depart = ile.ponton ?: return false
        val cible = ile.zoneDepart ?: return false

        val vus = BooleanArray(ile.largeur * ile.hauteur)
        val file = ArrayDeque<IslandGenerator.Case>()
        file += depart
        vus[depart.y * ile.largeur + depart.x] = true

        while (file.isNotEmpty()) {
            val c = file.removeFirst()
            // La zone de départ fait 4×4 : toucher n'importe laquelle de ses
            // cases suffit à prouver qu'on peut y aller.
            if (c.x in cible.x..cible.x + 3 && c.y in cible.y..cible.y + 3) return true

            for ((vx, vy) in listOf(
                c.x - 1 to c.y, c.x + 1 to c.y, c.x to c.y - 1, c.x to c.y + 1
            )) {
                if (vx !in 0 until ile.largeur || vy !in 0 until ile.hauteur) continue
                val i = vy * ile.largeur + vx
                if (vus[i]) continue
                if (!ile.type(vx, vy).franchissable) continue
                vus[i] = true
                file += IslandGenerator.Case(vx, vy)
            }
        }
        return false
    }
}
