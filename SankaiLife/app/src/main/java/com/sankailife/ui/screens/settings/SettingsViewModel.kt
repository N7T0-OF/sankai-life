package com.sankailife.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.sankailife.SankaiApplication
import com.sankailife.R
import com.sankailife.core.notifications.NotificationCoordinator
import com.sankailife.core.notifications.MemoAlarmScheduler
import com.sankailife.core.notifications.SankaiNotifications
import com.sankailife.core.update.UpdateManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val prefs = app.preferences

    val themeMode: StateFlow<String>      = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")
    val showNavLabels: StateFlow<Boolean> = prefs.showNavLabels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val vibrations: StateFlow<Boolean>    = prefs.vibrations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifications: StateFlow<Boolean> = prefs.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val batterySaver: StateFlow<Boolean>  = prefs.batterySaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val minimalMode: StateFlow<Boolean> = prefs.minimalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dailyMinutes: StateFlow<Int> = prefs.dailyMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val notificationDailyMax: StateFlow<Int> = prefs.notificationDailyMax
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val notificationPauseUntil: StateFlow<Long> = prefs.notificationPauseUntilEpochDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val weekendQuiet: StateFlow<Boolean> = prefs.weekendQuiet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val notifyLearning: StateFlow<Boolean> = prefs.notifyLearning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyMemo: StateFlow<Boolean> = prefs.notifyMemo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyCulture: StateFlow<Boolean> = prefs.notifyCulture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val notifyFocus: StateFlow<Boolean> = prefs.notifyFocus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val couleursSysteme: StateFlow<Boolean> = prefs.couleursSysteme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: String)         = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setCouleursSysteme(actif: Boolean) = viewModelScope.launch { prefs.setCouleursSysteme(actif) }

    /**
     * Palette choisie, en tenant compte de l'ancien reglage booleen.
     *
     * Tant que le joueur n'a pas choisi explicitement, on respecte ce qu'il
     * avait : le booleen precedent fait foi.
     */
    val palette: StateFlow<String> = combine(prefs.palette, prefs.couleursSysteme) { p, ancien ->
        when {
            p.isNotBlank() -> p
            ancien -> "systeme"
            else -> "sankai"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "systeme")

    val lectureAuto: StateFlow<Boolean> = prefs.lectureAuto
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val vitesseVoix: StateFlow<String> = prefs.vitesseVoix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "normale")

    val repetitionsVoix: StateFlow<Int> = prefs.repetitionsVoix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setLectureAuto(actif: Boolean) = viewModelScope.launch { prefs.setLectureAuto(actif) }
    fun setVitesseVoix(valeur: String) = viewModelScope.launch { prefs.setVitesseVoix(valeur) }
    fun setRepetitionsVoix(n: Int) = viewModelScope.launch { prefs.setRepetitionsVoix(n) }

    fun setPalette(valeur: String) = viewModelScope.launch {
        prefs.setPalette(valeur)
        // L'ancien reglage reste synchronise : d'autres endroits le lisent
        // encore, et deux sources de verite finiraient par diverger.
        prefs.setCouleursSysteme(valeur == "systeme")
    }
    fun setShowNavLabels(v: Boolean)       = viewModelScope.launch { prefs.setShowNavLabels(v) }
    fun setVibrations(v: Boolean)          = viewModelScope.launch { prefs.setVibrations(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch {
        prefs.setNotifications(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setBatterySaver(v: Boolean)        = viewModelScope.launch { prefs.setBatterySaver(v) }
    fun setMinimalMode(v: Boolean) = viewModelScope.launch {
        prefs.setMinimalMode(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setDailyMinutes(v: Int)            = viewModelScope.launch { prefs.setDailyMinutes(v) }
    fun setNotificationDailyMax(v: Int)    = viewModelScope.launch { prefs.setNotificationDailyMax(v) }
    fun setWeekendQuiet(v: Boolean)        = viewModelScope.launch { prefs.setWeekendQuiet(v) }
    fun setNotifyLearning(v: Boolean) = viewModelScope.launch {
        prefs.setNotifyLearning(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setNotifyMemo(v: Boolean) = viewModelScope.launch {
        prefs.setNotifyMemo(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setNotifyCulture(v: Boolean) = viewModelScope.launch {
        prefs.setNotifyCulture(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setNotifyFocus(v: Boolean)         = viewModelScope.launch { prefs.setNotifyFocus(v) }

    fun pauseNotifications(days: Long) = viewModelScope.launch {
        val until = java.time.LocalDate.now()
            .plusDays(days.coerceAtLeast(1L) - 1L)
            .toEpochDay()
        prefs.setNotificationPauseUntilEpochDay(until)
        NotificationCoordinator.reconcile(app)
    }

    fun resumeNotifications() = viewModelScope.launch {
        prefs.setNotificationPauseUntilEpochDay(0L)
        NotificationCoordinator.reconcile(app)
    }

    // ----- Heures silencieuses -------------------------------------------
    val quietEnabled: StateFlow<Boolean> = prefs.quietEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val quietStart: StateFlow<Int> = prefs.quietStartMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21 * 60)
    val quietEnd: StateFlow<Int> = prefs.quietEndMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9 * 60)

    // Changer une heure silencieuse invalide les alarmes déjà posées : il faut
    // reprogrammer, sinon un mémo tomberait en pleine plage silencieuse.
    fun setQuietEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setQuietEnabled(v)
        NotificationCoordinator.reconcile(app)
    }
    fun setQuietStart(minutes: Int) = viewModelScope.launch {
        prefs.setQuietStart(minutes)
        NotificationCoordinator.reconcile(app)
    }
    fun setQuietEnd(minutes: Int) = viewModelScope.launch {
        prefs.setQuietEnd(minutes)
        NotificationCoordinator.reconcile(app)
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
            _maj.value = EtatMaj(message = app.getString(R.string.settings_update_requires_internet))
            return@launch
        }
        _maj.value = EtatMaj(recherche = true, message = app.getString(R.string.settings_update_searching))

        _maj.value = when (val r = UpdateManager.rechercher()) {
            is UpdateManager.Resultat.AJour ->
                EtatMaj(message = app.getString(R.string.settings_update_current, r.versionActuelle))
            is UpdateManager.Resultat.Disponible ->
                EtatMaj(
                    disponible = r.maj,
                    message = app.getString(R.string.settings_update_available, r.maj.versionName)
                )
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
                message = app.getString(R.string.settings_update_allow_install)
            )
            return@launch
        }

        _maj.value = _maj.value.copy(
            telechargement = true,
            progression = 0f,
            message = app.getString(R.string.settings_update_downloading)
        )

        val erreur = UpdateManager.telechargerEtInstaller(context, maj) { p ->
            _maj.value = _maj.value.copy(progression = p)
        }

        _maj.value = if (erreur == null) {
            EtatMaj(message = app.getString(R.string.settings_update_install_started))
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

        val locale = app.resources.configuration.locales[0]
        val format = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
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
            app.getString(R.string.settings_test_notification_title),
            app.getString(R.string.settings_test_notification_body)
        )
    }

    fun reprogrammerTout(context: Context) = viewModelScope.launch {
        NotificationCoordinator.reconcile(context)
        rafraichirDiagnostic()
    }

    fun resetProgress() = viewModelScope.launch {
        app.database.withTransaction {
            val dao = app.database.userDao()
            val current = dao.getUserOnce()
                ?: com.sankailife.core.data.db.entities.UserEntity()
            dao.upsert(
                current.copy(
                    level = 1,
                    xp = 0,
                    xpNext = 200,
                    coins = 500,
                    gems = 5,
                    streakDays = 0,
                    lastLoginDate = "",
                    totalAdsWatched = 0,
                    totalChestsOpened = 0,
                    adCountToday = 0,
                    lastAdDate = "",
                    totalCoinsEarned = 0,
                    totalCoinsSpent = 0,
                    bestStreak = 0,
                    streakShields = 0,
                    lastDailyChestDay = ""
                )
            )
            app.database.chestDao().clearAll()
            app.database.challengeDao().clearAll()
            app.database.arenaRewardDao().toutEffacer()
            app.database.statsDao().clearAll()
        }
        NotificationCoordinator.reconcile(app)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { SettingsViewModel(app) } }
    }
}
