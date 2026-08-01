package com.sankailife.ui.screens.life.objectives

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ObjectiveEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.XpEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Objectifs personnels : une simple checklist, entièrement locale.
 *
 * Cocher un objectif rapporte de l'XP et des pièces une seule fois. Le
 * décocher ne reprend rien : on ne punit pas quelqu'un qui corrige une erreur
 * de manipulation, et l'`completedAt` garde la trace du premier passage à
 * « fait » pour empêcher de farmer en cochant/décochant.
 */
class ObjectivesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val objectiveDao = app.database.objectiveDao()
    private val userRepo = UserRepository(app.database)
    private val gameRepo = GameRepository(app.database, app)

    /** Récompenses d'un objectif terminé, alignées sur XpEngine. */
    private val piecesParObjectif = 25

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val objectives: StateFlow<List<ObjectiveEntity>> = objectiveDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    private val _showLevelUp = MutableStateFlow(false)
    val showLevelUp: StateFlow<Boolean> = _showLevelUp

    fun ajouter(texte: String) = viewModelScope.launch {
        val propre = texte.trim()
        if (propre.isEmpty()) return@launch
        objectiveDao.upsert(ObjectiveEntity(text = propre))
    }

    fun basculer(objectif: ObjectiveEntity) = viewModelScope.launch {
        val desormaisFait = !objectif.isDone
        objectiveDao.setDone(
            id = objectif.id,
            done = desormaisFait,
            completedAt = if (desormaisFait) System.currentTimeMillis() else objectif.completedAt
        )

        // Récompense uniquement au tout premier passage à « fait ».
        val premiereFois = desormaisFait && objectif.completedAt == 0L
        if (!premiereFois) return@launch

        userRepo.addCoins(piecesParObjectif)
        gameRepo.updateChallengeProgress("daily_obj", 1)
        val monteeDeNiveau = userRepo.addXp(XpEngine.XP_OBJECTIVE_DONE)
        afficherToast("+${XpEngine.XP_OBJECTIVE_DONE} XP • +$piecesParObjectif 🪙")
        if (monteeDeNiveau) _showLevelUp.value = true
    }

    fun supprimer(objectif: ObjectiveEntity) = viewModelScope.launch {
        objectiveDao.delete(objectif)
    }

    fun effacerTermines() = viewModelScope.launch {
        objectiveDao.clearCompleted()
    }

    fun masquerLevelUp() { _showLevelUp.value = false }

    private fun afficherToast(message: String) = viewModelScope.launch {
        _toast.value = message
        delay(2500)
        _toast.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ObjectivesViewModel(app) }
        }
    }
}
