package com.sankailife.ui.screens.profile

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.MemorisationEngine
import com.sankailife.core.domain.engine.RegularityEngine
import com.sankailife.core.domain.model.ALL_THEMES
import com.sankailife.core.domain.model.UserState
import com.sankailife.core.time.observedMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
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

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val rawUser: StateFlow<UserEntity?> = app.database.userDao().getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val minimalMode: StateFlow<Boolean> = app.preferences.minimalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dailyMinutes: StateFlow<Int> = app.preferences.dailyMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    /**
     * L'état de la mémorisation, tous modules confondus.
     *
     * Les statistiques ne parlaient jusqu'ici que du jeu — XP, pièces, coffres.
     * Dans une application dont le but est d'apprendre, c'est la moitié qui
     * manquait.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val memorisation: StateFlow<MemorisationEngine.Etat> = observedTime
        .flatMapLatest { tick ->
            app.database.memoDao().statsMemorisation(
                tick.epochMillis,
                MemorisationEngine.BOITE_MAITRISEE
            )
        }
        .map {
            MemorisationEngine.Etat(
                total = it.total,
                maitrisees = it.maitrisees,
                dues = it.dues,
                revisions = it.revisions,
                reussites = it.reussites,
                entamees = it.entamees
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemorisationEngine.Etat())

    data class Regularite(val sept: Int = 0, val trente: Int = 0, val quatreVingtDix: Int = 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val regularite: StateFlow<Regularite> = observedDay
        .flatMapLatest { today ->
            // Une seule observation Room couvre les trois fenêtres. Elle se
            // réabonne au changement de jour afin de faire glisser la borne,
            // même quand le ViewModel reste vivant pendant plusieurs jours.
            app.database.dayRecordDao()
                .getDepuis(today.minusDays(89).toString())
                .map { records -> today to records }
        }
        .map { (today, records) ->
            fun percentage(days: Int): Int =
                (RegularityEngine.regularite(records, days, today) * 100).toInt()

            Regularite(
                sept = percentage(7),
                trente = percentage(30),
                quatreVingtDix = percentage(90)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Regularite())

    /**
     * Nom du thème équipé, pour la carte résumé.
     * La collection complète et l'équipement vivent dans CustomizationViewModel :
     * le profil n'affiche plus qu'un aperçu.
     */
    val nomThemeEquipe: StateFlow<String> = rawUser
        .map { e ->
            val id = e?.equippedThemeId ?: "default"
            ALL_THEMES.firstOrNull { it.id == id }?.name ?: "Default Or"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default Or")

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ProfileViewModel(app) } }
    }
}
