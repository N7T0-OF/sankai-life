package com.sankailife.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.notifications.MemoAlarmScheduler
import com.sankailife.core.notifications.SankaiNotifications
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    // ----- Heures silencieuses -------------------------------------------
    val quietEnabled: StateFlow<Boolean> = prefs.quietEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val quietStart: StateFlow<Int> = prefs.quietStartMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 23 * 60)
    val quietEnd: StateFlow<Int> = prefs.quietEndMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8 * 60)

    // Changer une heure silencieuse invalide les alarmes déjà posées : il faut
    // reprogrammer, sinon un mémo tomberait en pleine plage silencieuse.
    fun setQuietEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setQuietEnabled(v)
        MemoAlarmScheduler.replanifierTout(app)
    }
    fun setQuietStart(minutes: Int) = viewModelScope.launch {
        prefs.setQuietStart(minutes)
        MemoAlarmScheduler.replanifierTout(app)
    }
    fun setQuietEnd(minutes: Int) = viewModelScope.launch {
        prefs.setQuietEnd(minutes)
        MemoAlarmScheduler.replanifierTout(app)
    }

    // ----- Diagnostic ------------------------------------------------------
    data class Diagnostic(
        val notificationsAutorisees: Boolean = false,
        val alarmesExactes: Boolean = false,
        val prochaine: String = ""
    )

    private val _diagnostic = MutableStateFlow(Diagnostic())
    val diagnostic: StateFlow<Diagnostic> = _diagnostic

    fun rafraichirDiagnostic() = viewModelScope.launch {
        val prochaineMillis = app.database.memoDao().getAllProfilesOnce()
            .map { it.nextTriggerAtMillis }
            .filter { it > 0L }
            .minOrNull()

        val format = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH)
        _diagnostic.value = Diagnostic(
            notificationsAutorisees = SankaiNotifications.peutNotifier(app),
            alarmesExactes = MemoAlarmScheduler.peutPlanifierExact(app),
            prochaine = prochaineMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(format)
            } ?: ""
        )
    }

    fun ouvrirReglageAlarmes(context: Context) {
        val intent = MemoAlarmScheduler.intentReglageAlarmesExactes(context) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun envoyerNotificationTest(context: Context) {
        SankaiNotifications.afficherRappel(
            context,
            "🔔 Notification test",
            "Si tu vois ce message, les notifications fonctionnent."
        )
    }

    fun reprogrammerTout(context: Context) = viewModelScope.launch {
        MemoAlarmScheduler.replanifierTout(context)
        rafraichirDiagnostic()
    }

    fun resetProgress() = viewModelScope.launch {
        app.database.userDao().upsert(com.sankailife.core.data.db.entities.UserEntity())
        app.database.chestDao().cleanOld(Long.MAX_VALUE)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { SettingsViewModel(app) } }
    }
}
