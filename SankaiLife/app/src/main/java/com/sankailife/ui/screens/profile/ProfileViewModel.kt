package com.sankailife.ui.screens.profile

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.ALL_THEMES
import com.sankailife.core.domain.model.Theme
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val rawUser: StateFlow<UserEntity?> = app.database.userDao().getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Récompenses d'arène atteintes mais pas encore prises.
     * Sert la pastille de la carte de progression : une récompense en attente
     * doit se voir sans ouvrir le parcours.
     */
    val arenesAReclamer: StateFlow<Int> =
        combine(user, app.database.arenaRewardDao().getReclamees()) { u, prises ->
            ArenaEngine.recompensesAReclamer(u.level, prises.toSet()).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    data class Regularite(val sept: Int = 0, val trente: Int = 0, val quatreVingtDix: Int = 0)

    private val _regularite = MutableStateFlow(Regularite())
    val regularite: StateFlow<Regularite> = _regularite

    init {
        // Recalculé à l'ouverture du profil : trois lectures ponctuelles
        // suffisent, inutile d'observer la table en continu.
        viewModelScope.launch {
            _regularite.value = Regularite(
                sept = userRepo.regularite(7),
                trente = userRepo.regularite(30),
                quatreVingtDix = userRepo.regularite(90)
            )
        }
    }

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
