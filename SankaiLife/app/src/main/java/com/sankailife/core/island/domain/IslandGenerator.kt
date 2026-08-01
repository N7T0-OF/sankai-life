package com.sankailife.core.island.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Génère une île depuis une graine.
 *
 * Tout est déterministe : la même graine rend toujours la même île, sur
 * n'importe quel appareil et à n'importe quel lancement. C'est ce qui permet de
 * partager une île par un simple code, et de ne persister qu'une graine si l'on
 * y tient un jour.
 *
 * Le bruit est réimplémenté ici plutôt qu'emprunté à une bibliothèque : une
 * dépendance de plus pour quarante lignes de calcul serait un mauvais échange
 * dans une application qui tient dans cinq mégaoctets — et sa version pourrait
 * changer le relief sous les pieds des joueurs.
 *
 * Aucun contact avec Room ni avec Compose : le générateur est du calcul pur,
 * donc entièrement vérifiable sans téléphone.
 */
object IslandGenerator {

    const val TAILLE_DEFAUT = 32

    /** Version du générateur. À incrémenter dès que le relief produit change. */
    const val VERSION = 1

    data class Case(val x: Int, val y: Int)

    /**
     * Une île générée.
     *
     * [tuiles] est un tableau à plat, indexé `y * largeur + x` : une liste de
     * listes coûterait un objet par ligne pour un gain de lisibilité nul.
     */
    data class Ile(
        val seed: Long,
        val largeur: Int,
        val hauteur: Int,
        val tuiles: List<IslandTileType>,
        val ponton: Case?,
        val zoneDepart: Case?,
        val version: Int = VERSION
    ) {
        fun type(x: Int, y: Int): IslandTileType =
            if (x !in 0 until largeur || y !in 0 until hauteur) IslandTileType.DEEP_WATER
            else tuiles[y * largeur + x]

        fun compter(predicat: (IslandTileType) -> Boolean): Int = tuiles.count(predicat)
    }

    // ------------------------------------------------------------------
    // Bruit de valeur
    // ------------------------------------------------------------------

    /**
     * Bruit de valeur lissé, sur une grille de nombres pseudo-aléatoires.
     *
     * Moins riche qu'un Perlin, largement suffisant pour découper une côte, et
     * sans la table de gradients qu'il faudrait embarquer.
     */
    private class Bruit(seed: Long) {
        private val valeurs = FloatArray(TAILLE_TABLE * TAILLE_TABLE)

        init {
            val alea = Random(seed)
            for (i in valeurs.indices) valeurs[i] = alea.nextFloat()
        }

        private fun brut(x: Int, y: Int): Float {
            // Le modulo positif referme la table sur elle-même : une
            // coordonnée négative doit lire une valeur, pas planter.
            val cx = ((x % TAILLE_TABLE) + TAILLE_TABLE) % TAILLE_TABLE
            val cy = ((y % TAILLE_TABLE) + TAILLE_TABLE) % TAILLE_TABLE
            return valeurs[cy * TAILLE_TABLE + cx]
        }

        /** Interpolation en courbe douce : une interpolation linéaire laisse voir la grille. */
        private fun adoucir(t: Float): Float = t * t * (3f - 2f * t)

        fun valeur(x: Float, y: Float): Float {
            val x0 = kotlin.math.floor(x).toInt()
            val y0 = kotlin.math.floor(y).toInt()
            val fx = adoucir(x - x0)
            val fy = adoucir(y - y0)

            val haut = brut(x0, y0) * (1 - fx) + brut(x0 + 1, y0) * fx
            val bas = brut(x0, y0 + 1) * (1 - fx) + brut(x0 + 1, y0 + 1) * fx
            return haut * (1 - fy) + bas * fy
        }

        /** Somme d'octaves : les grandes formes d'abord, le détail ensuite. */
        fun octaves(x: Float, y: Float, nombre: Int = 4): Float {
            var somme = 0f
            var amplitude = 1f
            var frequence = 1f
            var total = 0f
            repeat(nombre) {
                somme += valeur(x * frequence, y * frequence) * amplitude
                total += amplitude
                amplitude *= 0.5f
                frequence *= 2f
            }
            return somme / total
        }

