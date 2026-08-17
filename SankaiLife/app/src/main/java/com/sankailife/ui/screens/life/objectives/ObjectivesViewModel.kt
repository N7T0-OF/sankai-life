package com.sankailife.ui.screens.life.objectives

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ObjectiveEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Objectifs personnels : une simple checklist, entièrement locale.
 *
 * Cocher un objectif ne rapporte ni XP ni pièces : l'objectif est une liste
 * de choses à faire, pas une machine à récompenses.
 */
class ObjectivesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val objectiveDao = app.database.objectiveDao()

    val objectives: StateFlow<List<ObjectiveEntity>> = objectiveDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }

    fun supprimer(objectif: ObjectiveEntity) = viewModelScope.launch {
        objectiveDao.delete(objectif)
    }

    fun effacerTermines() = viewModelScope.launch {
        objectiveDao.clearCompleted()
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ObjectivesViewModel(app) }
        }
    }
}
