package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.ArbreSankaiEngine

/**
 * Regroupe les cases boisées en arbres.
 *
 * Une case de bois n'est pas un arbre : c'est du terrain boisé. Dessiner un
 * arbre identique sur chacune donnerait une haie régulière, qui se lit comme un
 * motif de papier peint plutôt que comme une forêt.
 *
 * Les cases voisines sont donc agglomérées en arbres plus larges quand elles le
 * permettent, en réutilisant les emprises déjà définies pour l'Arbre Sankai. Un
 * bosquet devient un grand arbre, une case isolée un petit.
 */
object IslandForetEngine {

    /** Un arbre posé : son origine et sa taille. */
    data class Arbre(
        val x: Int,
        val y: Int,
        val taille: ArbreSankaiEngine.Taille
    )

    /**
     * Découpe les cases boisées en arbres.
     *
     * L'ordre du balayage — grands blocs d'abord, isolées en dernier — n'est
     * pas une optimisation mais la condition du résultat : commencer par les
     * petites laisserait des cases orphelines partout et aucun grand arbre ne
     * pourrait plus se former.
     *
     * Déterministe : la même île rend toujours le même découpage, sinon le
     * feuillage changerait de place à chaque rendu.
     */
    fun decouper(
        largeur: Int,
        hauteur: Int,
        estBois: (Int, Int) -> Boolean
    ): List<Arbre> {
        if (largeur <= 0 || hauteur <= 0) return emptyList()

        val pris = BooleanArray(largeur * hauteur)
        val arbres = mutableListOf<Arbre>()

        fun libre(x: Int, y: Int): Boolean =
            x in 0 until largeur && y in 0 until hauteur &&
                !pris[y * largeur + x] && estBois(x, y)

        fun prendre(x: Int, y: Int, taille: ArbreSankaiEngine.Taille) {
            ArbreSankaiEngine.casesOccupees(ArbreSankaiEngine.Case(x, y), taille).forEach {
                if (it.x in 0 until largeur && it.y in 0 until hauteur) {
                    pris[it.y * largeur + it.x] = true
                }
            }
            arbres += Arbre(x, y, taille)
        }

        // Blocs de deux par deux.
        for (y in 0 until hauteur - 1) {
            for (x in 0 until largeur - 1) {
                if (libre(x, y) && libre(x + 1, y) && libre(x, y + 1) && libre(x + 1, y + 1)) {
                    prendre(x, y, ArbreSankaiEngine.Taille.QUATRE)
                }
            }
        }

        // Paires horizontales.
        for (y in 0 until hauteur) {
            for (x in 0 until largeur - 1) {
                if (libre(x, y) && libre(x + 1, y)) {
                    prendre(x, y, ArbreSankaiEngine.Taille.DEUX)
                }
            }
        }

        // Ce qui reste, une case à la fois.
        for (y in 0 until hauteur) {
            for (x in 0 until largeur) {
                if (libre(x, y)) prendre(x, y, ArbreSankaiEngine.Taille.UNE)
            }
        }

        // Tri par profondeur : un arbre du bas doit passer devant celui du
        // haut, sinon un feuillage lointain recouvre un tronc proche.
        return arbres.sortedWith(compareBy({ it.y }, { it.x }))
    }

    /**
     * Variation d'échelle d'un arbre, tirée de sa position.
     *
     * Des arbres strictement identiques trahissent la répétition. La variation
     * vient des coordonnées et non d'un tirage : elle est donc stable d'une
     * frame à l'autre, sinon la forêt frémirait.
     */
    fun echelle(x: Int, y: Int): Float {
        var h = x * 374_761_393 xor y * 668_265_263
        h = h xor (h ushr 13)
        return 0.92f + ((h and 0xFF) / 255f) * 0.20f
    }
}
