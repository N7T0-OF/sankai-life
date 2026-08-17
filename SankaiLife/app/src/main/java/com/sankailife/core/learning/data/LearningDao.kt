package com.sankailife.core.learning.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {

    // --- Modules -------------------------------------------------------------

    @Query("SELECT * FROM learning_module ORDER BY ordre ASC, id ASC")
    fun observerModules(): Flow<List<LearningModuleEntity>>

    @Query("SELECT * FROM learning_module ORDER BY ordre ASC, id ASC")
    suspend fun modules(): List<LearningModuleEntity>

    @Query("SELECT * FROM learning_module WHERE id = :id LIMIT 1")
    suspend fun module(id: Long): LearningModuleEntity?

    @Query("SELECT * FROM learning_module WHERE memoProfileId = :profileId LIMIT 1")
    suspend fun moduleDuProfil(profileId: Long): LearningModuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrer(module: LearningModuleEntity): Long

    @Delete
    suspend fun supprimer(module: LearningModuleEntity)

    @Query("SELECT COALESCE(MAX(ordre), -1) + 1 FROM learning_module")
    suspend fun prochainOrdre(): Int

    // --- Erreurs -------------------------------------------------------------

    @Insert
    suspend fun noterErreur(erreur: LearningErrorEntity): Long

    /**
     * Cartes ratées récemment, de la plus fautive à la moins fautive.
     *
     * Bornée dans le temps : une faute d'il y a trois mois sur une carte
     * révisée dix fois depuis ne dit plus rien de ce qu'on sait aujourd'hui.
     */
    @Query("""
        SELECT carteId FROM learning_error
        WHERE momentMillis >= :depuisMillis
        GROUP BY carteId
        ORDER BY COUNT(*) DESC, MAX(momentMillis) DESC
        LIMIT :limite
    """)
    suspend fun cartesEnErreur(depuisMillis: Long, limite: Int = 50): List<Long>

    @Query("""
        SELECT COUNT(*) FROM learning_error
        WHERE carteId = :carteId AND momentMillis >= :depuisMillis
    """)
    suspend fun compterErreurs(carteId: Long, depuisMillis: Long): Int

    /**
     * Efface les fautes anciennes.
     *
     * Sans purge, la table grossit indéfiniment pour des données qui ne servent
     * plus. Appelée à l'ouverture, pas par un service.
     */
    @Query("DELETE FROM learning_error WHERE momentMillis < :avantMillis")
    suspend fun purgerErreurs(avantMillis: Long): Int

    /** Une carte corrigée n'est plus une erreur : ses fautes sont effacées. */
    @Query("DELETE FROM learning_error WHERE carteId = :carteId")
    suspend fun oublierErreurs(carteId: Long): Int

    // --- Sessions ------------------------------------------------------------

    @Insert
    suspend fun ouvrirSession(session: LearningSessionEntity): Long

    @Query("""
        UPDATE learning_session
        SET finMillis = :finMillis, exercicesFaits = :faits,
            exercicesReussis = :reussis, typesJoues = :types
        WHERE id = :id
    """)
    suspend fun cloturerSession(
        id: Long, finMillis: Long, faits: Int, reussis: Int, types: String
    ): Int

    /** Dernières sessions d'un module, pour ne pas rejouer les mêmes exercices. */
    @Query("""
        SELECT * FROM learning_session
        WHERE moduleId = :moduleId AND finMillis > 0
        ORDER BY finMillis DESC LIMIT :limite
    """)
    suspend fun dernieresSessions(moduleId: Long, limite: Int = 3): List<LearningSessionEntity>

    /**
     * Jours distincts où une session a été terminée depuis une date.
     *
     * Compté en jours et non en sessions : trois sessions le même soir ne font
     * pas trois jours de régularité, et prétendre le contraire flatterait
     * l'apprenant en lui mentant.
     */
    @Query("""
        SELECT COUNT(DISTINCT CAST((finMillis + :decalageMillis) / 86400000 AS INTEGER))
        FROM learning_session
        WHERE finMillis >= :depuisMillis AND exercicesFaits > 0
    """)
    fun joursActifs(depuisMillis: Long, decalageMillis: Long): Flow<Int>

    /**
     * Les jours (epoch-day) où une session a été terminée depuis une date.
     *
     * Permet de dessiner la semaine : une pastille par jour, remplie si une
     * session s'y est terminée. La même règle que [joursActifs] — un jour
     * travaillé est un jour avec au moins une session terminée.
     */
    @Query("""
        SELECT DISTINCT CAST((finMillis + :decalageMillis) / 86400000 AS INTEGER)
        FROM learning_session
        WHERE finMillis >= :depuisMillis AND exercicesFaits > 0
    """)
    fun joursActifsListe(depuisMillis: Long, decalageMillis: Long): Flow<List<Long>>

    @Query("DELETE FROM learning_session WHERE moduleId = :moduleId")
    suspend fun effacerSessions(moduleId: Long): Int
}
