package com.sankailife.ui.screens.academie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.learning.data.LearningModuleEntity
import com.sankailife.core.learning.data.LearningRepository
import com.sankailife.core.learning.domain.AcademieEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * La recommandation de l'onglet Apprendre.
 *
 * Son travail est réduit à une seule chose : savoir où l'on en est et le dire.
 * La bibliothèque elle-même (modules, statistiques, dossiers, suppression)
 * vit dans MemoViewModel ; ici, on calcule la « suite » du parcours en cours,
 * que la carte-dossier affiche sous forme de « Continuer → ».
 */
class AcademieViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
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
        /** La suite du parcours en cours, prête à reprendre. */
        val suite: Suite? = null
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun messageAffiche() { _message.value = "" }

    init {
        charger()
    }

    /**
     * Recompose la recommandation.
     *
     * Appelée à l'ouverture et au retour d'une session : la suite dépend des
     * boîtes de Leitner, qui viennent de changer.
     */
    fun charger() {
        viewModelScope.launch {
            _etat.value = _etat.value.copy(chargement = true)
            runCatching {
                depot.purger()

                val profils = memoDao.getAllProfilesOnce()
                val avecCartes = profils.map { it to memoDao.getLinesOnce(it.id) }
                    .filter { (_, lignes) -> lignes.isNotEmpty() }

                // Le premier module qui a encore quelque chose à faire, dans
                // l'ordre des profils. Prévisible, et c'est la qualité qu'on
                // cherche ici : une recommandation qui saute d'un module à
                // l'autre sans raison visible se lit comme un tirage au sort.
                val suite = avecCartes
                    .mapNotNull { (profil, _) -> depot.moduleDuProfil(profil.id) }
                    .firstNotNullOfOrNull { module -> suitePour(module) }

                Etat(chargement = false, suite = suite)
            }.onSuccess { _etat.value = it }
                .onFailure {
                    _etat.value = _etat.value.copy(chargement = false)
                    _message.value = getApplication<android.app.Application>()
                        .getString(R.string.academy_load_error)
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