        private companion object {
            const val TAILLE_TABLE = 64
        }
    }

    // ------------------------------------------------------------------
    // Génération
    // ------------------------------------------------------------------

    fun generer(seed: Long, taille: Int = TAILLE_DEFAUT): Ile {
        require(taille >= 16) { "Une île de moins de 16 cases n'a pas de place pour une plage." }

        val bruit = Bruit(seed)
        val alea = Random(seed xor 0x5DEECE66DL)
        val tuiles = MutableList(taille * taille) { IslandTileType.DEEP_WATER }

        val altitudes = relief(bruit, taille, alea)
        classer(altitudes, tuiles, taille)
        creuserRivieres(tuiles, altitudes, taille, alea)
        semerFertile(tuiles, bruit, taille)
        semerObstacles(tuiles, bruit, taille)

        val ponton = poserPonton(tuiles, taille, alea)
        val depart = choisirZoneDepart(tuiles, taille, ponton)

        return Ile(seed, taille, taille, tuiles.toList(), ponton, depart)
    }

    /**
     * Altitude de chaque case : bruit atténué par la distance au centre.
     *
     * L'atténuation radiale est ce qui garantit une île *fermée*. Le bruit seul
     * produirait des continents qui touchent le bord de la carte, et la
     * promesse « entourée d'eau de tous les côtés » ne tiendrait pas — c'est
     * l'un des critères de validation, autant le rendre structurellement vrai.
     */
    private fun relief(bruit: Bruit, taille: Int, alea: Random): FloatArray {
        val altitudes = FloatArray(taille * taille)

        // Le centre est décalé par la graine. Sans cela, toutes les îles sont
        // concentriques et se ressemblent au premier coup d'œil.
        val centre = (taille - 1) / 2f
        val decalageX = centre + (alea.nextFloat() - 0.5f) * taille * 0.10f
        val decalageY = centre + (alea.nextFloat() - 0.5f) * taille * 0.10f
        val rayonMax = centre

        for (y in 0 until taille) {
            for (x in 0 until taille) {
                val dx = (x - decalageX) / rayonMax
                val dy = (y - decalageY) / rayonMax
                val distance = sqrt(dx * dx + dy * dy)

                // Déformation de la distance elle-même.
                //
                // C'est ce qui fait la différence entre une île et un disque.
                // Atténuer un bruit par une distance nette donne toujours un
                // rond, quelle que soit la graine : le bruit ne fait que
                // grignoter le bord. En déformant la distance, la côte gagne de
                // vraies baies et de vrais caps, et deux graines cessent d'avoir
                // la même silhouette.
                val cote = bruit.octaves(x / (taille / 3.1f) + 71.3f, y / (taille / 3.1f) + 29.7f, 3)
                val distanceDeformee = distance + (cote - 0.5f) * 0.42f

                // Échelle du bruit : ~4 cellules sur la largeur, soit un relief
                // lisible plutôt qu'une dentelle.
                val n = bruit.octaves(x / (taille / 4f), y / (taille / 4f))

                val attenuation = 1f - min(1f, max(0f, distanceDeformee)).let { it * it }
                altitudes[y * taille + x] = (n * 0.55f + 0.45f) * attenuation
            }
        }
        return altitudes
    }

    private fun classer(
        altitudes: FloatArray,
        tuiles: MutableList<IslandTileType>,
        taille: Int
    ) {
        for (i in altitudes.indices) {
            tuiles[i] = when {
                altitudes[i] < SEUIL_EAU_PROFONDE -> IslandTileType.DEEP_WATER
                altitudes[i] < SEUIL_EAU_BASSE -> IslandTileType.SHALLOW_WATER
                altitudes[i] < SEUIL_PLAGE -> IslandTileType.BEACH
                else -> IslandTileType.GRASS
            }
        }
    }

