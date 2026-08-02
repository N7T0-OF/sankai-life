package com.sankailife.ui.screens.academie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.learning.data.LearningModuleEntity
import com.sankailife.core.learning.data.LearningRepository
import com.sankailife.core.learning.domain.AcademieEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * L'accueil de l'Académie.
 *
 * Son travail principal est de **réduire le choix à une action**. L'ancien
 * écran affichait Focus, objectifs, mémos, slots et boutique côte à côte, tous
 * de même importance : on savait ce qu'on pouvait faire, jamais ce qu'on
 * devait faire. Ici, une seule recommandation passe devant, et le reste
 * attend en dessous.
 */
class AcademieViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val depot = LearningRepository(app.database)
    private val memoDao = app.database.memoDao()

    /** Une recommandation prête à lancer, ou de quoi expliquer pourquoi il n'y en a pas. */
    data class Suite(
        val module: LearningModuleEntity,
        val unite: AcademieEngine.Unite,
        val progression: Float,
        val minutes: Int,
        val resume: String
    )

    data class Etat(
        val chargement: Boolean = true,
        val suite: Suite? = null,
        /** Profils Mémo qui peuvent servir de module, avec leur nombre de cartes. */
        val modulesDisponibles: List<Pair<MemoProfileEntity, Int>> = emptyList(),
        val cartesDues: Int = 0,
        val joursActifs: Int = 0
    ) {
        /** Rien à apprendre : ni contenu, ni révision. */
        val vide: Boolean get() = suite == null && modulesDisponibles.isEmpty()
    }

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun messageAffiche() { _message.value = "" }

    val utilisateur = userRepo.userFlow
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.sankailife.core.domain.model.UserState()
        )

    init {
        charger()
    }

    /**
     * Recompose l'accueil.
     *
     * Appelée à l'ouverture et au retour d'une session : le parcours dépend des
     * boîtes de Leitner, qui viennent de changer.
     */
    fun charger() {
        viewModelScope.launch {
            _etat.value = _etat.value.copy(chargement = true)
            runCatching {
                depot.purger()

                val maintenant = System.currentTimeMillis()
                val profils = memoDao.getAllProfilesOnce()
                val avecCartes = profils.map { it to memoDao.getLinesOnce(it.id) }
                    .filter { (_, lignes) -> lignes.isNotEmpty() }

                val dues = avecCartes.sumOf { (_, lignes) ->
                    lignes.count { it.nextReviewAtMillis <= maintenant }
                }

                // Le premier module qui a encore quelque chose à faire, dans
                // l'ordre des profils. Prévisible, et c'est la qualité qu'on
                // cherche ici : une recommandation qui saute d'un module à
                // l'autre sans raison visible se lit comme un tirage au sort.
                val suite = avecCartes
                    .mapNotNull { (profil, _) -> depot.moduleDuProfil(profil.id) }
                    .firstNotNullOfOrNull { module -> suitePour(module) }

                val semaine = maintenant - TimeUnit.DAYS.toMillis(7)
                Etat(
                    chargement = false,
                    suite = suite,
                    modulesDisponibles = avecCartes.map { (p, l) -> p to l.size },
                    cartesDues = dues,
                    joursActifs = depot.joursActifs(semaine).first()
                )
            }.onSuccess { _etat.value = it }
                .onFailure {
                    _etat.value = _etat.value.copy(chargement = false)
                    _message.value = "Impossible de charger l'Académie."
                }
        }
    }

    private suspend fun suitePour(module: LearningModuleEntity): Suite? {
        val parcours = depot.parcours(module)
        val unite = AcademieEngine.aContinuer(parcours) ?: return null
        val noeud = parcours.first { it.unite.id == unite.id }
        val plan = depot.preparerSession(module, unite.id) ?: return null
        if (plan.vide) return null
        return Suite(
            module = module,
            unite = unite,
            progression = noeud.progression,
            minutes = plan.minutesEstimees,
            resume = com.sankailife.core.learning.domain.SessionPlanEngine.resume(plan)
        )
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { AcademieViewModel(app) }
        }
    }
}
