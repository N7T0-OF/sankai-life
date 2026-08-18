package com.sankailife.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.poesie.PoesieDuJour
import com.sankailife.core.poesie.PoesieDuJourSelector
import com.sankailife.core.poesie.PoesieDuJourStore
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PoesieDuJourViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication

    /** Le catalogue complet, lu une fois depuis l'asset embarqué. */
    private val catalogue: StateFlow<List<PoesieDuJour>> = flow {
        emit(PoesieDuJourStore.lire(app))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** La découverte d'aujourd'hui — stable toute la journée, sans réseau. */
    val poesieDuJour: StateFlow<PoesieDuJour?> = catalogue
        .map { PoesieDuJourSelector.selectionner(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** La découverte de demain, pour la ligne « prochaine ». */
    val poesieDemain: StateFlow<PoesieDuJour?> = catalogue
        .map { PoesieDuJourSelector.suivant(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { PoesieDuJourViewModel(app) }
        }
    }
}
