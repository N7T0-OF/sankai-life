package com.sankailife.core.island.domain

/**
 * Ce qu'un joueur peut acheter sur son île, et à quel prix.
 *
 * L'île entière est visible dès le premier lancement — c'est ce qui remplace
 * l'ancien brouillard. Mais tout n'est pas utilisable : le joueur achète les
 * parcelles une par une, où il veut, dans la limite de son niveau.
 *
 * Le point de conception qui compte : un refus n'est **jamais** un simple
 * `false`. L'écran doit pouvoir dire « il te faut le niveau 5 » ou « on ne
 * cultive pas la plage », faute de quoi le joueur appuie sur une case et rien
 * ne se passe, ce qui se lit comme une panne.
 */
object IslandSlotEngine {

    /** État d'une case du point de vue du joueur. */
    enum class Etat {
        /** Terrain naturel : visible, jamais utilisable (eau, plage, rocher…). */
        NATUREL,

        /** Cultivable, mais le niveau est insuffisant ou le plafond est atteint. */
        VERROUILLE,

        /** Achetable tout de suite. */
        DISPONIBLE,

        /** Achetée, en attente d'être préparée. */
        ACHETEE,

        /** Occupée par un bâtiment ou un décor permanent. */
        OCCUPEE,

        /** Contient une culture. */
        CULTIVEE
    }

    /** Réponse à « puis-je acheter ici ? ». */
    sealed interface Verdict {
        data class Oui(val prix: Int) : Verdict
        data class Non(val raison: String) : Verdict
    }

    // ------------------------------------------------------------------
    // Prix
    // ------------------------------------------------------------------

    /**
     * Prix de base selon le terrain, avant progression.
     *
     * Le rocher est le moins cher et c'est volontaire : il faut ensuite le
     * dégager. On paie le terrain, pas le travail.
     */
    fun prixBase(type: IslandTileType): Int = when (type) {
        IslandTileType.GRASS -> 100
        IslandTileType.FERTILE_GRASS -> 180
        IslandTileType.FOREST -> 70
        IslandTileType.ROCK -> 60
        else -> 0
    }

    /**
     * Multiplicateur lié au nombre de parcelles déjà possédées.
     *
     * Le cahier des charges donne deux barèmes — une échelle 50/100/200/350/500
     * et des prix par terrain. Ils sont conciliés ainsi : le **terrain** fixe la
     * base, la **progression** la multiplie. Les deux listes disent alors la
     * même chose au lieu de se contredire, et une terre grasse reste plus chère
     * qu'une plaine à tout moment de la partie.
     *
     * Les premières parcelles sont gratuites : demander de l'argent à quelqu'un
     * qui n'en a pas encore gagné bloquerait la première session.
     */
    fun multiplicateur(dejaPossedes: Int): Float = when {
        dejaPossedes < GRATUITES -> 0f
        dejaPossedes < 6 -> 0.5f
        dejaPossedes < 10 -> 1f
        dejaPossedes < 16 -> 2f
        dejaPossedes < 24 -> 3.5f
        else -> 5f
    }

    /** Parcelles offertes au démarrage. */
    const val GRATUITES = 2

    fun prix(type: IslandTileType, dejaPossedes: Int): Int {
        val base = prixBase(type)
        if (base == 0) return 0
        // Arrondi à la dizaine : un prix à 187 pièces ne se retient pas et
        // n'apporte rien.
        val brut = base * multiplicateur(dejaPossedes.coerceAtLeast(0))
        return ((brut / 10f).toInt()) * 10
    }

    // ------------------------------------------------------------------
    // Plafond par niveau
    // ------------------------------------------------------------------

