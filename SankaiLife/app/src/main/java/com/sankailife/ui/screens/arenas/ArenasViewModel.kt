package com.sankailife.ui.screens.arenas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.ALL_ARENAS
import com.sankailife.core.domain.model.Arena
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArenasViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val gameRepo = GameRepository(app.database, app)
    private val arenaDao = app.database.arenaRewardDao()

    /**
     * État de progression d'une arène.
     *
     * Séparé du fait qu'une récompense soit réclamable : l'arène actuelle peut
     * très bien avoir déjà donné son lot, et une arène ancienne peut encore
     * avoir une récompense en attente si le joueur ne l'a pas prise.
     */
    enum class EtatArene { TERMINEE, ACTUELLE, DEBLOQUEE, VERROUILLEE }

    data class LigneArene(
        val arene: Arena,
        val etat: EtatArene,
        val recompenseDisponible: Boolean,
        val recompenseReclamee: Boolean
    ) {
        val estVerrouillee: Boolean get() = etat == EtatArene.VERROUILLEE
        val estCourante: Boolean get() = etat == EtatArene.ACTUELLE
    }

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    private val reclamees: StateFlow<Set<Int>> = arenaDao.getReclamees()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val parcours: StateFlow<List<LigneArene>> =
        combine(user, reclamees) { u, prises ->
            val courante = ArenaEngine.areneActuelle(u.level)
            ALL_ARENAS.map { arene ->
                val atteinte = ArenaEngine.estAtteinte(arene, u.level)
                val reclamee = arene.id in prises
                LigneArene(
                    arene = arene,
                    etat = when {
                        !atteinte -> EtatArene.VERROUILLEE
                        arene.id == courante.id -> EtatArene.ACTUELLE
                        reclamee -> EtatArene.TERMINEE
                        else -> EtatArene.DEBLOQUEE
                    },
                    recompenseDisponible = atteinte && !reclamee,
                    recompenseReclamee = reclamee
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nombreAReclamer: StateFlow<Int> = parcours
        .map { liste -> liste.count { it.recompenseDisponible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    /** Index de l'arène courante, pour recentrer le parcours à l'ouverture. */
    fun indexArenCourante(niveau: Int): Int =
        ALL_ARENAS.indexOfFirst { it.id == ArenaEngine.areneActuelle(niveau).id }.coerceAtLeast(0)

    fun reclamer(arene: Arena) = viewModelScope.launch {
        val u = user.value
        if (!ArenaEngine.estAtteinte(arene, u.level)) return@launch
        when (gameRepo.reclamerArene(arene)) {
            GameRepository.ReclamationArene.Reussie ->
                afficherToast("${arene.emoji} ${arene.nom} • ${arene.recompense.resume()}")
            GameRepository.ReclamationArene.CoffresPleins ->
                afficherToast("Libère un emplacement de coffre avant de réclamer")
            null -> Unit
        }
    }

    private fun afficherToast(message: String) = viewModelScope.launch {
        _toast.value = message
        delay(3000)
        _toast.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ArenasViewModel(app) }
        }
    }
}
