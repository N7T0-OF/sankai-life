package com.sankailife.core.island.domain

/**
 * Les bâtiments de l'île : leur emprise, leur coût et où on peut les poser.
 *
 * Un bâtiment n'est pas une case décorée : il réserve plusieurs cases, il a une
 * entrée, et il doit rester atteignable. Traiter ces trois choses séparément
 * est la façon habituelle de se retrouver avec une Boutique qu'on ne peut plus
 * atteindre après avoir bâti autour.
 */
object IslandBuildingEngine {

    /**
     * Types disponibles.
     *
     * Les autres bâtiments annoncés — serre, laboratoire, école — ne sont pas
     * listés ici tant qu'ils n'ont rien à faire. Une entrée dans un énuméré
     * qu'aucun code ne sert est une promesse que l'interface finit par
     * afficher.
     */
    enum class Type(
        val id: String,
        val libelle: String,
        val emoji: String,
        val largeur: Int,
        val hauteur: Int,
        val prix: Int,
        val niveauRequis: Int,
        /** Doit être posé au bord de l'eau. */
        val cotier: Boolean = false
    ) {
        BOUTIQUE("boutique", "Boutique", "🏪", 2, 2, 500, 1),
        DEPOT("depot", "Dépôt", "📦", 2, 2, 300, 2),

        /**
         * Le Port doit toucher l'eau. C'est la seule contrainte de terrain
         * particulière du jeu, et elle est vérifiée à part : un port au milieu
         * des terres n'aurait aucun sens même sur un emplacement par ailleurs
         * valide.
         */
        PORT("port", "Port", "⚓", 3, 2, 900, 4, cotier = true),

        /** Fait travailler les Mimos davantage pendant une absence. */
        ATELIER("atelier", "Atelier Mimo", "🛠️", 2, 2, 750, 6);

        companion object {
            fun parId(id: String): Type? = entries.firstOrNull { it.id == id }
        }
    }

    /** Cases occupées si le bâtiment est posé en (x, y). */
    fun casesOccupees(type: Type, x: Int, y: Int): List<Pair<Int, Int>> =
        (0 until type.hauteur).flatMap { dy ->
            (0 until type.largeur).map { dx -> (x + dx) to (y + dy) }
        }

    fun clesOccupees(type: Type, x: Int, y: Int, largeurIle: Int): Set<Int> =
        casesOccupees(type, x, y).map { (cx, cy) -> cy * largeurIle + cx }.toSet()

    /**
     * Centre de l'emprise, en cases.
     *
     * Sur une emprise paire, le centre tombe sur la frontière : un bâtiment
     * 2 × 2 se dessine à l'intersection des quatre cases, pas au milieu de
     * l'une d'elles.
     */
    fun centre(type: Type, x: Int, y: Int): Pair<Float, Float> =
        (x + type.largeur / 2f) to (y + type.hauteur / 2f)

    /** Réponse à « puis-je bâtir ici ? ». */
    sealed interface Verdict {
        data class Oui(val prix: Int) : Verdict
        data class Non(val raison: String) : Verdict
    }

    /**
     * Décide d'une construction.
     *
     * [terrainDe] renvoie le type de case, `null` hors de l'île. [occupee] dit
     * si une case est déjà prise par un bâtiment ou une culture.
     *
     * L'ordre des refus suit ce que le joueur peut y changer : d'abord ce qui
     * est définitif — le terrain —, ensuite le niveau, enfin l'argent.
     */
    fun peutBatir(
        type: Type,
        x: Int,
        y: Int,
        niveauJoueur: Int,
        pieces: Int,
        dejaConstruit: Boolean,
        terrainDe: (Int, Int) -> IslandTileType?,
        occupee: (Int, Int) -> Boolean
    ): Verdict {
        if (dejaConstruit) {
            return Verdict.Non("Tu as déjà ${unOuUne(type)} ${type.libelle.lowercase()}.")
        }

        val cases = casesOccupees(type, x, y)
        val terrains = cases.map { (cx, cy) -> terrainDe(cx, cy) }

        if (terrains.any { it == null }) {
            return Verdict.Non("${type.libelle} dépasse du bord de l'île.")
        }
        if (terrains.any { it!!.estEau }) {
            return Verdict.Non("On ne bâtit pas sur l'eau.")
        }
        if (terrains.any { !it!!.constructible }) {
            // Bois et rocher sont constructibles une fois dégagés ; ici on
            // parle du terrain brut, d'où la distinction.
            return Verdict.Non("Le terrain doit être dégagé sur les ${cases.size} cases.")
        }
        if (cases.any { (cx, cy) -> occupee(cx, cy) }) {
            return Verdict.Non("Il y a déjà quelque chose sur cet emplacement.")
        }
        if (type.cotier && !bordeEau(type, x, y, terrainDe)) {
            return Verdict.Non("${type.libelle} doit toucher l'eau.")
        }
        if (niveauJoueur < type.niveauRequis) {
            return Verdict.Non("${type.libelle} demande le niveau ${type.niveauRequis}.")
        }
        if (pieces < type.prix) {
            return Verdict.Non("Il te manque ${type.prix - pieces} pièces.")
        }
        return Verdict.Oui(type.prix)
    }

    /**
     * L'emprise touche-t-elle l'eau ?
     *
     * Vérifié sur le **voisinage** de l'emprise et non sur ses propres cases :
     * un bâtiment posé sur l'eau serait refusé plus haut, donc c'est bien la
     * case d'à côté qui doit être mouillée.
     */
    fun bordeEau(
        type: Type,
        x: Int,
        y: Int,
        terrainDe: (Int, Int) -> IslandTileType?
    ): Boolean {
        val emprise = casesOccupees(type, x, y).toSet()
        return emprise.any { (cx, cy) ->
            listOf(cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)
                .any { it !in emprise && terrainDe(it.first, it.second)?.estEau == true }
        }
    }

    /**
     * Le bâtiment reste-t-il atteignable à pied ?
     *
     * Vérifié séparément parce que ce n'est pas une propriété de l'emplacement
     * mais de son voisinage : il faut au moins une case franchissable
     * adjacente à l'emprise. Sans ce contrôle, on peut bâtir une Boutique
     * entourée d'eau et ne plus jamais y entrer.
     */
    fun accessible(
        type: Type,
        x: Int,
        y: Int,
        franchissable: (Int, Int) -> Boolean
    ): Boolean {
        val emprise = casesOccupees(type, x, y).toSet()
        return emprise.any { (cx, cy) ->
            listOf(cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)
                .any { it !in emprise && franchissable(it.first, it.second) }
        }
    }

    private fun unOuUne(type: Type): String = when (type) {
        Type.BOUTIQUE -> "une"
        Type.DEPOT, Type.PORT, Type.ATELIER -> "un"
    }
}
