package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.engine.EconomyEngine
import com.sankailife.core.domain.engine.XpEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    val userRepo  = UserRepository(app.database)
    val gameRepo  = GameRepository(app.database)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val chests: StateFlow<List<ChestEntity>> = gameRepo.activeChests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayXp    = MutableStateFlow(0)
    val todayCoins = MutableStateFlow(0)

    private val _showLevelUp    = MutableStateFlow(false)
    val showLevelUp: StateFlow<Boolean> = _showLevelUp

    private val _levelUpLevel   = MutableStateFlow(0)
    val levelUpLevel: StateFlow<Int> = _levelUpLevel

    private val _chestReward    = MutableStateFlow<ChestEngine.ChestReward?>(null)
    val chestReward: StateFlow<ChestEngine.ChestReward?> = _chestReward

    private val _toastMessage   = MutableStateFlow("")
    val toastMessage: StateFlow<String> = _toastMessage

    private val _adCooldown     = MutableStateFlow(0L)
    val adCooldown: StateFlow<Long> = _adCooldown

    init {
        viewModelScope.launch {
            userRepo.ensureUser()
            userRepo.checkStreak()
            gameRepo.ensureDailyChallenges()
            gameRepo.ensureWeeklyChallenges()
            gameRepo.addDailyChest()
            // Update today's stats from db
        }
    }

    fun openChest(chestId: Long) = viewModelScope.launch {
        val reward = gameRepo.openChest(chestId) ?: return@launch
        userRepo.addCoins(reward.coins)
        userRepo.addGems(reward.gems)
        val didLevelUp = userRepo.addXp(reward.xp + XpEngine.XP_CHEST_OPEN)
        _chestReward.value = reward
        if (didLevelUp) {
            _levelUpLevel.value = user.value.level
            _showLevelUp.value = true
        }
    }

    fun dismissChestReward() { _chestReward.value = null }
    fun dismissLevelUp()     { _showLevelUp.value = false }

    fun watchAd() = viewModelScope.launch {
        if (_adCooldown.value > 0) return@launch
        if (!userRepo.canWatchAd()) { showToast("Limite journalière atteinte (50 pubs)"); return@launch }
        // Simulate ad (real: AdMob rewarded)
        userRepo.addCoins(EconomyEngine.COINS_PER_AD)
        userRepo.recordAdWatched()
        todayCoins.value += EconomyEngine.COINS_PER_AD
        val count = userRepo.getAdCountToday()
        val msg = when {
            count % 5 == 0 -> "+${EconomyEngine.COINS_PER_AD} 🪙 — Bonus ×5 pubs : +${EconomyEngine.COINS_AD_BONUS_5} 🪙 !"
            else -> "+${EconomyEngine.COINS_PER_AD} 🪙"
        }
        if (count % 5 == 0) userRepo.addCoins(EconomyEngine.COINS_AD_BONUS_5)
        showToast(msg)
        gameRepo.updateChallengeProgress("daily_ads", 1)
        // Start cooldown
        _adCooldown.value = EconomyEngine.AD_COOLDOWN_SEC.toLong()
        viewModelScope.launch {
            while (_adCooldown.value > 0) { delay(1000); _adCooldown.value-- }
        }
    }

    private fun showToast(msg: String) = viewModelScope.launch {
        _toastMessage.value = msg
        delay(2500)
        _toastMessage.value = ""
    }

    fun formatChestTimer(chest: ChestEntity): String {
        val remaining = chest.unlocksAtMillis - System.currentTimeMillis()
        return ChestEngine.formatTimer(remaining.coerceAtLeast(0))
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { HomeViewModel(app) }
        }
    }
}
