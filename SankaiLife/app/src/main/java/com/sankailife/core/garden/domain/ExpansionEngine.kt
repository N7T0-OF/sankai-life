package com.sankailife.core.garden.domain

import kotlin.math.abs

/**
 * L'expansion du jardin.
 *
 * Le terrain était une liste de seize cases lues comme quatre colonnes : on
 * progressait vers le bas, sans choix. Il devient un plan dont on part du
 * centre et qu'on étend dans la direction qu'on veut, une case à la fois.
 *
 * Le changement n'est pas cosmétique. Un index linéaire ne peut pas exprimer
 * « la case au nord de celle-ci » ; il fallait des coordonnées.
 */
object ExpansionEngine {

    /**
     * Côté de la grille logique. Très large exprès : on n'en débloque qu'une
     * poignée de cases, mais la limite ne doit jamais être atteignable.
     */
    const val COTE = 40
    const val CENTRE = COTE / 2

    /**
     * Clé d'une case. La coordonnée *est* la clé primaire — une table de
     * correspondance séparée n'apporterait qu'une jointure de plus.
     */
    fun cle(x: Int, y: Int): Int = y * COTE + x
    fun xDe(cle: Int): Int = cle % COTE
    fun yDe(cle: Int): Int = cle / COTE

    /** État de déblocage. Indépendant de ce qui pousse sur la case. */
    enum class Deblocage {
        /** Dans le brouillard : ni visible, ni achetable. */
        CACHEE,
        /** Visible car adjacente à une case possédée, donc achetable. */
        DECOUVERTE,
        /** Chantier en cours. */
        EN_CHANTIER,
        /** Utilisable. */
        DEBLOQUEE
    }

    /**
     * Terrains disponibles à l'extension.
     *
     * Chacun a une contrepartie : sans ça, le joueur achèterait toujours le
     * même et le choix de direction n'existerait pas.
     */
    enum class Terrain(
        val libelle: String,
        val emoji: String,
        val sol: SoilType,
        /** Multiplicateur appliqué au prix de base. */
        val facteurPrix: Float,
        val aNettoyer: Boolean,
        val note: String
    ) {
        FERTILE("Terre fertile", "🟤", SoilType.RICHE, 1.6f, false,
            "Croissance accélérée"),
        ORDINAIRE("Terre ordinaire", "🟫", SoilType.TERRE, 1.0f, false,
            "Sans particularité"),
        HUMIDE("Sol humide", "💧", SoilType.HUMIDE, 1.3f, false,
            "Sèche lentement"),
        SABLEUX("Sable", "🏜️", SoilType.SABLE, 0.7f, false,
            "Sèche vite • seul sol à cactus"),
        ROCHEUX("Terrain rocheux", "🪨", SoilType.TERRE, 0.6f, true,
            "À nettoyer • minéraux"),
        FORESTIER("Lisière", "🌳", SoilType.RICHE, 1.2f, true,
            "Ombragé • sèche lentement"),
        ABANDONNE("Friche", "🍂", SoilType.TERRE, 0.4f, true,
            "Bon marché • beaucoup de débris");

        companion object {
            fun parNom(valeur: String): Terrain =
                entries.firstOrNull { it.name == valeur } ?: ORDINAIRE
        }
    }

    /** Les quatre voisines. Les diagonales sont volontairement exclues. */
    fun voisines(cle: Int): List<Int> {
        val x = xDe(cle)
        val y = yDe(cle)
        return buildList {
            if (y > 0) add(cle(x, y - 1))
            if (y < COTE - 1) add(cle(x, y + 1))
            if (x > 0) add(cle(x - 1, y))
            if (x < COTE - 1) add(cle(x + 1, y))
        }
    }

    fun sontVoisines(a: Int, b: Int): Boolean = voisines(a).contains(b)

    /** Distance de Manhattan au centre, qui pilote le prix. */
    fun distanceAuCentre(cle: Int): Int =
        abs(xDe(cle) - CENTRE) + abs(yDe(cle) - CENTRE)

    private const val PRIX_BASE = 180

    /**
     * Prix d'une case.
     *
     * Croît avec l'éloignement, sinon rien n'empêcherait de s'étendre
     * indéfiniment dès la première heure de jeu. La progression est
     * quadratique douce : les premières extensions restent accessibles, les
     * lointaines demandent une vraie économie.
     */
    fun cout(cle: Int, terrain: Terrain): Int {
        val d = distanceAuCentre(cle).coerceAtLeast(1)
        val base = PRIX_BASE * (1 + (d - 1) * (d - 1) / 4f)
        return (base * terrain.facteurPrix).toInt().coerceAtLeast(40)
    }

    /**
     * Durée du chantier, en minutes.
     *
     * Un déblocage instantané ferait de l'expansion un simple achat. Le
     * chantier laisse le temps de continuer à jouer ailleurs — et donne aux
     * Mimos constructeurs quelque chose à accélérer.
     */
    fun dureeChantierMinutes(cle: Int, terrain: Terrain): Long {
        val d = distanceAuCentre(cle).coerceAtLeast(1)
        val base = 20L + d * 15L
        return if (terrain.aNettoyer) (base * 1.5f).toLong() else base
    }

    /**
     * Peut-on acheter cette case ?
     *
     * La seule règle est l'adjacence à une case possédée. C'est ce qui rend
     * l'expansion organique : on avance de proche en proche, pas en sautant.
     */
    fun estAchetable(cle: Int, possedees: Set<Int>): Boolean {
        if (cle in possedees) return false
        return voisines(cle).any { it in possedees }
    }

    /**
     * Les cases qui doivent sortir du brouillard.
     *
     * Appelé après chaque déblocage : le brouillard recule d'un cran, ce qui
     * révèle de nouvelles possibilités sans jamais montrer tout le plan.
     */
    fun frontiere(possedees: Set<Int>): Set<Int> =
        possedees.flatMap { voisines(it) }.filter { it !in possedees }.toSet()

    /**
     * Terrain d'une case, dérivé de sa position.
     *
     * Calculé, pas tiré au sort ni stocké : deux appareils affichent le même
     * jardin, et rouvrir l'application ne change pas ce qu'on croyait acheter.
     * Le sable s'étend au sud-est, la forêt au nord — assez pour que la
     * direction choisie ait un sens.
     */
    fun terrainDe(cle: Int): Terrain {
        val dx = xDe(cle) - CENTRE
        val dy = yDe(cle) - CENTRE
        val bruit = abs((dx * 73 + dy * 151) * 2654435761L.toInt()) % 100

        return when {
            dy < -2 && bruit < 55 -> Terrain.FORESTIER
            dx > 2 && dy > 2 && bruit < 60 -> Terrain.SABLEUX
            dx < -2 && bruit < 45 -> Terrain.HUMIDE
            bruit < 12 -> Terrain.FERTILE
            bruit < 26 -> Terrain.ROCHEUX
            bruit < 34 -> Terrain.ABANDONNE
            else -> Terrain.ORDINAIRE
        }
    }

    /** Les quatre cases de départ, autour du centre. */
    fun casesInitiales(): List<Int> = listOf(
        cle(CENTRE, CENTRE),
        cle(CENTRE + 1, CENTRE),
        cle(CENTRE, CENTRE + 1),
        cle(CENTRE + 1, CENTRE + 1)
    )
}
