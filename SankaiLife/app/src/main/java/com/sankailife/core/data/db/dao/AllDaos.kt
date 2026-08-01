package com.sankailife.core.data.db.dao

import androidx.room.*
import com.sankailife.core.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user WHERE id=1 LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM user WHERE id=1 LIMIT 1")
    suspend fun getUserOnce(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    /** Crée le profil initial sans jamais remplacer un profil apparu entre-temps. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(user: UserEntity): Long

    @Query("UPDATE user SET coins=:coins WHERE id=1")
    suspend fun updateCoins(coins: Int)

    /** Crédit atomique : deux gains concurrents s'additionnent au lieu de s'écraser. */
    @Query(
        "UPDATE user SET coins = coins + :amount, " +
            "totalCoinsEarned = totalCoinsEarned + :amount WHERE id=1"
    )
    suspend fun creditCoins(amount: Int): Int

    /** Remboursement : restaure le solde sans le compter comme un nouveau gain. */
    @Query("UPDATE user SET coins = coins + :amount WHERE id=1")
    suspend fun refundCoins(amount: Int): Int

    /** Débit et statistique dans une seule écriture, uniquement si le solde suffit. */
    @Query(
        "UPDATE user SET coins = coins - :amount, " +
            "totalCoinsSpent = totalCoinsSpent + :amount " +
            "WHERE id=1 AND :amount >= 0 AND coins >= :amount"
    )
    suspend fun spendCoinsIfEnough(amount: Int): Int

    @Query("UPDATE user SET gems=:gems WHERE id=1")
    suspend fun updateGems(gems: Int)

    @Query("UPDATE user SET gems = gems + :amount WHERE id=1")
    suspend fun creditGems(amount: Int): Int

    @Query("UPDATE user SET gems = gems - :amount WHERE id=1 AND :amount >= 0 AND gems >= :amount")
    suspend fun spendGemsIfEnough(amount: Int): Int

    @Query("UPDATE user SET xp=:xp, xpNext=:xpNext, level=:level WHERE id=1")
    suspend fun updateXp(xp: Int, xpNext: Int, level: Int)

    @Query("UPDATE user SET streakDays=:streak, lastLoginDate=:date WHERE id=1")
    suspend fun updateStreak(streak: Int, date: String)

    @Query("UPDATE user SET adCountToday=:count, lastAdDate=:date WHERE id=1")
    suspend fun updateAdCount(count: Int, date: String)

    @Query("UPDATE user SET totalFocusMinutes=totalFocusMinutes+:min WHERE id=1")
    suspend fun addFocusMinutes(min: Int)

    @Query("UPDATE user SET totalAdsWatched=totalAdsWatched+1 WHERE id=1")
    suspend fun incrementAds()

    @Query("UPDATE user SET totalChestsOpened=totalChestsOpened+1 WHERE id=1")
    suspend fun incrementChests()

    @Query("UPDATE user SET totalCoinsEarned=totalCoinsEarned+:amount WHERE id=1")
    suspend fun addCoinsEarned(amount: Int)

    @Query("UPDATE user SET totalCoinsSpent=totalCoinsSpent+:amount WHERE id=1")
    suspend fun addCoinsSpent(amount: Int)

    @Query("UPDATE user SET equippedThemeId=:themeId WHERE id=1")
    suspend fun updateTheme(themeId: String)

    @Query("UPDATE user SET moduleSlots=:slots WHERE id=1")
    suspend fun updateModuleSlots(slots: Int)

    @Query("UPDATE user SET moduleSlots = moduleSlots + :amount WHERE id=1")
    suspend fun addModuleSlots(amount: Int): Int

    @Query("UPDATE user SET bestStreak=:best WHERE id=1")
    suspend fun updateBestStreak(best: Int)

    @Query("UPDATE user SET streakShields=:shields WHERE id=1")
    suspend fun updateShields(shields: Int)

    /**
     * Réserve le coffre quotidien pour [jour].
     *
     * La clause `lastDailyChestDay != :jour` fait office de verrou atomique :
     * SQLite ne modifie la ligne qu'une seule fois, même si deux appels
     * arrivent simultanément. Un second appel renvoie 0 et n'accorde rien.
     *
     * @return 1 si le coffre vient d'être réservé, 0 s'il l'était déjà.
     */
    @Query("UPDATE user SET lastDailyChestDay=:jour WHERE id=1 AND lastDailyChestDay != :jour")
    suspend fun reserverCoffreQuotidien(jour: String): Int

    /** Libère uniquement la réservation que l'appelant avait lui-même prise. */
    @Query("UPDATE user SET lastDailyChestDay='' WHERE id=1 AND lastDailyChestDay=:jour")
    suspend fun libererCoffreQuotidien(jour: String): Int
}

