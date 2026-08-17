package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.model.UserState
import com.sankailife.core.time.observedMinutes
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepository = UserRepository(app.database)
    private val observedTime = observedMinutes().shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000,
            replayExpirationMillis = 0
        ),
        replay = 1
    )
    private val observedDay = observedTime
        .map { it.localDate }
        .distinctUntilChanged()

    val user: StateFlow<UserState> = userRepository.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserState())

    /** Travail réellement utile disponible aujourd'hui, sans urgence fabriquée. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dueCards: StateFlow<Int> = observedTime
        .flatMapLatest { tick ->
            // Le paramètre temporel d'une requête Room est immuable : la
            // réabonner chaque minute rend visibles les cartes qui arrivent à
            // échéance même si aucune ligne de la base n'a changé.
            app.database.memoDao().compterToutesCartesDues(tick.epochMillis)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val nextMemo: StateFlow<MemoProfileEntity?> = combine(
        app.database.memoDao().getAllProfiles(),
        observedTime
    ) { profiles, tick ->
        // Un horaire expiré n'est jamais présenté comme le « prochain »
        // rappel. Le planificateur pourra publier sa nouvelle échéance via
        // Room ; entre-temps l'accueil affiche qu'aucun rappel n'est prévu.
        profiles.asSequence()
            .filter { it.isActive && it.nextTriggerAtMillis > tick.epochMillis }
            .minByOrNull { it.nextTriggerAtMillis }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val minimalMode: StateFlow<Boolean> = app.preferences.minimalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val dailyMinutes: StateFlow<Int> = app.preferences.dailyMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    val todayCompleted: StateFlow<Boolean> = combine(
        app.preferences.todayCompletedDate,
        observedDay
    ) { completedDate, today -> completedDate == today.toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * L'XP gagné aujourd'hui, toutes sources confondues.
     *
     * Mis à jour par les occurrences réelles (révisions, concentration,
     * découverte) — jamais par le simple fait d'ouvrir l'application. C'est
     * le chiffre que montre l'Accueil : ce que tu as fait, pas combien de
     * temps tu as passé dans Sankai.
     */
    val xpDuJour: StateFlow<Int> = observedDay
        .flatMapLatest { jour -> app.preferences.xpSourceTotalJour(jour.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // L'accueil initialise seulement le profil. Il ne crée plus en
        // arrière-plan de coffre, défi ou récompense à réclamer.
        viewModelScope.launch { userRepository.ensureUser() }
    }

    fun finishToday(onSaved: () -> Unit = {}) = viewModelScope.launch {
        app.preferences.setTodayCompletedDate(LocalDate.now().toString())
        onSaved()
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { HomeViewModel(app) }
        }
    }
}
