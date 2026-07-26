package com.sankailife.core.domain.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

object XpEngine {
    fun xpForLevel(level: Int): Int = when {
        level <= 1  -> 0
        level <= 5  -> 100 * level
        level <= 10 -> 150 * level
        level <= 20 -> 250 * level
        level <= 30 -> 400 * level
        else        -> 600 * level
    }

    fun checkLevelUp(currentXp: Int, currentLevel: Int): Pair<Int, Int> {
        var lvl = currentLevel
        var xp  = currentXp
        while (xp >= xpForLevel(lvl + 1) && lvl < 100) {
            xp -= xpForLevel(lvl + 1)
            lvl++
        }
        return Pair(lvl, xp)
    }

    fun levelUpRewardCoins(level: Int): Int = level * 50

    const val XP_OPEN_APP          = 5
    const val XP_DAILY_STREAK      = 10
    const val XP_MEMO_NOTIF_OPEN   = 15
    const val XP_FOCUS_25MIN       = 50
    const val XP_FOCUS_LONG        = 80
    const val XP_OBJECTIVE_DONE    = 30
    const val XP_CHALLENGE_DAILY   = 20
    const val XP_CHALLENGE_WEEKLY  = 80
    const val XP_CHEST_OPEN        = 10
}

object EconomyEngine {
    const val COINS_PER_AD          = 5
    const val COINS_AD_BONUS_5      = 10
    const val COINS_AD_MILESTONE_20 = 20
    const val MAX_ADS_PER_DAY       = 50
    const val AD_COOLDOWN_SEC       = 25

    const val COST_CHEST_COMMON     = 200
    const val COST_CHEST_RARE       = 500
    const val COST_BOOST_2X_COINS   = 200
    const val COST_BOOST_SKIP_CD    = 1   // gems
    const val COST_SLOT_MODULE_BASE = 1200

    fun slotCost(currentSlots: Int): Int = when (currentSlots) {
        1 -> 1200
        2 -> 2500
        else -> 5000
    }
}

object StreakEngine {
    fun check(lastLoginDate: String): StreakResult {
        if (lastLoginDate.isBlank()) return StreakResult.FIRST_LOGIN
        return try {
            val last  = LocalDate.parse(lastLoginDate)
            val today = LocalDate.now()
            val diff  = ChronoUnit.DAYS.between(last, today).toInt()
            when (diff) {
                0    -> StreakResult.ALREADY_DONE
                1    -> StreakResult.MAINTAINED
                else -> StreakResult.BROKEN
            }
        } catch (e: Exception) {
            StreakResult.FIRST_LOGIN
        }
    }

    fun streakBonusCoins(streakDays: Int): Int = when (streakDays) {
        1    -> 20
        3    -> 50
        7    -> 200
        14   -> 400
        30   -> 1000
        else -> if (streakDays % 7 == 0) 100 else 0
    }

    enum class StreakResult { FIRST_LOGIN, ALREADY_DONE, MAINTAINED, BROKEN }
}

object MemoEngine {
    fun getRandomLine(lineIds: List<Long>, historyStr: String): Long? {
        if (lineIds.isEmpty()) return null
        val history = historyStr.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        val available = lineIds.filter { it !in history }
        val pool = available.ifEmpty { lineIds }
        return pool[Random.nextInt(pool.size)]
    }

    fun updateHistory(historyStr: String, newId: Long, maxHistory: Int = 10): String {
        val list = historyStr.split(",").mapNotNull { it.toLongOrNull() }.toMutableList()
        list.add(newId)
        if (list.size > maxHistory) list.removeAt(0)
        return list.joinToString(",")
    }

    fun cleanText(raw: String): List<String> {
        return raw.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}

object ChestEngine {
    fun timerMillis(type: String): Long = when (type) {
        "RARE"       -> 6 * 60 * 60 * 1000L
        "EPIC"       -> 12 * 60 * 60 * 1000L
        "LEGENDARY"  -> 24 * 60 * 60 * 1000L
        "DAILY"      -> 0L
        else         -> 3 * 60 * 60 * 1000L  // COMMON
    }

    data class ChestReward(val coins: Int, val gems: Int, val xp: Int, val boostType: String = "")

    fun generateReward(type: String): ChestReward = when (type) {
        "RARE"      -> ChestReward(
            coins = Random.nextInt(50, 151),
            gems  = if (Random.nextFloat() < 0.6f) Random.nextInt(1, 3) else 0,
            xp    = 20
        )
        "EPIC"      -> ChestReward(
            coins = Random.nextInt(100, 301),
            gems  = Random.nextInt(3, 6),
            xp    = 30,
            boostType = if (Random.nextFloat() < 0.5f) "2X_COINS" else ""
        )
        "LEGENDARY" -> ChestReward(
            coins = Random.nextInt(500, 1001),
            gems  = Random.nextInt(10, 16),
            xp    = 50
        )
        "DAILY"     -> ChestReward(
            coins = Random.nextInt(20, 81),
            gems  = if (Random.nextFloat() < 0.2f) 1 else 0,
            xp    = 10
        )
        else        -> ChestReward(   // COMMON
            coins = Random.nextInt(10, 51),
            gems  = 0,
            xp    = 5
        )
    }

    fun formatTimer(millisRemaining: Long): String {
        if (millisRemaining <= 0) return "PRÊT"
        val totalSec = millisRemaining / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
