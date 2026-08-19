package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.culture.CultureLocalState
import com.sankailife.core.culture.DailyCultureEntry
import com.sankailife.core.culture.DailyDiscovery
import com.sankailife.core.motdujour.MotDuJour
import com.sankailife.core.motdujour.MotDuJourSelector
import com.sankailife.core.motdujour.MotDuJourStore
import com.sankailife.core.poesie.PoesieDuJour
import com.sankailife.core.poesie.PoesieDuJourSelector
import com.sankailife.core.poesie.PoesieDuJourStore
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.learning.AvailableLearningLanguages
import com.sankailife.core.domain.model.UserState
import com.sankailife.core.learning.data.LearningModuleEntity
import com.sankailife.core.learning.data.LearningRepository
import com.sankailife.core.learning.domain.AcademieEngine
import com.sankailife.core.time.observedMinutes
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    /** Ce qu'il faut continuer : un module, l'unité en cours, la progression. */
    data class Suite(
        val module: LearningModuleEntity,
        val unite: AcademieEngine.Unite,
        val progression: Float,
        val resume: String
    )

    private val _suite = MutableStateFlow<Suite?>(null)
    val suite: StateFlow<Suite?> = _suite.asStateFlow()

    /** La capsule du jour, la même que l'écran Culture et la notification. */
    private val _decouverte = MutableStateFlow<DailyCultureEntry?>(null)
    val decouverte: StateFlow<DailyCultureEntry?> = _decouverte.asStateFlow()

    /**
     * Le catalogue du mot du jour, lu depuis l'asset embarqué, puis filtré
     * sur les langues réellement disponibles chez l'utilisateur — la même
     * source unique que l'écran et la notification.
     */
    private val motsDuJour: StateFlow<List<MotDuJour>> = flow {
        val langues = AvailableLearningLanguages.pour(app.database)
        emit(MotDuJourStore.lire(app).filter { it.codeLangue in langues })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Le mot d'aujourd'hui, stable toute la journée, sans réseau. */
    val motDuJour: StateFlow<MotDuJour?> = motsDuJour
        .map { MotDuJourSelector.selectionner(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Le mot de demain, pour la ligne « prochaine découverte ». */
    val motDemain: StateFlow<MotDuJour?> = motsDuJour
        .map { MotDuJourSelector.suivant(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Le catalogue des découvertes littéraires, lu depuis l'asset embarqué. */
    private val textesPoesie: StateFlow<List<PoesieDuJour>> = flow {
        emit(PoesieDuJourStore.lire(app))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Le proverbe ou le poème d'aujourd'hui, stable toute la journée. */
    val poesieDuJour: StateFlow<PoesieDuJour?> = textesPoesie
        .map { PoesieDuJourSelector.selectionner(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // L'accueil initialise seulement le profil. Il ne crée plus en
        // arrière-plan de coffre, défi ou récompense à réclamer.
        viewModelScope.launch { userRepository.ensureUser() }
    }

    /**
     * Relit le module en cours et la capsule du jour.
     *
     * Appelé à chaque ouverture de l'écran : la progression vient de changer
     * après une session, et la découverte du jour peut avoir été consultée.
     */
    fun rafraichir() {
        viewModelScope.launch {
            runCatching {
                val depot = LearningRepository(app.database)
                val maintenant = System.currentTimeMillis()
                val profils = app.database.memoDao().getAllProfilesOnce()
                val avecCartes = profils.map { it to app.database.memoDao().getLinesOnce(it.id) }
                    .filter { (_, lignes) -> lignes.isNotEmpty() }
                _suite.value = avecCartes
                    .mapNotNull { (profil, _) -> depot.moduleDuProfil(profil.id) }
                    .firstNotNullOfOrNull { module -> suitePour(depot, module) }

                val local = CultureLocalState(app)
                val userId = app.database.userDao().getUserOnce()?.id ?: 1L
                val profileId = "user-$userId"
                _decouverte.value = DailyDiscovery.duJour(
                    app,
                    profileId = profileId,
                    history = local.history(profileId),
                    enabledLanguages = AvailableLearningLanguages.pour(app.database)
                )
            }
        }
    }

    private suspend fun suitePour(
        depot: LearningRepository,
        module: LearningModuleEntity
    ): Suite? {
        val parcours = depot.parcours(module)
        val unite = AcademieEngine.aContinuer(parcours) ?: return null
        val noeud = parcours.first { it.unite.id == unite.id }
        val plan = depot.preparerSession(module, unite.id) ?: return null
        if (plan.vide) return null
        return Suite(
            module = module,
            unite = unite,
            progression = noeud.progression,
            resume = com.sankailife.core.learning.domain.SessionPlanEngine.resume(plan)
        )
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
