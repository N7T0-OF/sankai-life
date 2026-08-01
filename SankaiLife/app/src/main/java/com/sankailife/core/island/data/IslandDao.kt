package com.sankailife.core.island.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IslandDao {

    @Query("SELECT * FROM island WHERE id = 1 LIMIT 1")
    fun observerIle(): Flow<IslandEntity?>

    @Query("SELECT * FROM island WHERE id = 1 LIMIT 1")
    suspend fun ile(): IslandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerIle(ile: IslandEntity)

    /**
     * Crée l'île seulement si aucune n'existe.
     *
     * `IGNORE` fait office de verrou : deux appels concurrents au premier
     * lancement ne peuvent pas produire deux îles, ni écraser celle qui vient
     * d'être générée. La même prudence qu'à la création du profil joueur.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun creerSiAbsente(ile: IslandEntity): Long

    @Query("SELECT * FROM island_slot ORDER BY cle ASC")
    fun observerParcelles(): Flow<List<IslandSlotEntity>>

    @Query("SELECT * FROM island_slot ORDER BY cle ASC")
    suspend fun parcelles(): List<IslandSlotEntity>

    @Query("SELECT COUNT(*) FROM island_slot")
    fun compterParcelles(): Flow<Int>

    @Query("SELECT COUNT(*) FROM island_slot")
    suspend fun nombreParcelles(): Int

    /**
     * Achète une parcelle, une seule fois.
     *
     * `IGNORE` sur une clé déjà présente : un double appui ne peut pas faire
     * payer deux fois la même case. Le nombre de lignes renvoyé dit ce qui
     * s'est réellement passé — 0 signifie « déjà achetée », et l'appelant doit
     * alors rembourser plutôt que de faire comme si.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun acheterParcelle(parcelle: IslandSlotEntity): Long

    @Delete
    suspend fun supprimerParcelle(parcelle: IslandSlotEntity)

    @Query("SELECT * FROM island_slot WHERE cle = :cle LIMIT 1")
    suspend fun parcelle(cle: Int): IslandSlotEntity?

    @androidx.room.Update
    suspend fun majParcelle(parcelle: IslandSlotEntity)

    /**
     * Sème, une seule fois.
     *
     * La condition d'état vit dans le `WHERE` : c'est elle qui fait office de
     * verrou. Lire puis écrire laisserait deux appuis rapprochés semer deux
     * graines et n'en facturer qu'une. Le nombre de lignes modifiées dit ce
     * qui s'est réellement passé.
     */
    @Query(
        "UPDATE island_slot SET etat = 'PLANTED', graineId = :graineId, " +
            "planteeMillis = :maintenant, minutesCumulees = 0, " +
            "dernierArrosageMillis = :maintenant, arrosages = 1, " +
            "dernierCalculMillis = :maintenant " +
            "WHERE cle = :cle AND etat = 'PREPARED'"
    )
    suspend fun semerSiPreparee(cle: Int, graineId: String, maintenant: Long): Int

    /** Prépare la terre, uniquement si la parcelle est vide et dégagée. */
    @Query(
        "UPDATE island_slot SET etat = 'PREPARED' " +
            "WHERE cle = :cle AND etat = 'EMPTY' AND aDegager = 0"
    )
    suspend fun preparerSiVide(cle: Int): Int

    /** Dégage bois ou rocher, une seule fois. */
    @Query("UPDATE island_slot SET aDegager = 0 WHERE cle = :cle AND aDegager = 1")
    suspend fun degagerSiBesoin(cle: Int): Int

    /** Arrose une culture en cours ; sans effet sur une parcelle libre. */
    @Query(
        "UPDATE island_slot SET dernierArrosageMillis = :maintenant, " +
            "arrosages = arrosages + 1 " +
            "WHERE cle = :cle AND graineId != ''"
    )
    suspend fun arroserSiCulture(cle: Int, maintenant: Long): Int

    /** Vide la parcelle après récolte, uniquement si la culture était prête. */
    @Query(
        "UPDATE island_slot SET etat = 'EMPTY', graineId = '', planteeMillis = 0, " +
            "minutesCumulees = 0, dernierArrosageMillis = 0, arrosages = 0, " +
            "dernierCalculMillis = 0 " +
            "WHERE cle = :cle AND etat = 'READY_TO_HARVEST'"
    )
    suspend fun recolterSiPrete(cle: Int): Int

    @Query("SELECT * FROM island_building")
    fun observerBatiments(): Flow<List<IslandBuildingEntity>>

    @Query("SELECT * FROM island_building")
    suspend fun batiments(): List<IslandBuildingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun poserBatiment(batiment: IslandBuildingEntity)

    @Query("DELETE FROM island_building WHERE type = :type")
    suspend fun retirerBatiment(type: String)

    // --- Remise à zéro -----------------------------------------------------
    //
    // Utilisées ensemble, et uniquement pour régénérer une île à la demande
    // explicite du joueur. Jamais appelées par une migration.

    // --- Stock -------------------------------------------------------------

    @Query("SELECT * FROM island_stock WHERE quantite > 0 ORDER BY graineId ASC")
    fun observerStock(): Flow<List<IslandStockEntity>>

    @Query("SELECT IFNULL(SUM(quantite), 0) FROM island_stock")
    suspend fun totalStock(): Int

    @Query("SELECT * FROM island_stock WHERE graineId = :graineId LIMIT 1")
    suspend fun stock(graineId: String): IslandStockEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun creerStockSiAbsent(stock: IslandStockEntity): Long

    @Query("UPDATE island_stock SET quantite = quantite + :ajout WHERE graineId = :graineId")
    suspend fun ajouterAuStock(graineId: String, ajout: Int)

    /**
     * Retire du stock, jamais plus qu'il n'y en a.
     *
     * La condition vit dans le `WHERE` : deux ventes concurrentes ne peuvent
     * pas vider deux fois le même lot et créditer deux fois.
     */
    @Query(
        "UPDATE island_stock SET quantite = quantite - :retrait " +
            "WHERE graineId = :graineId AND quantite >= :retrait AND :retrait > 0"
    )
    suspend fun retirerDuStock(graineId: String, retrait: Int): Int

    @Query("DELETE FROM island_stock")
    suspend fun effacerStock()

    @Query("DELETE FROM island_slot")
    suspend fun effacerParcelles()

    @Query("DELETE FROM island_building")
    suspend fun effacerBatiments()

    @Query("DELETE FROM island")
    suspend fun effacerIle()
}
