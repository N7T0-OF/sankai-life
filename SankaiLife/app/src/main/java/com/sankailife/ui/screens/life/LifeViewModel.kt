package com.sankailife.ui.screens.life

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LifeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    private val memoDao = app.database.memoDao()

    val user = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.sankailife.core.domain.model.UserState())

    val memoProfiles: StateFlow<List<MemoProfileEntity>> = memoDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val objectivesPending: StateFlow<Int> = app.database.objectiveDao().countPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleMemo(profileId: Long, active: Boolean) = viewModelScope.launch {
        memoDao.setActive(profileId, active)
    }

    fun buyModuleSlot() = viewModelScope.launch {
        val u = app.database.userDao().getUserOnce() ?: return@launch
        val cost = com.sankailife.core.domain.engine.EconomyEngine.slotCost(u.moduleSlots)
        if (userRepo.spendCoins(cost)) {
            app.database.userDao().updateModuleSlots(u.moduleSlots + 1)
        }
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { LifeViewModel(app) } }
    }
}
