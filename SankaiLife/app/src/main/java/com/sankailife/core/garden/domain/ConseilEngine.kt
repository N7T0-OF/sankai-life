package com.sankailife.core.garden.domain

/**
 * Le conseil du moment.
 *
 * La grande carte sous le jardin affichait toujours quelque chose, souvent la
 * même phrase générique. Elle occupait de la hauteur pour ne rien apprendre.
 *
 * Ici, **un seul conseil à la fois**, choisi par priorité, et rien du tout
 * quand il n'y a rien à dire. Une bulle vide est une bulle qu'on ignore ; une
 * bulle qui parle rarement est une bulle qu'on lit.
 */
object ConseilEngine {

    /**
     * Les conseils, du plus urgent au plus anecdotique.
     *
     * L'ordre de déclaration **est** l'ordre de priorité : le premier dont la
     * condition est remplie gagne. Une constante de priorité séparée finirait
     * par diverger de cet ordre.
     */
    enum class Type(val emoji: String) {
        /** Le terrain sature : plus rien ne peut être récolté. */
        DEPOT_PLEIN("📦"),
        /** Des cartes sont dues : c'est la raison d'être de l'application. */
        CARTES_DUES("📚"),
        /** Plus d'eau, et l'apprentissage est le seul moyen d'en produire. */
        PLUS_D_EAU("💧"),
        /** Des plantes attendent d'être récoltées. */
        RECOLTE_PRETE("🧺"),
        /** Des parcelles ont soif. */
        PARCELLES_SECHES("🌵"),
        /** Le stock attend d'être vendu et le marchand est là. */
        STOCK_VENDABLE("🪙"),
        /** Il va pleuvoir : inutile d'arroser. */
        PLUIE_ATTENDUE("🌧️"),
        /** Les Mimos n'ont plus de compost. */
        MIMOS_AFFAMES("🌱")
    }

    data class Conseil(
        val type: Type,
        val texte: String,
        /** Libellé du bouton d'action, ou null si le conseil est informatif. */
        val action: String? = null
    )

    /** Ce que la bulle a besoin de savoir sur le jardin. */
    data class Contexte(
        val cartesDues: Int = 0,
        val eau: Int = 0,
        val compost: Int = 0,
        val nombreMimos: Int = 0,
        val parcellesPretes: Int = 0,
        val parcellesSeches: Int = 0,
        val caissesPosees: Int = 0,
        val terrainSature: Boolean = false,
        val valeurStock: Int = 0,
        val magasinOuvert: Boolean = false,
        val ilVaPleuvoir: Boolean = false
    )

    /**
     * Le conseil le plus utile, ou null.
     *
     * Renvoyer null est un cas normal, pas un échec : quand tout va bien, la
     * bulle disparaît au lieu d'inventer quelque chose à dire.
     */
    fun choisir(c: Contexte): Conseil? = when {
        c.terrainSature || c.caissesPosees >= DepotEngine.CAPACITE_CAISSES - 2 ->
            Conseil(
                Type.DEPOT_PLEIN,
                "Le terrain se couvre de caisses. Range-les au dépôt avant de récolter.",
                "Ranger"
            )

        // L'apprentissage passe avant l'agriculture. C'est le seul endroit du
        // code où cette hiérarchie est affirmée, et elle est délibérée.
        c.cartesDues > 0 -> Conseil(
            Type.CARTES_DUES,
            "${c.cartesDues} carte(s) sont à réviser aujourd'hui.",
            "Réviser"
        )

        c.eau <= 0 -> Conseil(
            Type.PLUS_D_EAU,
            "Ta réserve d'eau est vide. Une révision en produit.",
            "Réviser"
        )

        c.parcellesPretes > 0 -> Conseil(
            Type.RECOLTE_PRETE,
            "${c.parcellesPretes} plante(s) sont prêtes à récolter.",
            "Récolter"
        )

        // La pluie prime sur la soif : arroser juste avant une averse gaspille
        // une eau qu'il a fallu gagner.
        c.ilVaPleuvoir && c.parcellesSeches > 0 -> Conseil(
            Type.PLUIE_ATTENDUE,
            "Il pleut : inutile d'arroser, le jardin se sert tout seul."
        )

        c.parcellesSeches > 0 -> Conseil(
            Type.PARCELLES_SECHES,
            "${c.parcellesSeches} parcelle(s) ont soif."
        )

        c.nombreMimos > 0 && c.compost <= 0 -> Conseil(
            Type.MIMOS_AFFAMES,
            "Tes Mimos n'ont plus de compost. Récolte pour en produire."
        )

        c.magasinOuvert && c.valeurStock > 0 -> Conseil(
            Type.STOCK_VENDABLE,
            "Ton dépôt vaut ${c.valeurStock} pièces et le marchand est là.",
            "Vendre"
        )

        else -> null
    }

    /**
     * Capsule d'apprentissage : ce qui reste à réviser.
     *
     * Séparée du conseil parce qu'elle doit rester visible même quand un
     * conseil plus urgent s'affiche. C'est le rappel permanent que le jardin
     * n'est pas le but.
     */
    data class Capsule(val faites: Int, val total: Int) {
        val visible: Boolean get() = total > 0
        val libelle: String get() = "$faites / $total"
        val terminee: Boolean get() = total > 0 && faites >= total
    }
}