    /**
     * Nombre maximal de parcelles possédées à un niveau donné.
     *
     * Ce plafond est ce qui empêche d'acheter l'île entière au premier jour, et
     * donc ce qui relie la progression du jardin à l'apprentissage : on monte de
     * niveau en révisant.
     *
     * Les paliers sont interpolés entre les valeurs annoncées plutôt que de
     * sauter : sans interpolation, les niveaux 4, 6 et 7 n'apporteraient rien du
     * tout, et gagner un niveau sans rien débloquer décourage.
     */
    fun plafond(niveau: Int): Int {
        val n = niveau.coerceAtLeast(1)
        val paliers = listOf(1 to 4, 2 to 6, 3 to 9, 5 to 14, 8 to 22, 12 to 32, 20 to 50)

        paliers.lastOrNull()?.let { (dernierNiveau, dernierPlafond) ->
            if (n >= dernierNiveau) return dernierPlafond
        }

        for (i in 0 until paliers.size - 1) {
            val (n1, p1) = paliers[i]
            val (n2, p2) = paliers[i + 1]
            if (n in n1..n2) {
                if (n == n1) return p1
                val progression = (n - n1).toFloat() / (n2 - n1)
                return p1 + ((p2 - p1) * progression).toInt()
            }
        }
        return paliers.first().second
    }

    // ------------------------------------------------------------------
    // Règles
    // ------------------------------------------------------------------

    /**
     * Le terrain se prête-t-il à la culture ?
     *
     * La forêt et le rocher sont inclus : ils ne sont pas cultivables en
     * l'état, mais ils se dégagent. Refuser de les vendre priverait le joueur
     * des seules parcelles bon marché.
     */
    fun terrainAchetable(type: IslandTileType): Boolean =
        type.cultivable || type == IslandTileType.FOREST || type == IslandTileType.ROCK

    /**
     * Refus explicite pour un terrain qu'on ne vend pas.
     *
     * Chaque cas a sa phrase. « Impossible » ne fait pas comprendre qu'une
     * plage se construit mais ne se cultive pas.
     */
    fun raisonTerrain(type: IslandTileType): String? = when (type) {
        IslandTileType.DEEP_WATER, IslandTileType.SHALLOW_WATER -> "On ne cultive pas la mer."
        IslandTileType.RIVER -> "C'est le lit de la rivière."
        IslandTileType.POND -> "C'est l'étang."
        IslandTileType.BEACH -> "La plage reste libre : on y bâtit, on n'y plante pas."
        IslandTileType.DOCK -> "Le ponton est le seul accès à l'île."
        IslandTileType.PATH -> "Il y a un chemin ici."
        IslandTileType.BRIDGE -> "Il y a un pont ici."
        else -> null
    }

    /**
     * Décide d'un achat.
     *
     * L'ordre des contrôles suit ce que le joueur peut y faire. On lui dit
     * d'abord que le terrain ne s'achète pas — information définitive — avant de
     * parler de niveau ou d'argent, qui ne sont que des questions de temps.
     */
    fun peutAcheter(
        type: IslandTileType,
        dejaAchetee: Boolean,
        occupee: Boolean,
        niveauJoueur: Int,
        parcellesPossedees: Int,
        pieces: Int
    ): Verdict {
        if (occupee) return Verdict.Non("Cette case est déjà occupée.")
        if (dejaAchetee) return Verdict.Non("Cette parcelle t'appartient déjà.")

        raisonTerrain(type)?.let { return Verdict.Non(it) }
        if (!terrainAchetable(type)) return Verdict.Non("Ce terrain ne peut pas être cultivé.")

        val limite = plafond(niveauJoueur)
        if (parcellesPossedees >= limite) {
            return Verdict.Non(
                "Limite de $limite parcelles au niveau $niveauJoueur. Révise pour monter de niveau."
            )
        }

        val cout = prix(type, parcellesPossedees)
        if (pieces < cout) return Verdict.Non("Il te manque ${cout - pieces} pièces.")

        return Verdict.Oui(cout)
    }

    /**
     * État affiché pour une case.
     *
     * Distingue « verrouillé » de « naturel », parce que le premier finira par
     * s'ouvrir et le second jamais. Les afficher pareil laisserait croire que
     * l'océan deviendra un champ.
     */
    fun etat(
        type: IslandTileType,
        dejaAchetee: Boolean,
        occupee: Boolean,
        cultivee: Boolean,
        niveauJoueur: Int,
        parcellesPossedees: Int
    ): Etat = when {
        occupee -> Etat.OCCUPEE
        cultivee -> Etat.CULTIVEE
        dejaAchetee -> Etat.ACHETEE
        !terrainAchetable(type) -> Etat.NATUREL
        parcellesPossedees >= plafond(niveauJoueur) -> Etat.VERROUILLE
        else -> Etat.DISPONIBLE
    }
}
