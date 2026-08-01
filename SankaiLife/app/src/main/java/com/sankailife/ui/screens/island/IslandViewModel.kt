package com.sankailife.ui.screens.island

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.island.data.IslandRepository
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.core.island.domain.IslandTileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IslandViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val depot = IslandRepository(app.database, userRepo)

    data class Etat(
        val chargement: Boolean = true,
        val ile: IslandGenerator.Ile? = null,
        /** Message d'erreur affichable, vide s'il n'y en a pas. */
        val erreur: String = ""
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    /** Clés des parcelles achetées, pour les distinguer du terrain naturel. */
    val parcelles: StateFlow<Set<Int>> = depot.observerParcelles()
        .map { liste -> liste.map { it.cle }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val utilisateur = userRepo.userFlow
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.sankailife.core.domain.model.UserState()
        )

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    /** Jeton de recentrage, incrémenté à chaque demande. */
    private val _recentrage = MutableStateFlow(0L)
    val recentrage: StateFlow<Long> = _recentrage.asStateFlow()

    init {
        charger()
    }

    /**
     * Charge l'île, en la générant si le joueur n'en a pas.
     *
     * La génération est faite ici et non au démarrage de l'application : elle
     * prend quelques dizaines de millisecondes et n'a aucune raison de retarder
     * l'écran d'accueil de quelqu'un qui ne va pas au Jardin.
     */
    fun charger() {
        viewModelScope.launch {
            _etat.value = Etat(chargement = true)
            runCatching { depot.creerSiAbsente() }
                .onSuccess { _etat.value = Etat(chargement = false, ile = it) }
                .onFailure {
                    _etat.value = Etat(
                        chargement = false,
                        erreur = it.message ?: "L'île n'a pas pu être chargée."
                    )
                }
        }
    }

    fun definirZoom(valeur: Float) {
        _zoom.value = valeur.coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    fun recentrer() {
        _recentrage.value = _recentrage.value + 1
    }

    /**
     * Achète la parcelle touchée, et dit ce qui s'est passé.
     *
     * Le message vient du dépôt, qui le tient du moteur : l'écran ne réécrit
     * jamais la raison d'un refus. Deux formulations pour la même règle
     * finiraient par se contredire.
     */
    fun acheter(x: Int, y: Int) {
        viewModelScope.launch {
            when (val resultat = depot.acheterParcelle(x, y)) {
                is IslandRepository.Achat.Reussi ->
                    _message.value = if (resultat.prixPaye == 0) {
                        "Parcelle offerte."
                    } else {
                        "Parcelle achetée pour ${resultat.prixPaye} pièces."
                    }

                is IslandRepository.Achat.Refuse -> _message.value = resultat.raison
            }
        }
    }

    fun messageAffiche() {
        _message.value = ""
    }

    /** Prix affiché pour une case, ou `null` si elle n'est pas à vendre. */
    fun prixAffiche(type: IslandTileType, possedees: Int): Int? =
        if (IslandSlotEngine.terrainAchetable(type)) {
            IslandSlotEngine.prix(type, possedees)
        } else null

    companion object {
        // Zoom minimum : voir l'île entière sur un écran de téléphone.
        const val ZOOM_MIN = 0.5f

        // Zoom maximum : environ trois cases de large, comme demandé.
        const val ZOOM_MAX = 3.5f

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { IslandViewModel(app) }
        }
    }
}
