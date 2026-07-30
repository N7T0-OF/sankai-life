package com.sankailife.core.data.repository

import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.*
import com.sankailife.core.domain.engine.ChestEngine
import java.time.LocalDate

class GameRepository(private val db: SankaiDatabase) {
    private val chestDao     = db.chestDao()
    private val challengeDao = db.challengeDao()

    // ── Chests ───────────────────────────────────────────────────────
    val activeChests = chestDao.getActiveChests()

    suspend fun addChest(type: String): Boolean {
        val count = chestDao.countActive()
        if (count >= 4) return false
        val timer = ChestEngine.timerMillis(type)
        val slot  = (0..3).firstOrNull { s -> chestDao.getActiveChestsOnce().none { it.slotIndex == s } } ?: return false
        chestDao.insert(ChestEntity(
            type = type, slotIndex = slot,
            unlocksAtMillis = System.currentTimeMillis() + timer
        ))
        return true
    }

    suspend fun openChest(chestId: Long): ChestEngine.ChestReward? {
        val chest = chestDao.getActiveChestsOnce().find { it.id == chestId } ?: return null
        if (!chest.isReady) return null
        chestDao.markOpened(chestId)
        db.userDao().incrementChests()
        return ChestEngine.generateReward(chest.type)
    }

    // ── Daily Chest ───────────────────────────────────────────────────
    suspend fun hasDailyChest(): Boolean =
        chestDao.getActiveChestsOnce().any { it.type == "DAILY" && !it.isReady.not() }

    /**
     * Accorde le coffre quotidien, une seule fois par jour.
     *
     * L'ancienne version testait seulement l'absence de coffre DAILY non
     * ouvert : dès que le joueur l'ouvrait, la condition redevenait vraie et
     * un nouveau coffre apparaissait immédiatement, le même jour.
     *
     * La réservation par date sert désormais de verrou. Elle est prise AVANT
     * la création : si l'insertion échoue, on rend la réservation plutôt que
     * de risquer un jour sans coffre du tout.
     */
    suspend fun addDailyChest() {
        val aujourdhui = LocalDate.now().toString()
        val userDao = db.userDao()

        if (userDao.reserverCoffreQuotidien(aujourdhui) == 0) return

        if (!addChest("DAILY")) {
            // File pleine : on libère la réservation pour réessayer plus tard,
            // sinon le joueur perdrait son coffre du jour sans explication.
            userDao.reserverCoffreQuotidien("")
        }
    }

    // ── Challenges ────────────────────────────────────────────────────
    val allChallenges = challengeDao.getAllChallenges()
    val claimableCount = challengeDao.countClaimable()

    suspend fun ensureDailyChallenges() {
        val today   = LocalDate.now().toString()
        val existing = challengeDao.getByType("DAILY")
        if (existing.isNotEmpty() && existing.first().resetDate == today) return
        challengeDao.deleteByType("DAILY")
        defaultDailyChallenges(today).forEach { challengeDao.upsert(it) }
    }

    suspend fun ensureWeeklyChallenges() {
        val week = LocalDate.now().let { "${it.year}-W${it.dayOfYear / 7}" }
        val existing = challengeDao.getByType("WEEKLY")
        if (existing.isNotEmpty() && existing.first().resetDate == week) return
        challengeDao.deleteByType("WEEKLY")
        defaultWeeklyChallenges(week).forEach { challengeDao.upsert(it) }
    }

    suspend fun updateChallengeProgress(id: String, amount: Int) {
        val all = challengeDao.getByType("DAILY") + challengeDao.getByType("WEEKLY")
        val ch  = all.find { it.id == id } ?: return
        challengeDao.updateProgress(id, ch.currentProgress + amount)
    }

    suspend fun claimChallenge(id: String): Pair<Int, Int>? {
        val all = challengeDao.getByType("DAILY") + challengeDao.getByType("WEEKLY")
        val ch  = all.find { it.id == id && it.isComplete && !it.isClaimed } ?: return null
        challengeDao.markClaimed(id)
        return Pair(ch.rewardCoins, ch.rewardXp)
    }

    private fun defaultDailyChallenges(date: String) = listOf(
        ChallengeEntity("daily_ads",      "DAILY", "Regarder 2 pubs",        "Regarde 2 publicités",        2, 0, 50,  0,  "",      false, date),
        ChallengeEntity("daily_obj",      "DAILY", "Compléter 1 objectif",   "Valide un objectif",          1, 0, 0,   80, "",      false, date),
        ChallengeEntity("daily_focus",    "DAILY", "1 session Focus",        "Fais une session Focus",      1, 0, 40,  30, "",      false, date),
        ChallengeEntity("daily_open",     "DAILY", "Ouvrir l'app",           "Lance l'application",         1, 1, 0,   15, "",      false, date)
    )

    private fun defaultWeeklyChallenges(week: String) = listOf(
        ChallengeEntity("weekly_streak",  "WEEKLY", "7 jours consécutifs",   "Streak de 7 jours",           7, 0, 200, 100, "RARE", false, week),
        ChallengeEntity("weekly_chests",  "WEEKLY", "Ouvrir 3 coffres",      "Ouvre 3 coffres",             3, 0, 150, 0,   "",     false, week),
        ChallengeEntity("weekly_focus",   "WEEKLY", "3 sessions Focus",      "Fais 3 sessions Focus",       3, 0, 100, 100, "",     false, week),
        ChallengeEntity("weekly_ads",     "WEEKLY", "Regarder 10 pubs",      "Regarde 10 publicités",       10, 0, 200, 0,   "",    false, week)
    )
}
