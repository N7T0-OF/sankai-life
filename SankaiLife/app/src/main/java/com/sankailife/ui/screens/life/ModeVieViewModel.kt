package com.sankailife.ui.screens.life

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.calendar.CalendrierIntegration
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ProgressSourceEngine
import com.sankailife.core.time.observedMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * La section Vie : ce qui accompagne la vraie vie, sans la recréer.
 *
 * Le calendrier Android fait l'action (l'utilisateur vit ses événements),
 * Sankai la transforme en progression symbolique : un événement terminé est
 * crédité une fois par jour, plafonné et dégressif via [ProgressSourceEngine].
 * Lecture seule, traitement local, jamais une modification du calendrier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeVieViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)

    /** Une source de progression et ce qu'elle a rapporté aujourd'hui. */
    data class Activite(
        val source: ProgressSourceEngine.Source,
        val xp: Int
    )

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

    /** L'XP d'aujourd'hui, toutes sources confondues — jamais du temps passé. */
    val xpTotalJour: StateFlow<Int> = observedDay
        .flatMapLatest { jour -> app.preferences.xpSourceTotalJour(jour.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Les activités réellement créditées aujourd'hui, avec leur XP. */
    val activites: StateFlow<List<Activite>> = observedDay
        .flatMapLatest { jour ->
            flow {
                val auj = jour.toString()
                emit(
                    ProgressSourceEngine.Source.entries.map { source ->
                        Activite(source, app.preferences.xpAccordeSource(source.name, auj))
                    }.filter { it.xp > 0 }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Etat(
        val permissionAccordee: Boolean = false,
        /** Événements terminés aujourd'hui, tous (déjà crédités ou non). */
        val evenementsAujourdhui: Int = 0,
        /** XP crédité aujourd'hui par la source Calendrier, toutes occurrences. */
        val xpCalendrier: Int = 0,
        /** Gain de la dernière synchronisation, pour une ligne de retour. */
        val dernierGain: Int = 0,
        val chargement: Boolean = false
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    /**
     * Relit le calendrier et crédite les événements terminés non encore
     * crédités aujourd'hui.
     *
     * Appelé à chaque ouverture de l'écran et après l'autorisation de
     * lecture. Un événement ne rapporte qu'une fois par jour ; l'anti-farm
     * plafonne la source quoi qu'il arrive.
     */
    fun rafraichir() {
        val permission = CalendrierIntegration.permissionAccordee(app)
        if (!permission) {
            _etat.value = Etat(permissionAccordee = false)
            return
        }
        _etat.value = _etat.value.copy(permissionAccordee = true, chargement = true)
        viewModelScope.launch {
            runCatching {
                val aujourdhui = LocalDate.now().toString()
                val evenements = CalendrierIntegration.evenementsTerminesAujourdhui(app)
                val deja = app.preferences.calendrierDejaCredites(aujourdhui)
                val aCrediter = CalendrierIntegration.aCrediter(deja, evenements)

                var gain = 0
                aCrediter.forEach {
                    gain += userRepo.addSourceXp(
                        ProgressSourceEngine.Source.CALENDRIER,
                        app.preferences
                    )
                }
                if (aCrediter.isNotEmpty()) {
                    app.preferences.calendrierCrediter(
                        aujourdhui,
                        aCrediter.map { e -> e.id }.toSet()
                    )
                }

                Etat(
                    permissionAccordee = true,
                    evenementsAujourdhui = evenements.size,
                    xpCalendrier = app.preferences.xpAccordeSource(
                        ProgressSourceEngine.Source.CALENDRIER.name,
                        aujourdhui
                    ),
                    dernierGain = gain,
                    chargement = false
                )
            }.onSuccess { etat ->
                _etat.value = etat
            }.onFailure {
                _etat.value = _etat.value.copy(chargement = false)
            }
        }
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ModeVieViewModel(app) }
        }
    }
}
