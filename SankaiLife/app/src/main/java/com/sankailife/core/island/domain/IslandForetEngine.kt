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
     * Rayon du feuillage, en fraction du côté du dessin.
     *
     * Le fichier est carré mais la couronne est ronde, et le bas de l'image
     * n'est qu'un tronc étroit. Prendre le carré entier réserverait des cases
     * que rien ne cache.
     */
    private const val RAYON_COURONNE = 0.37f

    /** Hauteur du centre de la couronne dans le dessin, mesurée sur l'asset. */
    private const val CENTRE_COURONNE_Y = 0.42f

    /**
     * Part d'une case qu'il faut couvrir pour la considérer perdue.
     *
     * Une case à moitié cachée reste utilisable ; au-delà, on n'y voit plus ce
     * qu'on y fait.
     */
    private const val SEUIL_MASQUAGE = 0.5f

    /**
     * Cases réellement masquées par le feuillage d'un arbre.
     *
     * Le défaut que cette fonction corrige : un arbre ne réservait que les
     * cases de son emprise, alors que sa couronne déborde largement — un arbre
     * d'une seule case recouvre environ trois quarts de la case située
     * au-dessus. Cette case restait achetable et cultivable, donc on pouvait y
     * semer sans jamais voir ce qui y poussait.
     *
     * La géométrie est **la même que celle du rendu** : elle part de l'ancrage
     * mesuré du tronc et du même débordement. Deux calculs séparés finiraient
     * par diverger, et la case bloquée ne serait plus celle qu'on voit cachée.
     */
    fun casesMasquees(arbre: Arbre, debordement: Float = 1.35f): Set<Pair<Int, Int>> {
        val taille = arbre.taille
        val emprise = ArbreSankaiEngine.casesOccupees(
            ArbreSankaiEngine.Case(arbre.x, arbre.y), taille
        ).map { it.x to it.y }.toSet()

        // Tout en unités de case, pour ne dépendre ni du zoom ni de l'écran.
        val cote = maxOf(taille.emprise.largeur, taille.emprise.hauteur) *
            echelle(arbre.x, arbre.y) * debordement
        val (centreX, centreY) = taille.emprise.centre
        val troncX = arbre.x + centreX
        val troncY = arbre.y + centreY

        val gauche = troncX - ArbreSankaiEngine.ANCRAGE_X * cote
        val haut = troncY - ArbreSankaiEngine.ANCRAGE_Y * cote
        val couronneX = gauche + 0.5f * cote
        val couronneY = haut + CENTRE_COURONNE_Y * cote
        val rayon = RAYON_COURONNE * cote

        val masquees = mutableSetOf<Pair<Int, Int>>()
        val portee = kotlin.math.ceil(rayon).toInt() + 1

        for (dy in -portee..portee) {
            for (dx in -portee..portee) {
                val cx = arbre.x + dx
                val cy = arbre.y + dy
                if ((cx to cy) in emprise) continue

                // Échantillonnage régulier : une intersection cercle/carré
                // exacte ne vaut pas sa complexité pour une décision binaire.
                var couverts = 0
                for (sy in 0 until ECHANTILLONS) {
                    for (sx in 0 until ECHANTILLONS) {
                        val px = cx + (sx + 0.5f) / ECHANTILLONS
                        val py = cy + (sy + 0.5f) / ECHANTILLONS
                        val ex = px - couronneX
                        val ey = py - couronneY
                        if (ex * ex + ey * ey <= rayon * rayon) couverts++
                    }
                }
                val part = couverts.toFloat() / (ECHANTILLONS * ECHANTILLONS)
                if (part > SEUIL_MASQUAGE) masquees += cx to cy
            }
        }
        return masquees
    }

    private const val ECHANTILLONS = 6

    /** Toutes les cases rendues inutilisables par les arbres d'une île. */
    fun casesReservees(arbres: List<Arbre>): Set<Pair<Int, Int>> {
        val reservees = mutableSetOf<Pair<Int, Int>>()
        arbres.forEach { arbre ->
            reservees += ArbreSankaiEngine
                .casesOccupees(ArbreSankaiEngine.Case(arbre.x, arbre.y), arbre.taille)
                .map { it.x to it.y }
            reservees += casesMasquees(arbre)
        }
        return reservees
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
