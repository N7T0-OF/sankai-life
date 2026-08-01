package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    val userRepo  = UserRepository(app.database)
    val gameRepo  = GameRepository(app.database, app)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val chests: StateFlow<List<ChestEntity>> = gameRepo.activeChests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showLevelUp    = MutableStateFlow(false)
    val showLevelUp: StateFlow<Boolean> = _showLevelUp

    private val _levelUpLevel   = MutableStateFlow(0)
    val levelUpLevel: StateFlow<Int> = _levelUpLevel

    private val _chestReward    = MutableStateFlow<ChestEngine.ChestReward?>(null)
    val chestReward: StateFlow<ChestEngine.ChestReward?> = _chestReward

    /** Coffres prêts à ouvrir, pour le badge de l'onglet Accueil. */
    val coffresPrets: StateFlow<Int> = chests
        .map { liste -> liste.count { it.isReady } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
        val ouverture = gameRepo.openChest(chestId) ?: return@launch
        _chestReward.value = ouverture.recompense
        if (ouverture.niveauGagne) {
            _levelUpLevel.value = ouverture.nouveauNiveau
            _showLevelUp.value = true
        }
    }

    fun dismissChestReward() { _chestReward.value = null }
    fun dismissLevelUp()     { _showLevelUp.value = false }

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
