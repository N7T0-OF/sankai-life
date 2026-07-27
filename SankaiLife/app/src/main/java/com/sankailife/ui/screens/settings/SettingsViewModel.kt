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
import com.sankailife.core.update.UpdateManager
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

    /** Grise les liens externes hors connexion. Le reste de l'écran reste actif. */
    val isOnline: StateFlow<Boolean> = app.connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), app.connectivity.currentlyOnline())

    // ----- Mises à jour ----------------------------------------------------

    data class EtatMaj(
        val recherche: Boolean = false,
        val telechargement: Boolean = false,
        val progression: Float = 0f,
        val message: String = "",
        val disponible: UpdateManager.Maj? = null
    )

    private val _maj = MutableStateFlow(EtatMaj())
    val maj: StateFlow<EtatMaj> = _maj

    val versionInstallee: String get() = UpdateManager.versionInstallee

    fun rechercherMaj() = viewModelScope.launch {
        if (!app.connectivity.currentlyOnline()) {
            _maj.value = EtatMaj(message = "Connexion internet requise")
            return@launch
        }
        _maj.value = EtatMaj(recherche = true, message = "Recherche…")

        _maj.value = when (val r = UpdateManager.rechercher()) {
            is UpdateManager.Resultat.AJour ->
                EtatMaj(message = "Tu es à jour (version ${r.versionActuelle})")
            is UpdateManager.Resultat.Disponible ->
                EtatMaj(disponible = r.maj, message = "Version ${r.maj.versionName} disponible")
            is UpdateManager.Resultat.Erreur ->
                EtatMaj(message = r.message)
        }
    }

    fun telechargerMaj(context: Context) = viewModelScope.launch {
        val maj = _maj.value.disponible ?: return@launch

        // Sans cette autorisation, l'installateur s'ouvrirait pour rien.
        if (!UpdateManager.peutInstaller(context)) {
            UpdateManager.intentAutorisationInstallation(context)?.let {
                runCatching { context.startActivity(it) }
            }
            _maj.value = _maj.value.copy(
                message = "Autorise l'installation depuis cette source, puis relance"
            )
            return@launch
        }

        _maj.value = _maj.value.copy(
            telechargement = true, progression = 0f, message = "Téléchargement…"
        )

        val erreur = UpdateManager.telechargerEtInstaller(context, maj) { p ->
            _maj.value = _maj.value.copy(progression = p)
        }

        _maj.value = if (erreur == null) {
            EtatMaj(message = "Installation lancée — confirme sur l'écran Android")
        } else {
            _maj.value.copy(telechargement = false, message = erreur)
        }
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
