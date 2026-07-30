package com.sankailife.ui.screens.garden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.data.GardenRepository
import com.sankailife.core.garden.data.GardenStateEntity
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.*
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val repo = GardenRepository(app.database, userRepo)

    /** Vue d'une parcelle, entièrement résolue : l'écran n'a plus rien à calculer. */
    data class ParcelleUi(
        val id: Int,
        val etat: PlotState,
        val sol: SoilType,
        val graine: Seed? = null,
        val stage: CropStage? = null,
        val progression: Float = 0f,
        val minutesRestantes: Long = 0L,
        val prete: Boolean = false,
        val besoinEau: Boolean = false,
        val enRepos: Boolean = false,
        val areneRequise: Int = 1
    )

    private val _chargement = MutableStateFlow(true)
    val chargement: StateFlow<Boolean> = _chargement

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val etat: StateFlow<GardenStateEntity> = repo.etatFlow
        .map { it ?: GardenStateEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GardenStateEntity())

    /**
     * Les parcelles, croissance résolue.
     *
     * Un compteur d'une minute force le recalcul : sans lui, les temps
     * restants resteraient figés tant que la base ne change pas, ce qui donne
     * l'impression d'un jardin arrêté.
     */
    private val tick = flow {
        while (true) { emit(Unit); delay(60_000) }
    }

    val parcelles: StateFlow<List<ParcelleUi>> =
        combine(repo.parcellesFlow, repo.culturesFlow, user, tick) { plots, crops, u, _ ->
            val areneActuelle = ArenaEngine.areneActuelle(u.level).id
            val maintenant = System.currentTimeMillis()

            plots.map { plot ->
                val sol = SoilType.parId(plot.solId)
                val culture = crops.firstOrNull { it.plotId == plot.id }
                val graine = culture?.let { seedParId(it.seedId) }

                // Une parcelle verrouillée s'ouvre dès que l'arène est atteinte.
                val etatBrut = runCatching { PlotState.valueOf(plot.etat) }
                    .getOrDefault(PlotState.LOCKED)
                val etatReel = if (etatBrut == PlotState.LOCKED && areneActuelle >= plot.areneRequise) {
                    PlotState.UNCLEARED
                } else etatBrut

                if (culture == null || graine == null) {
                    ParcelleUi(plot.id, etatReel, sol, areneRequise = plot.areneRequise)
                } else {
                    val depuisArrosage = (maintenant - culture.dernierArrosageMillis) / 60_000
                    val e = CropGrowthEngine.etat(graine, sol, culture.minutesCumulees, depuisArrosage)
                    ParcelleUi(
                        id = plot.id,
                        etat = if (e.prete) PlotState.READY_TO_HARVEST else PlotState.GROWING,
                        sol = sol,
                        graine = graine,
                        stage = e.stage,
                        progression = e.progression,
                        minutesRestantes = e.minutesRestantes,
                        prete = e.prete,
                        besoinEau = e.besoinEau,
                        enRepos = e.enRepos,
                        areneRequise = plot.areneRequise
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nombrePretes: StateFlow<Int> = parcelles
        .map { liste -> liste.count { it.prete } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Graines réellement plantables au niveau actuel. */
    fun grainesDisponibles(niveau: Int): List<Seed> {
        val arene = ArenaEngine.areneActuelle(niveau).id
        return ALL_SEEDS.filter { it.areneRequise <= arene }
    }

    private val _defi = MutableStateFlow<MemoChallengeEngine.Defi?>(null)
    val defi: StateFlow<MemoChallengeEngine.Defi?> = _defi

    init {
        viewModelScope.launch {
            val verdict = repo.ouvrirJardin()
            TrustedTimeEngine.message(verdict)?.let { afficher(it) }
            _defi.value = runCatching { repo.defiSouvenir() }.getOrNull()
            _chargement.value = false
        }
    }

    /** Nombre de colonnes du terrain, partagé avec la grille. */
    val colonnes = 4

    private val _outil = MutableStateFlow<OutilJardin?>(null)
    val outil: StateFlow<OutilJardin?> = _outil

    /** Sélectionner l'outil déjà tenu le repose : un seul geste pour les deux. */
    fun choisirOutil(nouveau: OutilJardin?) {
        _outil.value = if (_outil.value == nouveau) null else nouveau
    }

    /**
     * Applique l'outil tenu à une parcelle.
     *
     * Appelé pour chaque case traversée pendant un glissement, donc
     * potentiellement plusieurs fois par seconde : les messages ne sont émis
     * que pour les échecs réellement informatifs, sinon un balayage sur des
     * cases incompatibles noierait l'écran de notifications.
     */
    fun appliquerOutil(plotId: Int) = viewModelScope.launch {
        val outilCourant = _outil.value ?: return@launch
        val parcelle = parcelles.value.firstOrNull { it.id == plotId } ?: return@launch
        if (!outilCourant.applicableA(parcelle.etat)) return@launch

        when (outilCourant) {
            is OutilJardin.Graine ->
                if (!repo.planter(plotId, outilCourant.seed.id)) {
                    afficher("Pièces insuffisantes")
                    _outil.value = null
                }
            OutilJardin.Arrosoir ->
                if (!repo.arroser(plotId)) {
                    afficher("Plus d'eau — révise des cartes pour en obtenir")
                    _outil.value = null
                }
            OutilJardin.Panier -> repo.recolter(plotId)
            OutilJardin.Pioche ->
                if (!repo.nettoyer(plotId)) {
                    afficher("Il faut ${GardenRepository.COUT_NETTOYAGE} pièces")
                    _outil.value = null
                }
        }
    }

    /** Répond au défi souvenir. Une seule réponse est prise en compte. */
    fun repondreDefi(reponse: String) = viewModelScope.launch {
        val defiCourant = _defi.value ?: return@launch
        val reussi = reponse == defiCourant.bonneReponse
        val recompense = repo.repondreDefiSouvenir(defiCourant.challengeId, reussi)

        _defi.value = null
        afficher(
            if (reussi) "Souvenir exact • +${recompense.eau} 💧 +${recompense.pieces} 🪙"
            else "Ce n'était pas celle-là. Elle reviendra."
        )
    }

    fun ignorerDefi() { _defi.value = null }

    fun nettoyer(plotId: Int) = viewModelScope.launch {
        if (repo.nettoyer(plotId)) afficher("Parcelle nettoyée")
        else afficher("Il faut ${GardenRepository.COUT_NETTOYAGE} pièces")
    }

    fun planter(plotId: Int, graine: Seed) = viewModelScope.launch {
        if (repo.planter(plotId, graine.id)) afficher("${graine.emoji} ${graine.nom} plantée")
        else afficher("Pièces insuffisantes")
    }

    fun arroser(plotId: Int) = viewModelScope.launch {
        if (repo.arroser(plotId)) afficher("Parcelle arrosée")
        else afficher("Plus d'eau — révise des cartes pour en obtenir")
    }

    fun recolter(plotId: Int) = viewModelScope.launch {
        val pieces = repo.recolter(plotId)
        if (pieces != null) afficher("Récolte : +$pieces 🪙")
        else afficher("Cette plante n'est pas encore prête")
    }

    fun toutRecolter() = viewModelScope.launch {
        var total = 0
        parcelles.value.filter { it.prete }.forEach { p ->
            repo.recolter(p.id)?.let { total += it }
        }
        if (total > 0) afficher("Tout récolté : +$total 🪙")
    }

    private fun afficher(texte: String) = viewModelScope.launch {
        _message.value = texte
        delay(2600)
        _message.value = ""
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { GardenViewModel(app) }
        }
    }
}
