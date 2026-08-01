package com.sankailife.core.data.repository

import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.notifications.CoffreAlarmReceiver
import com.sankailife.core.data.db.entities.*
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.engine.XpEngine
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.Arena
import java.time.LocalDate

/**
 * @param contexte requis seulement pour programmer les rappels de coffre.
 *   Optionnel à dessein : le dépôt reste utilisable dans un test, et un appel
 *   qui n'a pas de contexte sous la main n'est pas obligé d'en inventer un.
 */
class GameRepository(
    private val db: SankaiDatabase,
    private val contexte: android.content.Context? = null
) {
    private val chestDao     = db.chestDao()
    private val challengeDao = db.challengeDao()

    // ── Chests ───────────────────────────────────────────────────────
    val activeChests = chestDao.getActiveChests()

    suspend fun addChest(type: String): Boolean {
        val cree = db.withTransaction {
            val actifs = chestDao.getActiveChestsOnce()
            if (actifs.size >= 4) return@withTransaction null
            val slot = (0..3).firstOrNull { s -> actifs.none { it.slotIndex == s } }
                ?: return@withTransaction null
            val pretALe = System.currentTimeMillis() + ChestEngine.timerMillis(type)
            val id = chestDao.insert(
                ChestEntity(type = type, slotIndex = slot, unlocksAtMillis = pretALe)
            )
            id to pretALe
        } ?: return false

        // Rappel à l'heure d'ouverture. Un coffre met des heures à mûrir ;
        // sans rappel, il reste prêt pendant des jours et bloque son
        // emplacement, donc toute la progression derrière.
        contexte?.let { CoffreAlarmReceiver.programmer(it, cree.first, cree.second) }
        return true
    }

    /**
     * Ouvre un coffre et renvoie sa récompense.
     *
     * Le marquage précède la génération de la récompense : si deux appuis
     * arrivent presque simultanément, seul le premier passe le verrou et
     * l'emplacement n'est crédité qu'une fois.
     */
    data class CoffreOuvert(
        val recompense: ChestEngine.ChestReward,
        val niveauGagne: Boolean,
        val nouveauNiveau: Int
    )

    suspend fun openChest(chestId: Long): CoffreOuvert? {
        val ouverture = db.withTransaction {
            val chest = chestDao.getActiveChestsOnce().find { it.id == chestId }
                ?: return@withTransaction null
            if (!chest.isReady) return@withTransaction null

            val utilisateur = db.userDao().getUserOnce() ?: return@withTransaction null
            if (chestDao.markOpened(chestId) == 0) return@withTransaction null

            val recompense = ChestEngine.generateReward(chest.type)
            val gainXp = recompense.xp + XpEngine.XP_CHEST_OPEN
            val (niveau, xpRestant) = XpEngine.checkLevelUp(
                utilisateur.xp + gainXp,
                utilisateur.level
            )
            val niveauGagne = niveau > utilisateur.level
            val bonusNiveau = if (niveauGagne) XpEngine.levelUpRewardCoins(niveau) else 0
            val gainPieces = recompense.coins + bonusNiveau

            db.userDao().updateXp(xpRestant, XpEngine.xpForLevel(niveau + 1), niveau)
            if (gainPieces > 0) db.userDao().creditCoins(gainPieces)
            if (recompense.gems > 0) db.userDao().creditGems(recompense.gems)
            db.userDao().incrementChests()
            challengeDao.incrementProgress("weekly_chests", 1)

            val jour = LocalDate.now().toString()
            val stats = db.statsDao()
            val actuelles = stats.getToday(jour) ?: StatsEntity(date = jour)
            stats.upsert(
                actuelles.copy(
                    xpGained = actuelles.xpGained + gainXp,
                    coinsGained = actuelles.coinsGained + gainPieces,
                    chestsOpened = actuelles.chestsOpened + 1
                )
            )

            CoffreOuvert(recompense, niveauGagne, niveau)
        } ?: return null

        // Le rappel n'a plus lieu d'être : le coffre est ouvert.
        contexte?.let { CoffreAlarmReceiver.annuler(it, chestId) }
        return ouverture
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
            userDao.libererCoffreQuotidien(aujourdhui)
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
        challengeDao.incrementProgress(id, amount)
    }

    sealed interface ReclamationDefi {
        data class Reussie(
            val pieces: Int,
            val xp: Int,
            val coffre: String
        ) : ReclamationDefi
        data object CoffresPleins : ReclamationDefi
    }

    suspend fun claimChallenge(id: String): ReclamationDefi? {
        var alarmeCoffre: Pair<Long, Long>? = null
        val resultat = db.withTransaction {
            val all = challengeDao.getByType("DAILY") + challengeDao.getByType("WEEKLY")
            val defi = all.find { it.id == id && it.isComplete && !it.isClaimed }
                ?: return@withTransaction null
            val utilisateur = db.userDao().getUserOnce() ?: return@withTransaction null

            val typeCoffre = defi.rewardChestType
            val emplacement = if (typeCoffre.isNotBlank()) {
                val actifs = chestDao.getActiveChestsOnce()
                if (actifs.size >= 4) return@withTransaction ReclamationDefi.CoffresPleins
                (0..3).firstOrNull { slot -> actifs.none { it.slotIndex == slot } }
                    ?: return@withTransaction ReclamationDefi.CoffresPleins
            } else null

            if (challengeDao.markClaimed(id) == 0) return@withTransaction null

            val (niveau, xpRestant) = XpEngine.checkLevelUp(
                utilisateur.xp + defi.rewardXp,
                utilisateur.level
            )
            val bonusNiveau = if (niveau > utilisateur.level) {
                XpEngine.levelUpRewardCoins(niveau)
            } else 0
            val piecesCreditees = defi.rewardCoins + bonusNiveau

            db.userDao().updateXp(xpRestant, XpEngine.xpForLevel(niveau + 1), niveau)
            if (piecesCreditees > 0) db.userDao().creditCoins(piecesCreditees)

            if (typeCoffre.isNotBlank() && emplacement != null) {
                val pretALe = System.currentTimeMillis() + ChestEngine.timerMillis(typeCoffre)
                val coffreId = chestDao.insert(
                    ChestEntity(
                        type = typeCoffre,
                        slotIndex = emplacement,
                        unlocksAtMillis = pretALe
                    )
                )
                alarmeCoffre = coffreId to pretALe
            }

            val jour = LocalDate.now().toString()
            val stats = db.statsDao()
            val actuelles = stats.getToday(jour) ?: StatsEntity(date = jour)
            stats.upsert(
                actuelles.copy(
                    xpGained = actuelles.xpGained + defi.rewardXp,
                    coinsGained = actuelles.coinsGained + piecesCreditees
                )
            )

            ReclamationDefi.Reussie(
                pieces = piecesCreditees,
                xp = defi.rewardXp,
                coffre = typeCoffre
            )
        }

        alarmeCoffre?.let { (coffreId, pretALe) ->
            contexte?.let { CoffreAlarmReceiver.programmer(it, coffreId, pretALe) }
        }
        return resultat
    }

    sealed interface ReclamationArene {
        data object Reussie : ReclamationArene
        data object CoffresPleins : ReclamationArene
    }

    /** Réserve et livre toute la récompense d'arène dans une même transaction. */
    suspend fun reclamerArene(arene: Arena): ReclamationArene? {
        var alarmeCoffre: Pair<Long, Long>? = null
        val resultat = db.withTransaction {
            val utilisateur = db.userDao().getUserOnce() ?: return@withTransaction null
            if (!ArenaEngine.estAtteinte(arene, utilisateur.level)) return@withTransaction null

            val recompense = arene.recompense
            val emplacement = if (recompense.chestType.isNotBlank()) {
                val actifs = chestDao.getActiveChestsOnce()
                if (actifs.size >= 4) return@withTransaction ReclamationArene.CoffresPleins
                (0..3).firstOrNull { slot -> actifs.none { it.slotIndex == slot } }
                    ?: return@withTransaction ReclamationArene.CoffresPleins
            } else null

            val insere = db.arenaRewardDao().marquerReclamee(
                ArenaRewardEntity(arenaId = arene.id, claimedAt = System.currentTimeMillis())
            )
            if (insere == -1L) return@withTransaction null

            if (recompense.coins > 0) db.userDao().creditCoins(recompense.coins)
            if (recompense.gems > 0) db.userDao().creditGems(recompense.gems)
            if (recompense.moduleSlots > 0) {
                db.userDao().addModuleSlots(recompense.moduleSlots)
            }

            if (recompense.chestType.isNotBlank() && emplacement != null) {
                val pretALe = System.currentTimeMillis() +
                    ChestEngine.timerMillis(recompense.chestType)
                val coffreId = chestDao.insert(
                    ChestEntity(
                        type = recompense.chestType,
                        slotIndex = emplacement,
                        unlocksAtMillis = pretALe
                    )
                )
                alarmeCoffre = coffreId to pretALe
            }

            if (recompense.coins > 0) {
                val jour = LocalDate.now().toString()
                val stats = db.statsDao()
                val actuelles = stats.getToday(jour) ?: StatsEntity(date = jour)
                stats.upsert(
                    actuelles.copy(coinsGained = actuelles.coinsGained + recompense.coins)
                )
            }
            ReclamationArene.Reussie
        }

        alarmeCoffre?.let { (coffreId, pretALe) ->
            contexte?.let { CoffreAlarmReceiver.programmer(it, coffreId, pretALe) }
        }
        return resultat
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
