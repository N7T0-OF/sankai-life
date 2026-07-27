package com.sankailife.ui.screens.home

import android.app.Activity
import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.ads.RegarderPubUseCase
import com.sankailife.core.ads.ResultatPub
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ArenaEngine
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

    private val pubUseCase = RegarderPubUseCase(userRepo, gameRepo)

    /** Sert uniquement à griser le bouton pub — le reste de l'écran marche hors ligne. */
    val isOnline: StateFlow<Boolean> = app.connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), app.connectivity.currentlyOnline())

    /** Pastille de la carte de progression : récompenses d'arène en attente. */
    val arenesAReclamer: StateFlow<Int> =
        combine(user, app.database.arenaRewardDao().getReclamees()) { u, prises ->
            ArenaEngine.recompensesAReclamer(u.level, prises.toSet()).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    fun watchAd(activity: Activity) = viewModelScope.launch {
        if (_adCooldown.value > 0) return@launch

        when (val resultat = pubUseCase.executer(activity, isOnline.value)) {
            is ResultatPub.Recompense -> {
                todayCoins.value += EconomyEngine.COINS_PER_AD
                showToast(resultat.message)
                // Le cooldown ne démarre qu'après une pub réellement regardée :
                // un échec réseau ne doit pas pénaliser le joueur.
                _adCooldown.value = EconomyEngine.AD_COOLDOWN_SEC.toLong()
                while (_adCooldown.value > 0) { delay(1000); _adCooldown.value-- }
            }
            is ResultatPub.Impossible -> showToast(resultat.message)
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
