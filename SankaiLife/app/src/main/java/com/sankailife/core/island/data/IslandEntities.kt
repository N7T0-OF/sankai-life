package com.sankailife.core.island.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * L'île d'un profil.
 *
 * Une seule ligne, clé fixée à 1, comme la table `user`. Un profil a une île et
 * une seule ; laisser la clé auto-incrémentée permettrait d'en accumuler
 * silencieusement plusieurs après un import raté.
 */
@Entity(tableName = "island")
data class IslandEntity(
    @PrimaryKey val id: Int = 1,

    /** Graine retenue. Permet de partager la disposition sans la progression. */
    val seed: Long = 0L,

    val largeur: Int = 0,
    val hauteur: Int = 0,

    /**
     * Terrain encodé, un caractère par case (voir `IslandCodec`).
     *
     * Stocké et non recalculé : le jour où le générateur sera amélioré, une île
     * déjà bâtie ne doit pas être redessinée sous les pieds de son joueur.
     */
    val tuiles: String = "",

    /** Somme de contrôle de [tuiles], vérifiée au chargement. */
    val empreinte: Int = 0,

    /**
     * Version du générateur qui a produit ce terrain.
     *
     * Conservée pour savoir *comment* l'île a été faite, jamais pour la
     * refaire.
     */
    val generationVersion: Int = 0,

    /** Version du format de stockage, pour les migrations futures. */
    val schemaVersion: Int = 1,

    val pontonX: Int = -1,
    val pontonY: Int = -1,
    val departX: Int = -1,
    val departY: Int = -1,

    /** Nom donné par le joueur. Vide tant qu'il n'a pas choisi. */
    val nom: String = "",

    val creeMillis: Long = 0L
)

/**
 * Une parcelle achetée par le joueur.
 *
 * Seules les cases **achetées** sont enregistrées. Les mille autres n'existent
 * que dans le terrain encodé : écrire une ligne par case de l'île ferait mille
 * écritures pour n'en décrire qu'une poignée d'utiles.
 */
@Entity(tableName = "island_slot")
data class IslandSlotEntity(
    /** Clé de grille `y * largeur + x`. */
    @PrimaryKey val cle: Int = 0,

    val x: Int = 0,
    val y: Int = 0,

    /** Prix réellement payé, conservé pour un éventuel remboursement. */
    val prixPaye: Int = 0,

    /** Identifiant de sol, repris du système de jardin existant. */
    val solId: String = "terre",

    /** Vrai tant que la forêt ou le rocher n'a pas été dégagé. */
    val aDegager: Boolean = false,

    val acheteeMillis: Long = 0L
)

/**
 * Un bâtiment posé.
 *
 * L'emprise n'est pas stockée : elle se déduit du type, via le système
 * d'emprise déjà écrit. La dupliquer en base la laisserait diverger du code le
 * jour où un bâtiment change de taille.
 */
@Entity(tableName = "island_building")
data class IslandBuildingEntity(
    @PrimaryKey val type: String = "",

    val origineX: Int = 0,
    val origineY: Int = 0,

    /** 0, 90, 180 ou 270 degrés. */
    val orientation: Int = 0,

    val niveau: Int = 1,

    /** Fin du chantier, 0 si le bâtiment est terminé. */
    val chantierFinMillis: Long = 0L
)
