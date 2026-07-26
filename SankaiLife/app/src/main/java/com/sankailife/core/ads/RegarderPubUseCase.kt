package com.sankailife.core.ads

import android.app.Activity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.EconomyEngine

/** Issue d'un « regarder une pub » du point de vue du joueur. */
sealed interface ResultatPub {
    data class Recompense(val message: String) : ResultatPub
    data class Impossible(val message: String) : ResultatPub
}

/**
 * Regarder une pub récompensée et créditer le joueur.
 *
 * Centralisé ici parce que l'Accueil et la Boutique proposent tous les deux le
 * bouton : les paliers de bonus (5 pubs, 20 pubs) et le plafond journalier
 * doivent rester identiques des deux côtés.
 *
 * Le crédit n'a lieu **que** si AdMob confirme que la pub a été regardée
 * jusqu'au bout. Fermer la pub en avance ne rapporte rien — c'est ce
 * qu'exigent les règles AdMob, et ça évite de se faire bannir du programme.
 */
class RegarderPubUseCase(
    private val userRepo: UserRepository,
    private val gameRepo: GameRepository
) {

    suspend fun executer(activity: Activity, estEnLigne: Boolean): ResultatPub {
        if (!userRepo.canWatchAd()) {
            return ResultatPub.Impossible(AdUnavailableReason.DAILY_LIMIT.message)
        }

        return when (val resultat = AdsManager.showRewarded(activity, estEnLigne)) {
            is AdResult.Unavailable -> ResultatPub.Impossible(resultat.reason.message)
            AdResult.Dismissed -> ResultatPub.Impossible("Pub non terminée — aucune récompense")
            AdResult.Rewarded -> crediter()
        }
    }

    private suspend fun crediter(): ResultatPub {
        userRepo.addCoins(EconomyEngine.COINS_PER_AD)
        userRepo.recordAdWatched()
        gameRepo.updateChallengeProgress("daily_ads", 1)
        gameRepo.updateChallengeProgress("weekly_ads", 1)

        val nombrePubs = userRepo.getAdCountToday()
        val message = StringBuilder("+${EconomyEngine.COINS_PER_AD} 🪙")

        if (nombrePubs > 0 && nombrePubs % 5 == 0) {
            userRepo.addCoins(EconomyEngine.COINS_AD_BONUS_5)
            message.append(" • Bonus 5 pubs : +${EconomyEngine.COINS_AD_BONUS_5} 🪙")
        }
        if (nombrePubs > 0 && nombrePubs % 20 == 0 && gameRepo.addChest("COMMON")) {
            message.append(" • Coffre commun offert 🎁")
        }

        return ResultatPub.Recompense(message.toString())
    }
}
