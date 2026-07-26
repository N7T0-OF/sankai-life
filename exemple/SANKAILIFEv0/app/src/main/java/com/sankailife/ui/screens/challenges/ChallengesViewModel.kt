package com.sankailife.ui.screens.challenges

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ChallengeEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChallengesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    val gameRepo = GameRepository(app.database)

    val user = userRepo.userFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
        com.sankailife.core.domain.model.UserState())

    val challenges: StateFlow<List<ChallengeEntity>> = gameRepo.allChallenges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val claimableCount: StateFlow<Int> = gameRepo.claimableCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    init {
        viewModelScope.launch {
            gameRepo.ensureDailyChallenges()
            gameRepo.ensureWeeklyChallenges()
        }
    }

    fun claimChallenge(id: String) = viewModelScope.launch {
        val result = gameRepo.claimChallenge(id) ?: return@launch
        val (coins, xp) = result
        userRepo.addCoins(coins)
        userRepo.addXp(xp)
        showToast(buildString {
            if (coins > 0) append("+$coins 🪙 ")
            if (xp > 0)   append("+$xp XP")
        })
    }

    private fun showToast(msg: String) = viewModelScope.launch {
        _toast.value = msg
        delay(2500)
        _toast.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ChallengesViewModel(app) } }
    }
}
