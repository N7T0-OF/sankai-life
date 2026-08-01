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

    @Query("DELETE FROM island_slot")
    suspend fun effacerParcelles()

    @Query("DELETE FROM island_building")
    suspend fun effacerBatiments()

    @Query("DELETE FROM island")
    suspend fun effacerIle()
}
