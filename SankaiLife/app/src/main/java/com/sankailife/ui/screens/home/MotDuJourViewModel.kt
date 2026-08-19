package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.learning.AvailableLearningLanguages
import com.sankailife.core.motdujour.MotDuJour
import com.sankailife.core.motdujour.MotDuJourSelector
import com.sankailife.core.motdujour.MotDuJourStore
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MotDuJourViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication

    /**
     * Le catalogue complet, lu une fois depuis l'asset embarqué, puis filtré
     * sur les langues réellement disponibles chez l'utilisateur : un mot en
     * japonais pour quelqu'un qui n'apprend que le français et le portugais
     * serait un contenu hors de propos.
     */
    private val catalogue: StateFlow<List<MotDuJour>> = flow {
        emit(lireCatalogueDisponible())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun lireCatalogueDisponible(): List<MotDuJour> {
        val langues = AvailableLearningLanguages.pour(app.database)
        return MotDuJourStore.lire(app).filter { it.codeLangue in langues }
    }

    /** Le mot d'aujourd'hui — stable toute la journée, sans réseau. */
    val motDuJour: StateFlow<MotDuJour?> = catalogue
        .map { MotDuJourSelector.selectionner(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Le mot de demain, pour la ligne « prochaine découverte ». */
    val motDemain: StateFlow<MotDuJour?> = catalogue
        .map { MotDuJourSelector.suivant(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoris: StateFlow<Set<String>> = app.preferences.motDuJourFavoris
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun basculerFavori(id: String) = viewModelScope.launch {
        app.preferences.basculerMotDuJourFavori(id)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { MotDuJourViewModel(app) }
        }
    }
}
