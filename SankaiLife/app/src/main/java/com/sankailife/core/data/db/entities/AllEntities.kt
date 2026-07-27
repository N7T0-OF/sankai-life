package com.sankailife.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: Long = 1L,
    val pseudo: String = "Joueur",
    val level: Int = 1,
    val xp: Int = 0,
    val xpNext: Int = 200,
    val coins: Int = 500,
    val gems: Int = 5,
    val streakDays: Int = 0,
    val lastLoginDate: String = "",
    val totalFocusMinutes: Int = 0,
    val totalAdsWatched: Int = 0,
    val totalChestsOpened: Int = 0,
    val equippedThemeId: String = "default",
    val unlockedThemeIds: String = "default",
    val moduleSlots: Int = 1,
    val focusSlots: Int = 1,
    val memoProfileSlots: Int = 1,
    val adCountToday: Int = 0,
    val lastAdDate: String = "",
    val totalCoinsEarned: Int = 0,
    val totalCoinsSpent: Int = 0
)

@Entity(tableName = "memo_profile")
data class MemoProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val frequencyPerDay: Int = 1,
    val scheduledHour: Int = 18,
    val scheduledMinute: Int = 0,
    val isActive: Boolean = false,
    val sentLineHistory: String = "",  // comma-separated IDs
    val lastNotifiedAtMillis: Long = 0L,

    /** Mode aléatoire : l'heure est tirée dans une plage au lieu d'être fixe. */
    val randomMode: Boolean = false,
    val randomStartHour: Int = 9,
    val randomStartMinute: Int = 0,
    val randomEndHour: Int = 21,
    val randomEndMinute: Int = 0,

    /** Jours actifs au format ISO, 1 = lundi. Par défaut toute la semaine. */
    val activeDays: String = "1,2,3,4,5,6,7",

    /** Instant de la prochaine alarme programmée, pour l'écran de diagnostic. */
    val nextTriggerAtMillis: Long = 0L
)

@Entity(tableName = "memo_line")
data class MemoLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long = 0L,
    val text: String = "",
    val orderIndex: Int = 0,

    // --- État de révision (flash cards) ---------------------------------
    // Boîte de Leitner : 0 = à revoir vite, 4 = acquis de longue date.
    val box: Int = 0,
    /** 0 = jamais révisée, donc due immédiatement. */
    val nextReviewAtMillis: Long = 0L,
    val reviewCount: Int = 0,
    val successCount: Int = 0
)

@Entity(tableName = "objective")
data class ObjectiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String = "",
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
)

@Entity(tableName = "chest")
data class ChestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String = "COMMON",      // COMMON / RARE / EPIC / DAILY / LEGENDARY
    val slotIndex: Int = 0,
    val unlocksAtMillis: Long = 0L,
    val isOpened: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isReady: Boolean get() = System.currentTimeMillis() >= unlocksAtMillis
}

@Entity(tableName = "challenge")
data class ChallengeEntity(
    @PrimaryKey val id: String = "",
    val type: String = "DAILY",         // DAILY / WEEKLY
    val title: String = "",
    val description: String = "",
    val targetAmount: Int = 1,
    val currentProgress: Int = 0,
    val rewardCoins: Int = 0,
    val rewardXp: Int = 0,
    val rewardChestType: String = "",
    val isClaimed: Boolean = false,
    val resetDate: String = ""
) {
    val isComplete: Boolean get() = currentProgress >= targetAmount
}

@Entity(tableName = "daily_stats")
data class StatsEntity(
    @PrimaryKey val date: String = "",
    val xpGained: Int = 0,
    val coinsGained: Int = 0,
    val coinsSpent: Int = 0,
    val focusSessions: Int = 0,
    val focusMinutes: Int = 0,
    val chestsOpened: Int = 0,
    val adsWatched: Int = 0,
    val memoLinesReceived: Int = 0
)
