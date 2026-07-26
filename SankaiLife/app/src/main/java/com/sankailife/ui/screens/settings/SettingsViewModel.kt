package com.sankailife.ui.screens.settings

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val prefs = app.preferences

    val themeMode: StateFlow<String>      = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")
    val showNavLabels: StateFlow<Boolean> = prefs.showNavLabels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val vibrations: StateFlow<Boolean>    = prefs.vibrations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifications: StateFlow<Boolean> = prefs.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val batterySaver: StateFlow<Boolean>  = prefs.batterySaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val streakReminder: StateFlow<Boolean> = prefs.streakReminder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: String)         = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setShowNavLabels(v: Boolean)       = viewModelScope.launch { prefs.setShowNavLabels(v) }
    fun setVibrations(v: Boolean)          = viewModelScope.launch { prefs.setVibrations(v) }
    fun setNotifications(v: Boolean)       = viewModelScope.launch { prefs.setNotifications(v) }
    fun setBatterySaver(v: Boolean)        = viewModelScope.launch { prefs.setBatterySaver(v) }
    fun setStreakReminder(v: Boolean)      = viewModelScope.launch { prefs.setStreakReminder(v) }

    fun resetProgress() = viewModelScope.launch {
        app.database.userDao().upsert(com.sankailife.core.data.db.entities.UserEntity())
        app.database.chestDao().cleanOld(Long.MAX_VALUE)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { SettingsViewModel(app) } }
    }
}
