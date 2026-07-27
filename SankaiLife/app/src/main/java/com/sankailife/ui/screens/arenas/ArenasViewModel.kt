package com.sankailife.ui.screens.arenas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.ArenaRewardEntity
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
    private val gameRepo = GameRepository(app.database)
    private val arenaDao = app.database.arenaRewardDao()

    /** État d'une arène tel que l'affiche le parcours. */
    enum class EtatArene { RECLAMEE, A_RECLAMER, ATTEINTE, VERROUILLEE }

    data class LigneArene(
        val arene: Arena,
        val etat: EtatArene,
        val estCourante: Boolean
    )

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
                LigneArene(
                    arene = arene,
                    etat = when {
                        arene.id in prises -> EtatArene.RECLAMEE
                        atteinte -> EtatArene.A_RECLAMER
                        else -> EtatArene.VERROUILLEE
                    },
                    estCourante = arene.id == courante.id
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nombreAReclamer: StateFlow<Int> = parcours
        .map { liste -> liste.count { it.etat == EtatArene.A_RECLAMER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    /** Index de l'arène courante, pour recentrer le parcours à l'ouverture. */
    fun indexArenCourante(niveau: Int): Int =
        ALL_ARENAS.indexOfFirst { it.id == ArenaEngine.areneActuelle(niveau).id }.coerceAtLeast(0)

    fun reclamer(arene: Arena) = viewModelScope.launch {
        val u = user.value
        if (!ArenaEngine.estAtteinte(arene, u.level)) return@launch

        // L'insertion sert de verrou : la clé primaire est l'identifiant de
        // l'arène, donc un double appui n'insère rien la seconde fois et ne
        // peut pas créditer deux fois.
        val insere = arenaDao.marquerReclamee(
            ArenaRewardEntity(arenaId = arene.id, claimedAt = System.currentTimeMillis())
        )
        if (insere == -1L) return@launch

        val r = arene.recompense
        if (r.coins > 0) userRepo.addCoins(r.coins)
        if (r.gems > 0) userRepo.addGems(r.gems)
        if (r.chestType.isNotBlank()) gameRepo.addChest(r.chestType)
        if (r.moduleSlots > 0) {
            val actuel = app.database.userDao().getUserOnce()
            if (actuel != null) {
                app.database.userDao().updateModuleSlots(actuel.moduleSlots + r.moduleSlots)
            }
        }

        afficherToast("${arene.emoji} ${arene.nom} • ${r.resume()}")
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
