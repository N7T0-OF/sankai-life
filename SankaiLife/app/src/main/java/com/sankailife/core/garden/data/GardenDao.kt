package com.sankailife.core.garden.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GardenDao {

    // --- État global ------------------------------------------------------

    /**
     * Retire de l'eau, uniquement s'il y en a assez.
     *
     * La condition vit dans le `WHERE` : c'est ce qui empeche deux arrosages
     * concurrents de puiser deux fois dans la meme goutte. Renvoie 0 quand la
     * reserve est insuffisante, sans rien modifier.
     */
    @Query(
        "UPDATE garden_state SET eau = eau - :quantite " +
            "WHERE id = 1 AND :quantite > 0 AND eau >= :quantite"
    )
    suspend fun depenserEauSiAssez(quantite: Int): Int

    /** Rend de l'eau : sert au remboursement d'un arrosage qui n'a pas eu lieu. */
    @Query("UPDATE garden_state SET eau = eau + :quantite WHERE id = 1 AND :quantite > 0")
    suspend fun crediterEau(quantite: Int): Int

    /** Le compost est credite sans relire puis remplacer tout l'etat global. */
    @Query("UPDATE garden_state SET compost = compost + :quantite WHERE id = 1 AND :quantite > 0")
    suspend fun crediterCompost(quantite: Int): Int

    /** Debit conditionnel partage par tous les Mimos d'une meme ouverture. */
    @Query(
        "UPDATE garden_state SET compost = compost - :quantite " +
            "WHERE id = 1 AND :quantite > 0 AND compost >= :quantite"
    )
    suspend fun depenserCompostSiAssez(quantite: Int): Int

    @Query("SELECT IFNULL((SELECT eau FROM garden_state WHERE id = 1), 0)")
    fun observerEau(): Flow<Int>

    @Query("SELECT * FROM garden_state WHERE id = 1 LIMIT 1")
    fun observerEtat(): Flow<GardenStateEntity?>

    @Query("SELECT * FROM garden_state WHERE id = 1 LIMIT 1")
    suspend fun etat(): GardenStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sauverEtat(state: GardenStateEntity)

    @Query("DELETE FROM garden_state")
    suspend fun effacerEtat()

    // --- Parcelles --------------------------------------------------------

    @Query("SELECT * FROM garden_plot ORDER BY id ASC")
    fun observerParcelles(): Flow<List<GardenPlotEntity>>

    @Query("SELECT * FROM garden_plot ORDER BY id ASC")
    suspend fun parcelles(): List<GardenPlotEntity>

    @Query("SELECT * FROM garden_plot WHERE id = :id LIMIT 1")
    suspend fun parcelle(id: Int): GardenPlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sauverParcelles(parcelles: List<GardenPlotEntity>)

    @Query("DELETE FROM garden_plot")
    suspend fun effacerParcelles()

    @Query("UPDATE garden_plot SET etat = :etat WHERE id = :id")
    suspend fun majEtatParcelle(id: Int, etat: String)

    /** Reserve une parcelle vide pour une plantation, une seule fois. */
    @Query(
        "UPDATE garden_plot SET etat = 'GROWING' " +
            "WHERE id = :id AND etat = 'EMPTY' AND deblocage = 'DEBLOQUEE' " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM garden_crop WHERE plotId = :id AND recoltee = 0)"
    )
    suspend fun commencerPlantationSiVide(id: Int): Int

    /** Annule uniquement une reservation qui n'a encore produit aucune culture. */
    @Query(
        "UPDATE garden_plot SET etat = 'EMPTY' " +
            "WHERE id = :id AND etat = 'GROWING' AND NOT EXISTS (" +
            "SELECT 1 FROM garden_crop WHERE plotId = :id AND recoltee = 0)"
    )
    suspend fun annulerPlantationSansCulture(id: Int): Int

    /** Lance le chantier uniquement si la parcelle est encore disponible. */
    @Query(
        "UPDATE garden_plot SET deblocage = 'EN_CHANTIER', chantierFinMillis = :fin " +
            "WHERE id = :id AND deblocage = 'DECOUVERTE'"
    )
    suspend fun lancerChantierSiDecouverte(id: Int, fin: Long): Int

    /** Rend une reservation de chantier qui n'a finalement pas pu etre payee. */
    @Query(
        "UPDATE garden_plot SET deblocage = 'DECOUVERTE', chantierFinMillis = 0 " +
            "WHERE id = :id AND deblocage = 'EN_CHANTIER' AND chantierFinMillis = :fin"
    )
    suspend fun annulerChantierSiIdentique(id: Int, fin: Long): Int

    /** Termine un chantier arrive a echeance, sans ecraser un etat plus recent. */
    @Query(
        "UPDATE garden_plot SET deblocage = 'DEBLOQUEE', chantierFinMillis = 0 " +
            "WHERE id = :id AND deblocage = 'EN_CHANTIER' " +
            "AND chantierFinMillis > 0 AND chantierFinMillis <= :maintenant"
    )
    suspend fun acheverChantierSiArrive(id: Int, maintenant: Long): Int

    @Query("UPDATE garden_plot SET solId = :solId WHERE id = :id")
    suspend fun majSol(id: Int, solId: String)

    @Query("UPDATE garden_plot SET humidite = :humidite, dernierCalculHumidite = :quand WHERE id = :id")
    suspend fun majHumidite(id: Int, humidite: Float, quand: Long)

    /**
     * Cette parcelle a-t-elle déjà porté une culture ?
     *
     * Sert à distinguer le défrichage — un terrain vierge plein de rochers,
     * qui se paie — du labour d'après récolte, qui est gratuit.
     */
    @Query("SELECT COUNT(*) FROM garden_crop WHERE plotId = :plotId")
    suspend fun aDejaEteCultivee(plotId: Int): Int

    // --- Cultures ---------------------------------------------------------

    @Query("SELECT * FROM garden_crop WHERE recoltee = 0")
    fun observerCultures(): Flow<List<GardenCropEntity>>

    @Query("SELECT * FROM garden_crop WHERE recoltee = 0")
    suspend fun culturesEnCours(): List<GardenCropEntity>

    @Query("SELECT * FROM garden_crop ORDER BY id ASC")
    suspend fun toutesCultures(): List<GardenCropEntity>

    @Query("SELECT * FROM garden_crop WHERE plotId = :plotId AND recoltee = 0 LIMIT 1")
    suspend fun cultureSurParcelle(plotId: Int): GardenCropEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererCulture(crop: GardenCropEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sauverCultures(cultures: List<GardenCropEntity>)

    @Query("DELETE FROM garden_crop")
    suspend fun effacerCultures()

    @Update
    suspend fun majCulture(crop: GardenCropEntity)

    @Query("UPDATE garden_crop SET recoltee = 1 WHERE id = :id AND recoltee = 0")
    suspend fun marquerRecolteeSiActive(id: Long): Int

    /** Compte l'arrosage sans recopier une ancienne version de la culture. */
    @Query(
        "UPDATE garden_crop SET dernierArrosageMillis = :maintenant, " +
            "arrosages = arrosages + 1 WHERE plotId = :plotId AND recoltee = 0"
    )
    suspend fun enregistrerArrosage(plotId: Int, maintenant: Long): Int

    /** Rend la terre a labourer seulement si la culture occupait encore la case. */
    @Query(
        "UPDATE garden_plot SET etat = 'UNCLEARED' " +
            "WHERE id = :id AND etat IN ('GROWING', 'READY_TO_HARVEST')"
    )
    suspend fun terminerRecolteSiEnCroissance(id: Int): Int

    /** Compte les récoltes, pour l'Herbier et les statistiques. */
    @Query("SELECT COUNT(*) FROM garden_crop WHERE recoltee = 1")
    fun compterRecoltes(): Flow<Int>

    /** Nettoie les cultures récoltées anciennes pour borner la table. */
    @Query("DELETE FROM garden_crop WHERE recoltee = 1 AND plantedAtMillis < :avant")
    suspend fun purger(avant: Long)

    // --- Caisses et dépôt --------------------------------------------------

    @Query("SELECT * FROM garden_crate ORDER BY creeALeMillis ASC")
    fun observerCaisses(): Flow<List<GardenCrateEntity>>

    @Query("SELECT * FROM garden_crate ORDER BY creeALeMillis ASC")
    suspend fun caisses(): List<GardenCrateEntity>

    @Query("SELECT COUNT(*) FROM garden_crate")
    suspend fun nombreCaisses(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun poserCaisse(caisse: GardenCrateEntity): Long

    @Query("DELETE FROM garden_crate")
    suspend fun effacerCaisses()

    @Query("DELETE FROM garden_crate WHERE id IN (:ids)")
    suspend fun retirerCaisses(ids: List<Long>)

    @Query("SELECT * FROM garden_inventory WHERE quantite > 0 ORDER BY seedId ASC")
    fun observerInventaire(): Flow<List<GardenInventoryEntity>>

    @Query("SELECT * FROM garden_inventory WHERE cle = :cle LIMIT 1")
    suspend fun ligneInventaire(cle: String): GardenInventoryEntity?

    @Query("SELECT * FROM garden_inventory ORDER BY cle ASC")
    suspend fun inventaire(): List<GardenInventoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sauverInventaire(ligne: GardenInventoryEntity)

    @Query("DELETE FROM garden_inventory")
    suspend fun effacerInventaire()

    /**
     * Retire du stock uniquement si la quantité demandée est disponible.
     *
     * La condition vit dans le WHERE : c'est ce qui rend la vente atomique.
     * Deux appuis simultanés sur « Vendre » ne peuvent pas vendre deux fois le
     * même légume, le second ne touchant aucune ligne.
     */
    @Query("UPDATE garden_inventory SET quantite = quantite - :n WHERE cle = :cle AND quantite >= :n")
    suspend fun retirerDuStock(cle: String, n: Int): Int

    // --- Mimos -------------------------------------------------------------

    @Query("SELECT * FROM garden_mimo ORDER BY embaucheMillis ASC")
    fun observerMimos(): Flow<List<GardenMimoEntity>>

    @Query("SELECT * FROM garden_mimo ORDER BY embaucheMillis ASC")
    suspend fun mimos(): List<GardenMimoEntity>

    @Query("SELECT COUNT(*) FROM garden_mimo WHERE type = :type")
    suspend fun compterMimosDeType(type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun embaucher(mimo: GardenMimoEntity): Long

    @Query("DELETE FROM garden_mimo")
    suspend fun effacerMimos()

    // --- Défi souvenir ----------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerNotification(defi: MemoChallengeEntity): Long

    @Query("SELECT * FROM memo_challenge ORDER BY id ASC")
    suspend fun defisSouvenir(): List<MemoChallengeEntity>

    @Query("DELETE FROM memo_challenge")
    suspend fun effacerDefisSouvenir()

    /** Dernière notification non encore transformée en défi réclamé. */
    @Query("""
        SELECT * FROM memo_challenge
        WHERE reclame = 0 AND envoyeALeMillis >= :depuis
        ORDER BY envoyeALeMillis DESC LIMIT 1
    """)
    suspend fun dernierDefiDisponible(depuis: Long): MemoChallengeEntity?

    @Query("""
        SELECT * FROM memo_challenge
        WHERE reclame = 0 AND envoyeALeMillis >= :depuis
        ORDER BY envoyeALeMillis DESC LIMIT 1
    """)
    fun observerDefiDisponible(depuis: Long): Flow<MemoChallengeEntity?>

    /**
     * Marque un défi comme réclamé.
     * La clause `reclame = 0` fait office de verrou : un second appel ne
     * modifie aucune ligne et renvoie 0, ce qui empêche le double crédit.
     */
    @Query("UPDATE memo_challenge SET reclame = 1 WHERE id = :id AND reclame = 0")
    suspend fun marquerDefiReclame(id: Long): Int

    @Query("DELETE FROM memo_challenge WHERE envoyeALeMillis < :avant")
    suspend fun purgerDefis(avant: Long)
}
