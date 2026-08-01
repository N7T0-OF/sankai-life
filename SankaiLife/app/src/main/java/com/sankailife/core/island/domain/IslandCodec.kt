package com.sankailife.core.island.domain

/**
 * Encode et décode le terrain d'une île en une chaîne.
 *
 * Une île de 32 × 32 fait 1 024 cases. Les écrire en 1 024 lignes Room
 * coûterait autant de lectures au démarrage pour des données qui ne changent
 * jamais après la génération. Une chaîne d'un kilo-octet dit la même chose,
 * s'écrit en une transaction et se relit d'un bloc.
 *
 * Pourquoi persister le terrain plutôt que la seule graine, qui tiendrait en
 * huit octets : parce qu'un jour le générateur sera amélioré. Si le terrain
 * était recalculé à chaque démarrage, cette amélioration redessinerait l'île
 * d'un joueur qui y a déjà bâti. Le stocker, c'est garantir qu'aucune mise à
 * jour ne déplace sa ferme.
 */
object IslandCodec {

    /**
     * Un caractère par type de case.
     *
     * Ces lettres sont un **format de stockage** : elles ne doivent jamais
     * changer de sens. Ajouter un type se fait en prenant une lettre libre, pas
     * en réordonnant celles-ci — sinon toutes les îles déjà enregistrées
     * changeraient de relief à la mise à jour suivante.
     */
    private val CODES: Map<IslandTileType, Char> = mapOf(
        IslandTileType.DEEP_WATER to 'W',
        IslandTileType.SHALLOW_WATER to 'w',
        IslandTileType.BEACH to 'b',
        IslandTileType.GRASS to 'g',
        IslandTileType.FERTILE_GRASS to 'f',
        IslandTileType.FOREST to 't',
        IslandTileType.ROCK to 'r',
        IslandTileType.RIVER to 'v',
        IslandTileType.POND to 'p',
        IslandTileType.PATH to 'c',
        IslandTileType.BRIDGE to 'h',
        IslandTileType.DOCK to 'd'
    )

    private val PAR_CODE: Map<Char, IslandTileType> =
        CODES.entries.associate { (type, code) -> code to type }

    init {
        // Un doublon de lettre ferait relire deux types comme un seul, et le
        // terrain se corromprait en silence à la première sauvegarde.
        require(PAR_CODE.size == CODES.size) { "Deux types partagent le même code." }
        require(CODES.size == IslandTileType.entries.size) {
            "Un type de case n'a pas de code de stockage."
        }
    }

    fun encoder(tuiles: List<IslandTileType>): String {
        val sb = StringBuilder(tuiles.size)
        tuiles.forEach { sb.append(CODES.getValue(it)) }
        return sb.toString()
    }

    /**
     * Relit un terrain.
     *
     * Un caractère inconnu devient de l'eau profonde plutôt que de faire échouer
     * la lecture : une sauvegarde écrite par une version plus récente doit
     * s'ouvrir en version ancienne, quitte à montrer de la mer là où il y avait
     * une nouveauté. Refuser de charger reviendrait à effacer la partie.
     */
    fun decoder(donnees: String): List<IslandTileType> =
        donnees.map { PAR_CODE[it] ?: IslandTileType.DEEP_WATER }

    /**
     * Vérifie qu'une chaîne correspond bien aux dimensions annoncées.
     *
     * Sans ce contrôle, une donnée tronquée se lit comme une île plus petite et
     * décale tout le terrain d'une ligne à l'autre.
     */
    fun tailleCoherente(donnees: String, largeur: Int, hauteur: Int): Boolean =
        largeur > 0 && hauteur > 0 && donnees.length == largeur * hauteur

    /** Somme de contrôle simple, pour détecter une corruption au chargement. */
    fun empreinte(donnees: String): Int {
        var h = 7
        for (c in donnees) h = h * 31 + c.code
        return h
    }
}
