package com.sankailife.core.garden.domain

/**
 * L'Arbre Sankai : l'emprise qu'il occupe sur la grille et son ancrage.
 *
 * Deux problèmes distincts sont traités ici, et les confondre est la façon
 * habituelle de rater un décor multi-cases.
 *
 * **L'emprise** est une affaire de données : quelles cases sont réservées,
 * donc interdites à la plantation. Elle ne dépend d'aucune image.
 *
 * **L'ancrage** est une affaire de dessin : où poser le fichier pour que le
 * tronc tombe au bon endroit. Le PNG livré n'est ni centré ni posé au bas de
 * son cadre — le feuillage déborde à gauche et le tronc s'arrête avant le bord
 * inférieur. Centrer naïvement le bitmap ferait flotter l'arbre au-dessus du
 * sol et le décalerait horizontalement.
 */
object ArbreSankaiEngine {

    /**
     * Centre horizontal du tronc, en fraction de la largeur du fichier.
     *
     * Mesuré sur `tree_sankai.png` : les pixels opaques de la bande basse
     * s'étendent de part et d'autre de 0,519 — et non de 0,5. Le feuillage,
     * lui, est décentré vers la gauche ; se fier à la boîte englobante donnerait
     * une autre valeur, et fausse.
     */
    const val ANCRAGE_X = 0.519f

    /**
     * Base du tronc, en fraction de la hauteur du fichier.
     *
     * Le fichier laisse 6,6 % de vide sous le tronc. Poser l'image sur la ligne
     * de sol ferait léviter l'arbre d'autant.
     */
    const val ANCRAGE_Y = 0.934f

    /** Case de la grille, en coordonnées absolues du monde. */
    data class Case(val x: Int, val y: Int)

    /**
     * Emprise : les cases occupées, exprimées par rapport à la case d'origine.
     *
     * Générique à dessein. Le jour où un bâtiment occupe une forme en L, il n'y
     * a rien à réécrire.
     */
    data class Emprise(val cases: List<Case>) {
        init {
            require(cases.isNotEmpty()) { "Une emprise vide n'occupe rien." }
            require(cases.distinct().size == cases.size) { "Case répétée dans l'emprise." }
        }

        val largeur: Int get() = cases.maxOf { it.x } - cases.minOf { it.x } + 1
        val hauteur: Int get() = cases.maxOf { it.y } - cases.minOf { it.y } + 1

        /**
         * Centre de masse de l'emprise, en cases.
         *
         * C'est là que tombe le tronc. Le calculer plutôt que de le déclarer
         * garde les formes paires correctes : sur deux cases côte à côte, le
         * tronc se pose sur leur frontière (0,5) et non au milieu de l'une des
         * deux — coller l'arbre sur la case de gauche est le défaut classique.
         */
        val centre: Pair<Float, Float>
            get() = Pair(
                cases.map { it.x }.average().toFloat() + 0.5f,
                cases.map { it.y }.average().toFloat() + 0.5f
            )
    }

    /**
     * Tailles disponibles.
     *
     * `TROIS` mérite une réserve honnête : le feuillage de l'arbre est rond, et
     * une emprise en L laisse une case libre que la couronne recouvre quand
     * même. Un joueur peut y planter et ne plus rien voir pousser. La forme est
     * fournie parce qu'elle est demandée ; pour un dessin rond, `DEUX` et
     * `QUATRE` sont les seules qui ne mentent pas.
     */
    enum class Taille(val emprise: Emprise) {
        UNE(Emprise(listOf(Case(0, 0)))),

        DEUX(Emprise(listOf(Case(0, 0), Case(1, 0)))),

        TROIS(Emprise(listOf(Case(0, 0), Case(1, 0), Case(1, 1)))),

        QUATRE(Emprise(listOf(Case(0, 0), Case(1, 0), Case(0, 1), Case(1, 1))))
    }

    /** Cases du monde réellement occupées si l'arbre est posé en [origine]. */
    fun casesOccupees(origine: Case, taille: Taille): List<Case> =
        taille.emprise.cases.map { Case(origine.x + it.x, origine.y + it.y) }

    /** Les mêmes, sous forme de clés de grille. */
    fun clesOccupees(origine: Case, taille: Taille): Set<Int> =
        casesOccupees(origine, taille).map { ExpansionEngine.cle(it.x, it.y) }.toSet()

    /**
     * Le placement est-il possible ?
     *
     * [casesLibres] ne contient que des cases plantables et vides. Une case
     * inconnue — hors terrain débloqué — est absente, donc refusée : un arbre à
     * cheval sur le brouillard réserverait des cases qui n'existent pas encore.
     */
    fun placementValide(
        origine: Case,
        taille: Taille,
        casesLibres: Set<Int>
    ): Boolean = clesOccupees(origine, taille).all { it in casesLibres }

    /**
     * Position du tronc à l'écran, en pixels.
     *
     * @param origineEcran position à l'écran du coin haut-gauche de la case
     *   d'origine.
     * @param pas taille d'une case à l'écran.
     */
    fun troncEcran(origineEcran: Pair<Float, Float>, pas: Float, taille: Taille): Pair<Float, Float> {
        val (cx, cy) = taille.emprise.centre
        return Pair(origineEcran.first + cx * pas, origineEcran.second + cy * pas)
    }

    /**
     * Rectangle où dessiner le fichier, pour que son tronc tombe sur [tronc].
     *
     * Renvoie `(gauche, haut, cote)`. Le dessin est carré comme le fichier :
     * l'étirer pour remplir l'emprise déformerait la couronne.
     *
     * Le côté vaut la plus grande dimension de l'emprise, majorée : une
     * couronne ronde doit déborder des cases qu'elle réserve, sinon l'arbre a
     * l'air taillé au carré.
     */
    fun cadreDessin(
        tronc: Pair<Float, Float>,
        pas: Float,
        taille: Taille,
        debordement: Float = 1.35f
    ): Triple<Float, Float, Float> {
        val cote = maxOf(taille.emprise.largeur, taille.emprise.hauteur) * pas * debordement
        return Triple(
            tronc.first - ANCRAGE_X * cote,
            tronc.second - ANCRAGE_Y * cote,
            cote
        )
    }

    /**
     * Taille atteinte à une arène donnée.
     *
     * La progression ne redescend jamais : un arbre qui rétrécit se lirait comme
     * une punition alors que rien n'a été perdu.
     */
    fun taillePourArene(arene: Int): Taille = when {
        arene >= 7 -> Taille.QUATRE
        arene >= 5 -> Taille.TROIS
        arene >= 3 -> Taille.DEUX
        else -> Taille.UNE
    }
}
