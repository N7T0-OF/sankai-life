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

    @Query("UPDATE user SET coins=:coins WHERE id=1")
    suspend fun updateCoins(coins: Int)

    @Query("UPDATE user SET gems=:gems WHERE id=1")
    suspend fun updateGems(gems: Int)

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
}

@Dao
interface ArenaRewardDao {
    @Query("SELECT arenaId FROM arena_reward")
    fun getReclamees(): Flow<List<Int>>

    @Query("SELECT arenaId FROM arena_reward")
    suspend fun getReclameesOnce(): List<Int>

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

    @Query("SELECT COUNT(*) FROM chest WHERE isOpened=0")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chest: ChestEntity): Long

    @Query("UPDATE chest SET isOpened=1 WHERE id=:id")
    suspend fun markOpened(id: Long)

    @Query("DELETE FROM chest WHERE isOpened=1 AND createdAt < :before")
    suspend fun cleanOld(before: Long)
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge ORDER BY type ASC, id ASC")
    fun getAllChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenge WHERE type=:type")
    suspend fun getByType(type: String): List<ChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: ChallengeEntity)

    @Query("UPDATE challenge SET currentProgress=:progress WHERE id=:id")
    suspend fun updateProgress(id: String, progress: Int)

    @Query("UPDATE challenge SET isClaimed=1 WHERE id=:id")
    suspend fun markClaimed(id: String)

    @Query("SELECT COUNT(*) FROM challenge WHERE currentProgress >= targetAmount AND isClaimed=0")
    fun countClaimable(): Flow<Int>

    @Query("DELETE FROM challenge WHERE type=:type")
    suspend fun deleteByType(type: String)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM daily_stats WHERE date=:date LIMIT 1")
    suspend fun getToday(date: String): StatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: StatsEntity)

    @Query("UPDATE daily_stats SET xpGained=xpGained+:xp, coinsGained=coinsGained+:coins WHERE date=:date")
    suspend fun addEarnings(date: String, xp: Int, coins: Int)
}
