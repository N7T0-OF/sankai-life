package com.sankailife.ui.screens.life

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.data.repository.MemoActivationRepository
import com.sankailife.core.notifications.MemoAlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LifeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    private val memoDao = app.database.memoDao()
    private val memoActivation = MemoActivationRepository(app.database)

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    val user = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.sankailife.core.domain.model.UserState())

    val memoProfiles: StateFlow<List<MemoProfileEntity>> = memoDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val objectivesPending: StateFlow<Int> = app.database.objectiveDao().countPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleMemo(profileId: Long, active: Boolean) = viewModelScope.launch {
        when (val resultat = memoActivation.definirActif(profileId, active)) {
            MemoActivationRepository.Resultat.MisAJour ->
                runCatching { MemoAlarmScheduler.replanifierTout(app) }
            is MemoActivationRepository.Resultat.LimiteAtteinte ->
                afficherMessage("${resultat.slots} slot(s) actif(s) maximum — achète un slot pour en activer un autre")
            MemoActivationRepository.Resultat.ProfilIntrouvable ->
                afficherMessage("Ce profil mémo n'existe plus")
        }
    }

    fun buyModuleSlot() = viewModelScope.launch {
        val achat = userRepo.acheterSlotModule()
        if (achat == null) afficherMessage("Pièces insuffisantes")
        else afficherMessage("+1 slot module • ${achat.totalSlots} au total")
    }

    fun messageAffiche() { _message.value = "" }

    private fun afficherMessage(texte: String) { _message.value = texte }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { LifeViewModel(app) } }
    }
}