    /**
     * Creuse une ou deux rivières, de l'intérieur vers l'océan.
     *
     * Le parcours descend l'altitude en s'autorisant des écarts : une descente
     * strictement gloutonne s'arrête dans le premier creux local, et laisse une
     * rivière qui ne rejoint jamais la mer. Comme la marche est bornée et
     * qu'elle vise une direction cardinale, elle finit toujours par sortir.
     */
    private fun creuserRivieres(
        tuiles: MutableList<IslandTileType>,
        altitudes: FloatArray,
        taille: Int,
        alea: Random
    ) {
        val nombre = if (alea.nextFloat() < 0.45f) 2 else 1
        repeat(nombre) {
            // Départ : une case de terre parmi les plus hautes, tirée dans le
            // quart central pour que la rivière traverse vraiment l'île.
            val marge = taille / 4
            var meilleur: Int? = null
            repeat(24) {
                val x = alea.nextInt(marge, taille - marge)
                val y = alea.nextInt(marge, taille - marge)
                val i = y * taille + x
                if (tuiles[i] == IslandTileType.GRASS &&
                    (meilleur == null || altitudes[i] > altitudes[meilleur!!])
                ) meilleur = i
            }
            val depart = meilleur ?: return@repeat

            var x = depart % taille
            var y = depart / taille
            // Direction de sortie : celle du bord le plus proche.
            val versDroite = x > taille / 2
            val versBas = y > taille / 2
            val horizontal = alea.nextBoolean()

            val largeur = 1 + alea.nextInt(2)
            var pas = 0
            while (pas < taille * 3) {
                pas++
                val type = tuiles.getOrNull(y * taille + x) ?: break
                if (type == IslandTileType.DEEP_WATER || type == IslandTileType.SHALLOW_WATER) break

                for (dx in 0 until largeur) {
                    for (dy in 0 until largeur) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until taille && ny in 0 until taille) {
                            val i = ny * taille + nx
                            if (tuiles[i].estTerre) tuiles[i] = IslandTileType.RIVER
                        }
                    }
                }

                // Avance d'une case vers la sortie, avec un écart latéral
                // occasionnel qui donne le méandre.
                if (horizontal) {
                    x += if (versDroite) 1 else -1
                    if (alea.nextFloat() < 0.35f) y += if (alea.nextBoolean()) 1 else -1
                } else {
                    y += if (versBas) 1 else -1
                    if (alea.nextFloat() < 0.35f) x += if (alea.nextBoolean()) 1 else -1
                }
                if (x !in 0 until taille || y !in 0 until taille) break
            }
        }
    }

    /** Quelques taches de terre grasse, aux endroits les plus « intérieurs ». */
    private fun semerFertile(
        tuiles: MutableList<IslandTileType>,
        bruit: Bruit,
        taille: Int
    ) {
        for (y in 0 until taille) {
            for (x in 0 until taille) {
                val i = y * taille + x
                if (tuiles[i] != IslandTileType.GRASS) continue
                // Second champ de bruit, décalé : réutiliser le premier ferait
                // coïncider la terre grasse avec le relief, ce qui se voit.
                val n = bruit.octaves(x / 5.5f + 31.7f, y / 5.5f + 17.3f, nombre = 2)
                if (n > 0.62f) tuiles[i] = IslandTileType.FERTILE_GRASS
            }
        }
    }

    /**
     * Bois et rochers.
     *
     * Volontairement clairsemés : une île saturée d'obstacles se lit comme une
     * corvée de nettoyage avant de pouvoir jouer.
     */
    private fun semerObstacles(
        tuiles: MutableList<IslandTileType>,
        bruit: Bruit,
        taille: Int
    ) {
        for (y in 0 until taille) {
            for (x in 0 until taille) {
                val i = y * taille + x
                if (tuiles[i] != IslandTileType.GRASS) continue
                val foret = bruit.octaves(x / 3.2f + 101.5f, y / 3.2f + 57.9f, nombre = 3)
                val roche = bruit.octaves(x / 2.1f + 211.3f, y / 2.1f + 143.1f, nombre = 2)
                when {
                    foret > 0.62f -> tuiles[i] = IslandTileType.FOREST
                    roche > 0.72f -> tuiles[i] = IslandTileType.ROCK
                }
            }
        }
    }

    /**
     * Pose le ponton sur une plage bordée d'eau.
     *
     * Recherche déterministe — première case valide en balayage — pour que la
     * même graine place toujours le ponton au même endroit.
     */
    private fun poserPonton(
        tuiles: MutableList<IslandTileType>,
        taille: Int,
        alea: Random
    ): Case? {
        // Tous les candidats, puis un tirage.
        //
        // Prendre la premiere plage rencontree en balayage semblait suffisant
        // — c'est deterministe — mais le balayage part toujours du haut : le
        // ponton se retrouvait au meme endroit sur toutes les iles, et avec lui
        // la ferme de depart. Le tirage reste reproductible puisqu'il vient de
        // la meme graine.
        val candidats = mutableListOf<Case>()
        for (y in 0 until taille) {
            for (x in 0 until taille) {
                if (tuiles[y * taille + x] != IslandTileType.BEACH) continue
                val bordeEau = voisins(x, y).any { (vx, vy) ->
                    vx in 0 until taille && vy in 0 until taille &&
                        tuiles[vy * taille + vx].let {
                            it == IslandTileType.SHALLOW_WATER || it == IslandTileType.DEEP_WATER
                        }
                }
                if (bordeEau) candidats += Case(x, y)
            }
        }
        if (candidats.isEmpty()) return null
        val choisi = candidats[alea.nextInt(candidats.size)]
        tuiles[choisi.y * taille + choisi.x] = IslandTileType.DOCK
        return choisi
    }

    /**
     * Coin haut-gauche d'un carré 4×4 entièrement cultivable.
     *
     * Le joueur doit pouvoir commencer sans rien dégager. Sans cette zone
     * garantie, une graine peut produire une île où les premières minutes
     * consistent à abattre des arbres — et le validateur la rejette.
     */
    private fun choisirZoneDepart(
        tuiles: List<IslandTileType>,
        taille: Int,
        ponton: Case?
    ): Case? {
        var meilleure: Case? = null
        var meilleureDistance = Int.MAX_VALUE

        for (y in 0..taille - 4) {
            for (x in 0..taille - 4) {
                val libre = (0 until 4).all { dy ->
                    (0 until 4).all { dx -> tuiles[(y + dy) * taille + (x + dx)].cultivable }
                }
                if (!libre) continue
                // La plus proche du ponton : on arrive par la mer, la ferme ne
                // doit pas être à l'autre bout de l'île.
                val d = if (ponton == null) 0 else abs(x - ponton.x) + abs(y - ponton.y)
                if (d < meilleureDistance) {
                    meilleureDistance = d
                    meilleure = Case(x, y)
                }
            }
        }
        return meilleure
    }

    private fun voisins(x: Int, y: Int): List<Pair<Int, Int>> =
        listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)

    // Seuils d'altitude. Réglés pour laisser une plage continue plutôt qu'un
    // liseré interrompu : une côte qui s'ouvre sur l'herbe se lit comme un trou.
    private const val SEUIL_EAU_PROFONDE = 0.30f
    private const val SEUIL_EAU_BASSE = 0.38f
    private const val SEUIL_PLAGE = 0.45f

    /** Nombre de cases cultivables en dessous duquel une île n'est pas jouable. */
    const val CULTIVABLES_MINIMUM = 40

    /**
     * Génère une île jouable, en changeant de graine tant que le validateur refuse.
     *
     * Le nombre de tentatives est borné : une boucle non bornée sur un réglage
     * malheureux gèlerait l'application au premier lancement, c'est-à-dire au
     * pire moment possible. Après [maxTentatives], la dernière île est rendue
     * telle quelle avec son rapport — mieux vaut une île médiocre qu'un écran
     * figé, et le rapport dit ce qui manque.
     */
    fun genererJouable(
        seedInitiale: Long,
        taille: Int = TAILLE_DEFAUT,
        maxTentatives: Int = 24
    ): Pair<Ile, IslandValidator.Rapport> {
        var seed = seedInitiale
        var derniere: Pair<Ile, IslandValidator.Rapport>? = null

        repeat(max(1, maxTentatives)) {
            val ile = generer(seed, taille)
            val rapport = IslandValidator.valider(ile)
            if (rapport.jouable) return ile to rapport
            derniere = ile to rapport
            // Décalage déterministe : la suite de graines essayée ne dépend que
            // de la graine initiale, donc reste reproductible.
            seed = seed * 6364136223846793005L + 1442695040888963407L
        }
        return derniere!!
    }
}
