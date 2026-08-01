package com.sankailife.ui.screens.island

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.island.data.IslandRepository
import com.sankailife.core.island.data.IslandBuildingEntity
import com.sankailife.core.island.data.IslandSlotEntity
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.core.island.domain.IslandTileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IslandViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val depot = IslandRepository(app.database, userRepo)

    /** Une île proposée au choix, avec de quoi la comparer aux autres. */
    data class Candidate(
        val ile: IslandGenerator.Ile,
        val cultivables: Int,
        val boise: Int,
        val rivieres: Int
    )

    data class Etat(
        val chargement: Boolean = true,
        val ile: IslandGenerator.Ile? = null,
        /** Non vide tant que le joueur n'a pas choisi son île. */
        val candidates: List<Candidate> = emptyList(),
        val choisie: Int = 0,
        val nom: String = "",
        /** Régénérations encore possibles avant de devoir choisir. */
        val relancesRestantes: Int = RELANCES,
        /** Message d'erreur affichable, vide s'il n'y en a pas. */
        val erreur: String = ""
    ) {
        val enAssistant: Boolean get() = ile == null && candidates.isNotEmpty()
    }

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    /** Parcelles achetées, indexées par clé de grille. */
    val parcelles: StateFlow<Map<Int, IslandSlotEntity>> = depot.observerParcelles()
        .map { liste -> liste.associateBy { it.cle } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Bâtiments posés, pour le rendu et pour la bulle. */
    val batiments: StateFlow<List<IslandBuildingEntity>> = depot.observerBatiments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Case ouverte dans la bulle, ou `null`. */
    private val _selection = MutableStateFlow<IslandGenerator.Case?>(null)
    val selection: StateFlow<IslandGenerator.Case?> = _selection.asStateFlow()

    fun selectionner(x: Int, y: Int) {
        _selection.value = IslandGenerator.Case(x, y)
    }

    fun fermerSelection() {
        _selection.value = null
    }

    /**
     * Rattrape la croissance des cultures.
     *
     * Appelé à l'ouverture de l'écran : rien ne tourne en arrière-plan, la
     * plante n'avance pas, on recalcule où elle en serait.
     */
    fun rafraichir() {
        viewModelScope.launch { runCatching { depot.rafraichirCultures() } }
    }

    private fun geste(bloc: suspend () -> IslandRepository.Geste) {
        viewModelScope.launch {
            _message.value = when (val r = bloc()) {
                is IslandRepository.Geste.Fait -> r.message
                is IslandRepository.Geste.Refuse -> r.raison
            }
        }
    }

    fun degager(x: Int, y: Int) = geste { depot.degager(x, y) }
    fun preparer(x: Int, y: Int) = geste { depot.preparer(x, y) }
    fun semer(x: Int, y: Int, graineId: String) = geste { depot.semer(x, y, graineId) }
    fun arroser(x: Int, y: Int) = geste { depot.arroser(x, y) }
    fun recolter(x: Int, y: Int) = geste { depot.recolter(x, y) }
    fun batir(type: IslandBuildingEngine.Type, x: Int, y: Int) = geste { depot.batir(type, x, y) }

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
        rafraichir()
    }

    /**
     * Charge l'île du joueur, ou ouvre l'assistant s'il n'en a pas encore.
     *
     * Rien n'est fait au démarrage de l'application : générer trois îles n'a
     * aucune raison de retarder l'accueil de quelqu'un qui ne va pas au Jardin.
     */
    fun charger() {
        viewModelScope.launch {
            _etat.value = Etat(chargement = true)
            runCatching { depot.ile() }
                .onSuccess { existante ->
                    if (existante != null) {
                        _etat.value = Etat(chargement = false, ile = existante)
                    } else {
                        // Aucune île : on propose, on n'impose pas. Generer
                        // d'office priverait le joueur du seul choix qui
                        // engage toute sa partie.
                        _etat.value = Etat(
                            chargement = false,
                            candidates = tirerCandidates(System.currentTimeMillis())
                        )
                    }
                }
                .onFailure {
                    _etat.value = Etat(
                        chargement = false,
                        erreur = it.message ?: "L'île n'a pas pu être chargée."
                    )
                }
        }
    }

    /**
     * Trois îles jouables, tirées de graines voisines.
     *
     * Générées en mémoire et jamais écrites : rien n'est engagé tant que le
     * joueur n'a pas choisi. Les deux qu'il écarte disparaissent sans laisser
     * de trace en base.
     */
    private suspend fun tirerCandidates(base: Long): List<Candidate> =
        // Hors du thread principal : trois îles, chacune pouvant nécessiter
        // plusieurs tirages avant d'être jouable. C'est court, mais pas assez
        // pour être fait pendant que l'écran doit répondre au doigt.
        withContext(Dispatchers.Default) {
                (0 until 3).map { rang ->
                val (ile, rapport) = IslandGenerator.genererJouable(base + rang * 7_919L)
                Candidate(
                    ile = ile,
                    cultivables = rapport.cultivables,
                    boise = ile.compter { it == IslandTileType.FOREST },
                    rivieres = ile.compter { it == IslandTileType.RIVER }
                )
            }
        }

    fun choisirCandidate(index: Int) {
        _etat.value = _etat.value.copy(choisie = index)
    }

    fun definirNom(nom: String) {
        // Borné : un nom de deux cents caractères déborderait partout où il
        // s'affiche, à commencer par les miniatures.
        _etat.value = _etat.value.copy(nom = nom.take(24))
    }

    /**
     * Retire trois nouvelles îles.
     *
     * Le nombre de relances est limité, et c'est délibéré : sans limite, on
     * relance indéfiniment en cherchant l'île parfaite, et on ne commence
     * jamais à jouer.
     */
    fun relancer() {
        val etat = _etat.value
        if (etat.relancesRestantes <= 0) return
        viewModelScope.launch {
            _etat.value = etat.copy(
                candidates = tirerCandidates(System.currentTimeMillis()),
                choisie = 0,
                relancesRestantes = etat.relancesRestantes - 1
            )
        }
    }

    /** Fixe l'île définitivement et l'écrit en base. */
    fun validerChoix() {
        val etat = _etat.value
        val candidate = etat.candidates.getOrNull(etat.choisie) ?: return
        viewModelScope.launch {
            _etat.value = etat.copy(chargement = true)
            runCatching { depot.creerSiAbsente(candidate.ile.seed, etat.nom.trim()) }
                .onSuccess { _etat.value = Etat(chargement = false, ile = it) }
                .onFailure {
                    _etat.value = etat.copy(
                        chargement = false,
                        erreur = it.message ?: "L'île n'a pas pu être créée."
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
        /** Relances offertes avant de devoir choisir. */
        const val RELANCES = 3

        // Zoom minimum : voir l'île entière sur un écran de téléphone.
        const val ZOOM_MIN = 0.5f

        // Zoom maximum : environ trois cases de large, comme demandé.
        const val ZOOM_MAX = 3.5f

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { IslandViewModel(app) }
        }
    }
}
