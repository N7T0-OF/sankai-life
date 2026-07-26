package com.sankailife.ui.screens.profile

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.model.ALL_THEMES
import com.sankailife.core.domain.model.Theme
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val rawUser: StateFlow<UserEntity?> = app.database.userDao().getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getThemes(unlockedIds: String, level: Int): List<Pair<Theme, Boolean>> {
        val unlocked = unlockedIds.split(",").toSet()
        return ALL_THEMES.map { theme ->
            val isUnlocked = theme.id in unlocked ||
                    (theme.unlockType == "level" && level >= theme.unlockLevel) ||
                    theme.unlockType == "default"
            Pair(theme, isUnlocked)
        }
    }

    fun equipTheme(themeId: String) = viewModelScope.launch {
        app.database.userDao().updateTheme(themeId)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ProfileViewModel(app) } }
    }
}
