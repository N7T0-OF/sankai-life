package com.sankailife.ui.screens.shop

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.engine.EconomyEngine
import com.sankailife.core.domain.model.ALL_SHOP_ITEMS
import com.sankailife.core.domain.model.ShopItem
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    val gameRepo = GameRepository(app.database)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val shopItems = ALL_SHOP_ITEMS

    private val _toast     = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    private val _adCooldown = MutableStateFlow(0L)
    val adCooldown: StateFlow<Long> = _adCooldown

    private val _chestReward = MutableStateFlow<ChestEngine.ChestReward?>(null)
    val chestReward: StateFlow<ChestEngine.ChestReward?> = _chestReward

    fun purchase(item: ShopItem) = viewModelScope.launch {
        val u = user.value
        val canBuyCoins = item.costCoins == 0 || u.coins >= item.costCoins
        val canBuyGems  = item.costGems  == 0 || u.gems  >= item.costGems
        if (!canBuyCoins || !canBuyGems) { showToast("Fonds insuffisants ❌"); return@launch }

        if (item.costCoins > 0) { if (!userRepo.spendCoins(item.costCoins)) { showToast("Fonds insuffisants ❌"); return@launch } }
        if (item.costGems  > 0) { if (!userRepo.spendGems(item.costGems))   { showToast("Gemmes insuffisantes ❌"); return@launch } }

        when (item.id) {
            "chest_common", "chest_rare", "chest_epic" -> {
                val type = item.id.removePrefix("chest_").uppercase()
                val added = gameRepo.addChest(type)
                if (added) showToast("${item.name} ajouté ! 🎁")
                else showToast("Coffres pleins (4/4) !")
            }
            "slot_module" -> {
                val cu = app.database.userDao().getUserOnce() ?: return@launch
                app.database.userDao().updateModuleSlots(cu.moduleSlots + 1)
                showToast("+1 slot module débloqué ✅")
            }
            else -> showToast("${item.name} acheté ✅")
        }
    }

    fun watchAd() = viewModelScope.launch {
        if (_adCooldown.value > 0) return@launch
        if (!userRepo.canWatchAd()) { showToast("Limite 50 pubs/jour atteinte"); return@launch }
        userRepo.addCoins(EconomyEngine.COINS_PER_AD)
        userRepo.recordAdWatched()
        val count = userRepo.getAdCountToday()
        gameRepo.updateChallengeProgress("daily_ads", 1)
        gameRepo.updateChallengeProgress("weekly_ads", 1)
        val bonus = when {
            count % 5 == 0 -> { userRepo.addCoins(EconomyEngine.COINS_AD_BONUS_5); "+${EconomyEngine.COINS_PER_AD} 🪙 + Bonus ×5 : +${EconomyEngine.COINS_AD_BONUS_5} 🪙" }
            else -> "+${EconomyEngine.COINS_PER_AD} 🪙"
        }
        showToast(bonus)
        _adCooldown.value = EconomyEngine.AD_COOLDOWN_SEC.toLong()
        while (_adCooldown.value > 0) { delay(1000); _adCooldown.value-- }
    }

    fun dismissChestReward() { _chestReward.value = null }

    private fun showToast(msg: String) = viewModelScope.launch {
        _toast.value = msg; delay(2500); _toast.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ShopViewModel(app) } }
    }
}
