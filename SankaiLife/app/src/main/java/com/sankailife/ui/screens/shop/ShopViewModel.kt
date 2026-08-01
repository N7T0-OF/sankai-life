package com.sankailife.ui.screens.shop

import android.app.Activity
import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.ads.RegarderPubUseCase
import com.sankailife.core.ads.ResultatPub
import com.sankailife.core.ads.PrivacyConsentManager
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.engine.EconomyEngine
import com.sankailife.core.domain.model.ALL_SHOP_ITEMS
import java.time.LocalDate
import com.sankailife.core.domain.model.ShopItem
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    val gameRepo = GameRepository(app.database, app)
    private val gardenRepo = com.sankailife.core.garden.data.GardenRepository(
        app.database, userRepo
    )

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val shopItems = ALL_SHOP_ITEMS

    private val _toast     = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    private val _adCooldown = MutableStateFlow(0L)
    val adCooldown: StateFlow<Long> = _adCooldown

    private val _chestReward = MutableStateFlow<ChestEngine.ChestReward?>(null)
    val chestReward: StateFlow<ChestEngine.ChestReward?> = _chestReward

    private val pubUseCase = RegarderPubUseCase(userRepo, gameRepo)

    /** Grise le bloc pub hors connexion ; le reste de la boutique reste utilisable. */
    val isOnline: StateFlow<Boolean> = app.connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), app.connectivity.currentlyOnline())

    val adsAutorisees: StateFlow<Boolean> = PrivacyConsentManager.adsAutorisees

    /** Remise appliquée à l'offre du jour. */
    private val remiseOffre = 0.25f

    /**
     * Article mis en avant, choisi à partir de la date.
     *
     * Déterministe et sans état : le même article toute la journée, différent
     * demain, identique après un redémarrage. Un tirage aléatoire stocké
     * aurait demandé une colonne en base et se serait désynchronisé au
     * changement de fuseau horaire.
     */
    val offreDuJour: ShopItem? =
        ALL_SHOP_ITEMS.filter { it.costCoins > 0 }.let { eligibles ->
            if (eligibles.isEmpty()) null
            else eligibles[(LocalDate.now().toEpochDay() % eligibles.size).toInt()]
        }

    fun estOffreDuJour(item: ShopItem): Boolean = item.id == offreDuJour?.id

    /**
     * Coût réel d'un article.
     *
     * Le slot module augmente à chaque achat, il ne peut donc pas vivre en dur
     * dans ALL_SHOP_ITEMS. Et la remise est appliquée ici, pas seulement à
     * l'affichage : sinon le prix montré mentirait sur ce qui sera débité.
     */
    fun coutReel(item: ShopItem, u: UserState): Int {
        val base = if (item.id == "slot_module") EconomyEngine.slotCost(u.moduleSlots)
                   else item.costCoins
        return if (estOffreDuJour(item) && base > 0) {
            (base * (1f - remiseOffre)).toInt().coerceAtLeast(1)
        } else base
    }

    fun purchase(item: ShopItem) = viewModelScope.launch {
        val u = user.value
        val prixPieces = coutReel(item, u)

        if (item.id == "slot_module") {
            val achat = userRepo.acheterSlotModule { base ->
                if (estOffreDuJour(item)) {
                    (base * (1f - remiseOffre)).toInt().coerceAtLeast(1)
                } else base
            }
            if (achat == null) showToast("Pièces insuffisantes ❌")
            else showToast("+1 slot module • ${achat.totalSlots} au total ✅")
            return@launch
        }

        if (prixPieces > u.coins) { showToast("Pièces insuffisantes ❌"); return@launch }
        if (item.costGems > u.gems) { showToast("Gemmes insuffisantes ❌"); return@launch }

        // Le débit vient avant la livraison : si la livraison échoue, on
        // rembourse. L'inverse permettrait d'obtenir l'article sans payer.
        if (prixPieces > 0 && !userRepo.spendCoins(prixPieces)) {
            showToast("Pièces insuffisantes ❌"); return@launch
        }
        if (item.costGems > 0 && !userRepo.spendGems(item.costGems)) {
            if (prixPieces > 0) userRepo.refundCoins(prixPieces)
            showToast("Gemmes insuffisantes ❌"); return@launch
        }

        when (item.id) {
            "chest_common", "chest_rare", "chest_epic" -> {
                val type = item.id.removePrefix("chest_").uppercase()
                if (gameRepo.addChest(type)) {
                    showToast("${item.name} ajouté ! 🎁")
                } else {
                    // File pleine : on rembourse intégralement.
                    if (prixPieces > 0) userRepo.refundCoins(prixPieces)
                    if (item.costGems > 0) userRepo.addGems(item.costGems)
                    showToast("Coffres pleins (4/4) — remboursé")
                }
            }
            "eau_10", "eau_30" -> {
                val quantite = if (item.id == "eau_30") 30 else 10
                if (gardenRepo.ajouterEau(quantite)) {
                    showToast("+$quantite 💧")
                } else {
                    rembourser(prixPieces, item.costGems)
                    showToast("Ta réserve d'eau est pleine — remboursé")
                }
            }

            "compost_10" -> {
                gardenRepo.ajouterCompost(10)
                showToast("+10 🌱 de compost")
            }

            "bouclier" -> {
                val cu = app.database.userDao().getUserOnce()
                if (cu == null || cu.streakShields >= 3) {
                    rembourser(prixPieces, item.costGems)
                    showToast("Tu as déjà trois boucliers — remboursé")
                } else {
                    app.database.userDao().upsert(
                        cu.copy(streakShields = cu.streakShields + 1)
                    )
                    showToast("Bouclier ajouté 🛡️")
                }
            }

            // Filet de sécurité : un article ajouté au catalogue sans être
            // câblé ici est REMBOURSÉ, pas encaissé avec un « acheté » de
            // politesse. C'est exactement ce qui rendait la boutique
            // malhonnête avant, et le défaut doit désormais coûter à la
            // boutique plutôt qu'au joueur.
            else -> {
                rembourser(prixPieces, item.costGems)
                showToast("Cet article n'est pas encore disponible — remboursé")
            }
        }
    }

    private suspend fun rembourser(pieces: Int, gemmes: Int) {
        if (pieces > 0) userRepo.refundCoins(pieces)
        if (gemmes > 0) userRepo.addGems(gemmes)
    }

    fun watchAd(activity: Activity) = viewModelScope.launch {
        if (_adCooldown.value > 0) return@launch

        when (val resultat = pubUseCase.executer(activity, isOnline.value)) {
            is ResultatPub.Recompense -> {
                showToast(resultat.message)
                _adCooldown.value = EconomyEngine.AD_COOLDOWN_SEC.toLong()
                while (_adCooldown.value > 0) { delay(1000); _adCooldown.value-- }
            }
            is ResultatPub.Impossible -> showToast(resultat.message)
        }
    }

    fun dismissChestReward() { _chestReward.value = null }

    private fun showToast(msg: String) = viewModelScope.launch {
        _toast.value = msg; delay(2500); _toast.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ShopViewModel(app) } }
    }
}
