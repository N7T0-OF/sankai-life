package com.sankailife.core.domain.engine

/**
 * Décide si une pastille de notification doit s'afficher.
 *
 * **Le défaut : un badge sur une porte fermée.** L'onglet Défis se débloque au
 * niveau 5 ; au niveau 2 il est grisé, cadenassé — et il portait quand même sa
 * pastille « 3 ». On annonçait trois choses à réclamer derrière une porte
 * qu'on ne peut pas ouvrir. C'est une promesse en l'air, et la seule chose
 * qu'elle produit est un aller-retour pour découvrir qu'on ne peut rien faire.
 *
 * La règle est centralisée ici et pas décidée écran par écran. Six endroits qui
 * appliquent chacun leur version de la même règle finissent toujours par ne
 * plus être d'accord — et c'est exactement ce qui s'était passé : le verrou
 * était calculé pour griser l'onglet, et ignoré pour la pastille, à trois
 * lignes d'intervalle.
 */
object BadgeEngine {

    /**
     * Faut-il afficher une pastille ?
     *
     * @param fonction la fonctionnalité concernée, ou `null` pour ce qui n'est
     *   jamais verrouillé — l'accueil, le profil.
     * @param compte nombre d'éléments en attente.
     */
    fun afficher(fonction: DeblocageEngine.Fonction?, niveau: Int, compte: Int): Boolean {
        if (compte <= 0) return false
        if (fonction == null) return true
        return DeblocageEngine.estDebloquee(fonction, niveau)
    }

    /**
     * Le nombre à peindre, ou 0 pour ne rien peindre.
     *
     * Rendre un entier plutôt qu'un booléen évite à l'appelant de refaire le
     * test avant d'afficher : un `if` de plus dans un composable est un `if` de
     * plus à oublier.
     */
    fun compte(fonction: DeblocageEngine.Fonction?, niveau: Int, compte: Int): Int =
        if (afficher(fonction, niveau, compte)) compte else 0

    /**
     * Fonctionnalité gardée par un onglet de navigation.
     *
     * Le tableau vit ici et non dans la barre : c'est la même association qui
     * sert à griser l'onglet et à masquer sa pastille, et les séparer était la
     * cause du défaut.
     */
    fun fonctionDeRoute(route: String?): DeblocageEngine.Fonction? = when (route) {
        "challenges" -> DeblocageEngine.Fonction.DEFIS
        else -> null
    }
}