@Dao
interface MemoDao {
    @Query("SELECT * FROM memo_profile ORDER BY id ASC")
    fun getAllProfiles(): Flow<List<MemoProfileEntity>>

    @Query("SELECT * FROM memo_profile WHERE id=:id LIMIT 1")
    suspend fun getProfile(id: Long): MemoProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: MemoProfileEntity): Long

    @Delete
    suspend fun deleteProfile(profile: MemoProfileEntity)

    @Query("DELETE FROM memo_line WHERE profileId=:profileId")
    suspend fun deleteAllLines(profileId: Long)

    @Query("SELECT * FROM memo_line WHERE profileId=:profileId ORDER BY orderIndex ASC")
    fun getLines(profileId: Long): Flow<List<MemoLineEntity>>

    @Query("SELECT * FROM memo_line WHERE profileId=:profileId ORDER BY orderIndex ASC")
    suspend fun getLinesOnce(profileId: Long): List<MemoLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: MemoLineEntity): Long

    @Delete
    suspend fun deleteLine(line: MemoLineEntity)

    @Query("SELECT COUNT(*) FROM memo_line WHERE profileId=:profileId")
    fun getLineCount(profileId: Long): Flow<Int>

    // --- Flash cards -----------------------------------------------------

    /** Cartes dues à la révision, les plus en retard d'abord. */
    @Query("""
        SELECT * FROM memo_line
        WHERE profileId = :profileId AND nextReviewAtMillis <= :maintenant
        ORDER BY nextReviewAtMillis ASC, box ASC
        LIMIT :limite
    """)
    suspend fun getCartesDues(profileId: Long, maintenant: Long, limite: Int): List<MemoLineEntity>

    @Query("""
        SELECT COUNT(*) FROM memo_line
        WHERE profileId = :profileId AND nextReviewAtMillis <= :maintenant
    """)
    fun compterCartesDues(profileId: Long, maintenant: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM memo_line
        WHERE nextReviewAtMillis <= :maintenant
    """)
    fun compterToutesCartesDues(maintenant: Long): Flow<Int>

    @Query("""
        UPDATE memo_line
        SET box = :box,
            nextReviewAtMillis = :prochaine,
            reviewCount = reviewCount + 1,
            successCount = successCount + :reussite
        WHERE id = :id
    """)
    suspend fun majEtatCarte(id: Long, box: Int, prochaine: Long, reussite: Int)

    /** Remet un module entier à zéro côté révision. */
    @Query("UPDATE memo_line SET box=0, nextReviewAtMillis=0, reviewCount=0, successCount=0 WHERE profileId=:profileId")
    suspend fun reinitialiserRevisions(profileId: Long)

    @Query("UPDATE memo_profile SET isActive=:active WHERE id=:id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE memo_profile SET sentLineHistory=:history WHERE id=:id")
    suspend fun updateHistory(id: Long, history: String)

    @Query("SELECT * FROM memo_profile WHERE isActive=1")
    suspend fun getActiveProfilesOnce(): List<MemoProfileEntity>

    @Query("UPDATE memo_profile SET lastNotifiedAtMillis=:millis WHERE id=:id")
    suspend fun updateLastNotified(id: Long, millis: Long)

    @Query("UPDATE memo_profile SET nextTriggerAtMillis=:millis WHERE id=:id")
    suspend fun updateNextTrigger(id: Long, millis: Long)

    @Query("SELECT * FROM memo_profile")
    suspend fun getAllProfilesOnce(): List<MemoProfileEntity>
}

@Dao
interface ObjectiveDao {
    @Query("SELECT * FROM objective ORDER BY isDone ASC, createdAt DESC")
    fun getAll(): Flow<List<ObjectiveEntity>>

    @Query("SELECT * FROM objective ORDER BY id ASC")
    suspend fun getAllOnce(): List<ObjectiveEntity>

    @Query("SELECT COUNT(*) FROM objective WHERE isDone=0")
    fun countPending(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(objective: ObjectiveEntity): Long

    @Delete
    suspend fun delete(objective: ObjectiveEntity)

    @Query("UPDATE objective SET isDone=:done, completedAt=:completedAt WHERE id=:id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long)

    @Query("DELETE FROM objective WHERE isDone=1")
    suspend fun clearCompleted()

    @Query("DELETE FROM objective")
    suspend fun clearAll()
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_record WHERE date >= :depuis ORDER BY date DESC")
    fun getDepuis(depuis: String): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_record WHERE date >= :depuis ORDER BY date DESC")
    suspend fun getDepuisOnce(depuis: String): List<DayRecordEntity>

    @Query("SELECT * FROM day_record WHERE date = :date LIMIT 1")
    suspend fun getJour(date: String): DayRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DayRecordEntity)

    @Query("UPDATE day_record SET note = :note WHERE date = :date")
    suspend fun setNote(date: String, note: String)

    @Query("DELETE FROM day_record")
    suspend fun clearAll()
}

@Dao
interface ArenaRewardDao {
    @Query("SELECT arenaId FROM arena_reward")
    fun getReclamees(): Flow<List<Int>>

    @Query("SELECT arenaId FROM arena_reward")
    suspend fun getReclameesOnce(): List<Int>

    @Query("SELECT * FROM arena_reward ORDER BY arenaId ASC")
    suspend fun getAllRewardsOnce(): List<ArenaRewardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun marquerReclamee(reward: ArenaRewardEntity): Long

    @Query("DELETE FROM arena_reward")
    suspend fun toutEffacer()
}

@Dao
interface ChestDao {
    @Query("SELECT * FROM chest WHERE isOpened=0 ORDER BY slotIndex ASC")
    fun getActiveChests(): Flow<List<ChestEntity>>

    @Query("SELECT * FROM chest WHERE isOpened=0 ORDER BY slotIndex ASC")
    suspend fun getActiveChestsOnce(): List<ChestEntity>

    @Query("SELECT * FROM chest ORDER BY id ASC")
    suspend fun getAllOnce(): List<ChestEntity>

    @Query("SELECT COUNT(*) FROM chest WHERE isOpened=0")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chest: ChestEntity): Long

    /**
     * Marque un coffre comme ouvert.
     *
     * La clause `isOpened=0` sert de verrou : deux appuis rapprochés ne
     * peuvent pas créditer la récompense deux fois, puisque la seconde mise à
     * jour ne touche aucune ligne.
     *
     * @return 1 si le coffre vient d'être ouvert, 0 s'il l'était déjà.
     */
    @Query("UPDATE chest SET isOpened=1 WHERE id=:id AND isOpened=0")
    suspend fun markOpened(id: Long): Int

    @Query("DELETE FROM chest WHERE isOpened=1 AND createdAt < :before")
    suspend fun cleanOld(before: Long)

    @Query("DELETE FROM chest")
    suspend fun clearAll()
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge ORDER BY type ASC, id ASC")
    fun getAllChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenge WHERE type=:type")
    suspend fun getByType(type: String): List<ChallengeEntity>

    @Query("SELECT * FROM challenge ORDER BY type ASC, id ASC")
    suspend fun getAllOnce(): List<ChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: ChallengeEntity)

    @Query(
        "UPDATE challenge SET currentProgress = " +
            "MIN(targetAmount, currentProgress + :amount) WHERE id=:id AND :amount > 0"
    )
    suspend fun incrementProgress(id: String, amount: Int): Int

    /** Verrou atomique anti-double récompense. */
    @Query(
        "UPDATE challenge SET isClaimed=1 " +
            "WHERE id=:id AND isClaimed=0 AND currentProgress >= targetAmount"
    )
    suspend fun markClaimed(id: String): Int

    @Query("SELECT COUNT(*) FROM challenge WHERE currentProgress >= targetAmount AND isClaimed=0")
    fun countClaimable(): Flow<Int>

    @Query("DELETE FROM challenge WHERE type=:type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM challenge")
    suspend fun clearAll()
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM daily_stats WHERE date=:date LIMIT 1")
    suspend fun getToday(date: String): StatsEntity?

    @Query("SELECT * FROM daily_stats ORDER BY date ASC")
    suspend fun getAllOnce(): List<StatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: StatsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: StatsEntity): Long

    @Query("UPDATE daily_stats SET xpGained=xpGained+:xp, coinsGained=coinsGained+:coins WHERE date=:date")
    suspend fun addEarnings(date: String, xp: Int, coins: Int)

    @Query("UPDATE daily_stats SET coinsSpent=coinsSpent+:coins WHERE date=:date")
    suspend fun addSpending(date: String, coins: Int)

    @Query("DELETE FROM daily_stats")
    suspend fun clearAll()
}
