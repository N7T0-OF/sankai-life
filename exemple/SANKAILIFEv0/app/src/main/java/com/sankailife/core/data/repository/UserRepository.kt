package com.sankailife.core.data.repository

import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.domain.engine.XpEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class UserRepository(private val db: SankaiDatabase) {
    private val dao = db.userDao()

    val userFlow: Flow<UserState> = dao.getUser().map { e ->
        e?.toState() ?: UserState()
    }

    suspend fun ensureUser() {
        if (dao.getUserOnce() == null) dao.upsert(UserEntity())
    }

    suspend fun addXp(amount: Int): Boolean {
        val u = dao.getUserOnce() ?: return false
        val newXp = u.xp + amount
        val (newLevel, remainXp) = XpEngine.checkLevelUp(newXp, u.level)
        val didLevelUp = newLevel > u.level
        dao.updateXp(remainXp, XpEngine.xpForLevel(newLevel + 1), newLevel)
        if (didLevelUp) {
            val bonus = XpEngine.levelUpRewardCoins(newLevel)
            addCoins(bonus)
        }
        db.statsDao().addEarnings(LocalDate.now().toString(), amount, 0)
        return didLevelUp
    }

    suspend fun addCoins(amount: Int) {
        val u = dao.getUserOnce() ?: return
        dao.updateCoins(u.coins + amount)
        dao.addCoinsEarned(amount)
        db.statsDao().addEarnings(LocalDate.now().toString(), 0, amount)
    }

    suspend fun spendCoins(amount: Int): Boolean {
        val u = dao.getUserOnce() ?: return false
        if (u.coins < amount) return false
        dao.updateCoins(u.coins - amount)
        dao.addCoinsSpent(amount)
        return true
    }

    suspend fun addGems(amount: Int) {
        val u = dao.getUserOnce() ?: return
        dao.updateGems(u.gems + amount)
    }

    suspend fun spendGems(amount: Int): Boolean {
        val u = dao.getUserOnce() ?: return false
        if (u.gems < amount) return false
        dao.updateGems(u.gems - amount)
        return true
    }

    suspend fun checkStreak() {
        val u = dao.getUserOnce() ?: return
        val today = LocalDate.now().toString()
        if (u.lastLoginDate == today) return
        val yesterday = LocalDate.now().minusDays(1).toString()
        val newStreak = if (u.lastLoginDate == yesterday) u.streakDays + 1 else 1
        dao.updateStreak(newStreak, today)
        addXp(XpEngine.XP_DAILY_STREAK)
        addCoins(newStreak * 5)
    }

    suspend fun canWatchAd(): Boolean {
        val u = dao.getUserOnce() ?: return true
        val today = LocalDate.now().toString()
        return if (u.lastAdDate != today) true else u.adCountToday < 50
    }

    suspend fun recordAdWatched() {
        val u = dao.getUserOnce() ?: return
        val today = LocalDate.now().toString()
        val count = if (u.lastAdDate != today) 1 else u.adCountToday + 1
        dao.updateAdCount(count, today)
        dao.incrementAds()
    }

    suspend fun getAdCountToday(): Int {
        val u = dao.getUserOnce() ?: return 0
        val today = LocalDate.now().toString()
        return if (u.lastAdDate != today) 0 else u.adCountToday
    }

    private fun UserEntity.toState() = UserState(
        pseudo = pseudo, level = level, xp = xp, xpNext = xpNext,
        coins = coins, gems = gems, streakDays = streakDays,
        totalFocusMinutes = totalFocusMinutes, totalAdsWatched = totalAdsWatched,
        totalChestsOpened = totalChestsOpened, adCountToday = adCountToday
    )
}
