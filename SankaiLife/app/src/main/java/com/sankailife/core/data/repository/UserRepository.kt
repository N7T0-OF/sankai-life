package com.sankailife.core.data.repository

import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.DayRecordEntity
import com.sankailife.core.data.db.entities.StatsEntity
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.domain.engine.EconomyEngine
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
        dao.insertIfAbsent(UserEntity())
    }

    suspend fun addXp(amount: Int): Boolean = db.withTransaction {
        if (amount <= 0) return@withTransaction false
        val u = dao.getUserOnce() ?: return@withTransaction false
        val newXp = u.xp + amount
        val (newLevel, remainXp) = XpEngine.checkLevelUp(newXp, u.level)
        val didLevelUp = newLevel > u.level
        dao.updateXp(remainXp, XpEngine.xpForLevel(newLevel + 1), newLevel)

        val bonus = if (didLevelUp) XpEngine.levelUpRewardCoins(newLevel) else 0
        if (bonus > 0) dao.creditCoins(bonus)
        enregistrerStatistiques(xp = amount, pieces = bonus)
        didLevelUp
    }

    suspend fun addCoins(amount: Int) = db.withTransaction {
        if (amount <= 0 || dao.creditCoins(amount) == 0) return@withTransaction
        enregistrerStatistiques(pieces = amount)
    }

    /** Un remboursement ne gonfle ni les gains cumulés ni les statistiques. */
    suspend fun refundCoins(amount: Int) {
        if (amount > 0) dao.refundCoins(amount)
    }

    suspend fun spendCoins(amount: Int): Boolean = db.withTransaction {
        if (amount < 0 || dao.spendCoinsIfEnough(amount) == 0) return@withTransaction false
        val jour = LocalDate.now().toString()
        db.statsDao().insertIfAbsent(StatsEntity(date = jour))
        db.statsDao().addSpending(jour, amount)
        true
    }

    suspend fun addGems(amount: Int) {
        if (amount > 0) dao.creditGems(amount)
    }

    suspend fun spendGems(amount: Int): Boolean =
        amount >= 0 && dao.spendGemsIfEnough(amount) > 0

    data class AchatSlotModule(val totalSlots: Int, val cout: Int)

    /**
     * Achète un slot avec le prix recalculé à l'intérieur de la transaction.
     *
     * Deux appuis simultanés paient donc bien deux paliers successifs, et un
     * arrêt du processus ne peut plus laisser les pièces débitées sans livrer
     * le slot. [transformerPrix] sert uniquement à appliquer une remise déjà
     * affichée par la boutique.
     */
    suspend fun acheterSlotModule(
        transformerPrix: (Int) -> Int = { it }
    ): AchatSlotModule? = db.withTransaction {
        val utilisateur = dao.getUserOnce() ?: return@withTransaction null
        val cout = transformerPrix(EconomyEngine.slotCost(utilisateur.moduleSlots))
            .coerceAtLeast(1)
        if (dao.spendCoinsIfEnough(cout) == 0) return@withTransaction null

        dao.addModuleSlots(1)
        val jour = LocalDate.now().toString()
        db.statsDao().insertIfAbsent(StatsEntity(date = jour))
        db.statsDao().addSpending(jour, cout)
        AchatSlotModule(totalSlots = utilisateur.moduleSlots + 1, cout = cout)
    }

    private suspend fun enregistrerStatistiques(xp: Int = 0, pieces: Int = 0) {
        val jour = LocalDate.now().toString()
        val stats = db.statsDao()
        stats.insertIfAbsent(StatsEntity(date = jour))
        stats.addEarnings(jour, xp, pieces)
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
        totalChestsOpened = totalChestsOpened,
        // Le compteur du jour est remis à zéro à la lecture, pas seulement à
        // l'écriture.
        //
        // C'était le bug : `recordAdWatched` comparait bien la date, mais ce
        // convertisseur exposait la valeur brute de la base. Tant qu'aucune
        // publicité n'était regardée après minuit, l'écran affichait le total
        // de la veille — et un compteur bloqué à 50 refusait toute nouvelle
        // publicité alors que la limite était réinitialisée côté logique.
        //
        // La ligne en base n'est pas modifiée ici : la remise à zéro est
        // dérivée, donc il n'existe aucun instant où l'affichage et la règle
        // peuvent diverger.
        adCountToday = if (lastAdDate == LocalDate.now().toString()) adCountToday else 0,
        moduleSlots = moduleSlots, focusSlots = focusSlots,
        bestStreak = bestStreak, streakShields = streakShields
    )
}
