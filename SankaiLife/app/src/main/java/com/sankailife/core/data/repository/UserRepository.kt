package com.sankailife.core.data.repository

import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.DayRecordEntity
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.domain.engine.RegularityEngine
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

    /**
     * Met à jour la série au retour dans l'application.
     *
     * @return le détail de ce qui s'est passé, pour que l'interface puisse
     *         expliquer un bouclier consommé ou une série cassée plutôt que
     *         de laisser le compteur retomber sans un mot.
     */
    suspend fun checkStreak(): RegularityEngine.Resultat? {
        val u = dao.getUserOnce() ?: return null
        val aujourdhui = LocalDate.now()
        val today = aujourdhui.toString()
        if (u.lastLoginDate == today) return null

        val resultat = RegularityEngine.evaluerRetour(
            dernierJour = u.lastLoginDate,
            serieActuelle = u.streakDays,
            boucliers = u.streakShields,
            aujourdhui = aujourdhui
        )

        // Les jours absorbés par un bouclier sont tracés comme protégés :
        // sans ça le calendrier les montrerait comme des échecs.
        if (resultat.boucliersUtilises > 0) {
            val dernier = runCatching { LocalDate.parse(u.lastLoginDate) }.getOrNull()
            if (dernier != null) {
                for (i in 1..resultat.boucliersUtilises) {
                    db.dayRecordDao().upsert(
                        DayRecordEntity(
                            date = dernier.plusDays(i.toLong()).toString(),
                            status = RegularityEngine.Statut.PROTEGE
                        )
                    )
                }
            }
        }

        dao.updateStreak(resultat.nouvelleSerie, today)
        db.dayRecordDao().upsert(
            DayRecordEntity(date = today, status = RegularityEngine.Statut.SUCCES)
        )

        // Le record ne redescend jamais, même quand la série casse.
        if (resultat.nouvelleSerie > u.bestStreak) {
            dao.updateBestStreak(resultat.nouvelleSerie)
        }

        val gagnes = RegularityEngine.boucliersGagnes(resultat.nouvelleSerie, resultat.boucliersRestants)
        dao.updateShields((resultat.boucliersRestants + gagnes).coerceAtMost(RegularityEngine.MAX_BOUCLIERS))

        addXp(XpEngine.XP_DAILY_STREAK)
        addCoins(resultat.nouvelleSerie * 5)
        return resultat
    }

    /** Régularité sur une fenêtre glissante, en pourcentage. */
    suspend fun regularite(jours: Int): Int {
        val depuis = LocalDate.now().minusDays(jours.toLong()).toString()
        val records = runCatching { db.dayRecordDao().getDepuisOnce(depuis) }.getOrDefault(emptyList())
        return RegularityEngine.pourcentage(records, jours)
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
        totalChestsOpened = totalChestsOpened, adCountToday = adCountToday,
        moduleSlots = moduleSlots, focusSlots = focusSlots,
        bestStreak = bestStreak, streakShields = streakShields
    )
}
