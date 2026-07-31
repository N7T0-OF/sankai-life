package com.sankailife.ui.screens.garden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.data.GardenCrateEntity
import com.sankailife.core.garden.data.GardenMimoEntity
import com.sankailife.core.garden.data.GardenRepository
import com.sankailife.core.garden.data.GardenStateEntity
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.*
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

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
        val areneRequise: Int = 1,

        val x: Int = 0,
        val y: Int = 0,
        val deblocage: ExpansionEngine.Deblocage = ExpansionEngine.Deblocage.CACHEE,
        val terrain: ExpansionEngine.Terrain = ExpansionEngine.Terrain.ORDINAIRE,
        val humidite: Float = 0.5f,
        val coutDeblocage: Int = 0,
        val minutesChantier: Long = 0L
    ) {
        val cultivable: Boolean
            get() = deblocage == ExpansionEngine.Deblocage.DEBLOQUEE
        val etatHumidite: MoistureEngine.Etat get() = MoistureEngine.etat(humidite)
    }

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
                val deblocage = runCatching {
                    ExpansionEngine.Deblocage.valueOf(plot.deblocage)
                }.getOrDefault(ExpansionEngine.Deblocage.CACHEE)
                val terrain = ExpansionEngine.Terrain.parNom(plot.terrain)

                val commun = ParcelleUi(
                    id = plot.id,
                    etat = runCatching { PlotState.valueOf(plot.etat) }
                        .getOrDefault(PlotState.EMPTY),
                    sol = sol,
                    areneRequise = plot.areneRequise,
                    x = ExpansionEngine.xDe(plot.id),
                    y = ExpansionEngine.yDe(plot.id),
                    deblocage = deblocage,
                    terrain = terrain,
                    humidite = plot.humidite,
                    coutDeblocage = ExpansionEngine.cout(plot.id, terrain),
                    minutesChantier = if (plot.chantierFinMillis > maintenant) {
                        (plot.chantierFinMillis - maintenant) / 60_000 + 1
                    } else 0L
                )

                if (culture == null || graine == null) {
                    commun
                } else {
                    // L'état de la culture est calculé avec l'humidité réelle
                    // du sol, pas avec un minuteur d'arrosage : c'est le sol
                    // qui nourrit la plante, pas le souvenir du dernier geste.
                    val depuisArrosage = (maintenant - culture.dernierArrosageMillis) / 60_000
                    val e = CropGrowthEngine.etat(graine, sol, culture.minutesCumulees, depuisArrosage)
                    commun.copy(
                        etat = if (e.prete) PlotState.READY_TO_HARVEST else PlotState.GROWING,
                        graine = graine,
                        stage = e.stage,
                        progression = e.progression,
                        minutesRestantes = e.minutesRestantes,
                        prete = e.prete,
                        besoinEau = MoistureEngine.aBesoinDEau(plot.humidite, graine),
                        enRepos = e.enRepos
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

    /** Ce que les Mimos ont fait pendant l'absence, montré une seule fois. */
    private val _rapportMimos = MutableStateFlow<String?>(null)
    val rapportMimos: StateFlow<String?> = _rapportMimos

    fun fermerRapport() { _rapportMimos.value = null }

    init {
        viewModelScope.launch {
            val ouverture = repo.ouvrirJardin()
            TrustedTimeEngine.message(ouverture.verdict)?.let { afficher(it) }
            _rapportMimos.value = MimoEngine.resume(ouverture.rapportMimos)
            _defi.value = runCatching { repo.defiSouvenir() }.getOrNull()
            _chargement.value = false
        }
    }

    // --- Mimos -------------------------------------------------------------

    val mimos: StateFlow<List<GardenMimoEntity>> = repo.mimosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Une offre d'embauche, prix courant compris. */
    data class OffreMimo(
        val type: MimoEngine.Type,
        val employes: Int,
        val prix: Int
    )

    val offres: StateFlow<List<OffreMimo>> = mimos
        .map { liste ->
            MimoEngine.Type.entries.map { type ->
                val employes = liste.count { it.type == type.name }
                OffreMimo(type, employes, repo.coutEmbauche(type, employes))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun embaucher(type: MimoEngine.Type) = viewModelScope.launch {
        if (repo.embaucherMimo(type)) afficher("${type.emoji} Un ${type.libelle.lowercase()} rejoint le jardin")
        else afficher("Pièces insuffisantes")
    }

    /** Achète une case découverte et lance son chantier. */
    fun debloquer(plotId: Int) = viewModelScope.launch {
        val parcelle = parcelles.value.firstOrNull { it.id == plotId }
        if (repo.lancerChantier(plotId)) {
            afficher("Chantier lancé — ${parcelle?.terrain?.libelle ?: "extension"}")
        } else {
            afficher("Il faut ${parcelle?.coutDeblocage ?: 0} 🪙")
        }
    }

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
        // Une case non débloquée ne réagit à aucun outil : le glissement la
        // traverse sans effet plutôt que de refuser bruyamment.
        if (!parcelle.cultivable) return@launch
        if (!outilCourant.applicableA(parcelle.etat)) return@launch

        when (outilCourant) {
            is OutilJardin.Graine ->
                if (!repo.planter(plotId, outilCourant.seed.id)) {
                    afficher("Pièces insuffisantes")
                    _outil.value = null
                }
            OutilJardin.Arrosoir ->
                // L'arrosage assisté n'échoue pas bruyamment sur un sol déjà
                // humide : pendant un glissement, ce serait une alerte par case.
                if (repo.arroserZone(plotId) == 0 && etat.value.eau < 1) {
                    afficher("Plus d'eau — révise des cartes pour en obtenir")
                    _outil.value = null
                }
            OutilJardin.Panier ->
                if (repo.recolter(plotId) == null &&
                    DepotEngine.terrainSature(caisses.value.size)
                ) {
                    afficher("Le terrain est couvert de caisses — range-les au dépôt")
                    _outil.value = null
                }
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
        val recolte = repo.recolter(plotId)
        when {
            recolte != null ->
                afficher("${recolte.graine.emoji} ${recolte.graine.nom} • ${recolte.qualite.libelle} → caisse")
            DepotEngine.terrainSature(caisses.value.size) ->
                afficher("Le terrain est couvert de caisses — range-les au dépôt")
            else -> afficher("Cette plante n'est pas encore prête")
        }
    }

    fun toutRecolter() = viewModelScope.launch {
        var total = 0
        parcelles.value.filter { it.prete }.forEach { p ->
            if (repo.recolter(p.id) != null) total++
        }
        if (total > 0) afficher("$total caisse(s) posée(s) — à ranger au dépôt")
        else afficher("Le terrain est couvert de caisses — range-les au dépôt")
    }

    // --- Dépôt central ----------------------------------------------------

    val caisses: StateFlow<List<GardenCrateEntity>> = repo.caissesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ligne de stock résolue : espèce, qualité, quantité, prix du jour. */
    data class LigneStock(
        val graine: Seed,
        val qualite: HarvestQuality,
        val quantite: Int,
        val prixUnitaire: Int
    ) {
        val total: Int get() = prixUnitaire * quantite
    }

    val stock: StateFlow<List<LigneStock>> =
        combine(repo.inventaireFlow, tick) { lignes, _ ->
            val jour = LocalDate.now().toString()
            lignes.mapNotNull { ligne ->
                val graine = seedParId(ligne.seedId) ?: return@mapNotNull null
                val qualite = runCatching { HarvestQuality.valueOf(ligne.qualite) }
                    .getOrDefault(HarvestQuality.NORMALE)
                LigneStock(
                    graine = graine,
                    qualite = qualite,
                    quantite = ligne.quantite,
                    prixUnitaire = DepotEngine.prixUnitaire(graine, qualite, jour)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val valeurStock: StateFlow<Int> = stock
        .map { liste -> liste.sumOf { it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun rangerCaisses() = viewModelScope.launch {
        val rangees = repo.rangerCaisses()
        if (rangees > 0) afficher("$rangees caisse(s) rangée(s) au dépôt")
        else afficher("Aucune caisse à ranger")
    }

    fun vendre(ligne: LigneStock) = viewModelScope.launch {
        val pieces = repo.vendre(ligne.graine.id, ligne.qualite, ligne.quantite)
        afficher(
            when {
                pieces != null -> "Vendu : +$pieces 🪙"
                // Le refus a deux causes possibles ; les confondre ferait dire
                // « le marchand dort » en plein midi si un second appui arrive
                // pendant la première vente.
                !DayNightEngine.magasinOuvert() -> DayNightEngine.messageMagasinFerme()
                else -> "Ce lot vient d'être vendu"
            }
        )
    }

    fun vendreTout() = viewModelScope.launch {
        val total = repo.vendreTout()
        if (total > 0) afficher("Tout vendu : +$total 🪙")
        else afficher(
            if (magasinOuvert.value) "Rien à vendre"
            else DayNightEngine.messageMagasinFerme()
        )
    }

    // --- Cycle jour / nuit -------------------------------------------------

    /**
     * L'heure n'est relue qu'à chaque tick d'une minute. Un flux plus fin
     * ferait recomposer l'écran en continu pour un ciel qui bouge à peine.
     */
    // --- Météo et arrosoir --------------------------------------------------

    val meteo: StateFlow<WeatherEngine.Meteo> = tick
        .map { WeatherEngine.meteoActuelle() }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            WeatherEngine.meteoActuelle()
        )

    /** Prévision d'arrosage : combien de parcelles auront soif d'ici ce soir. */
    val parcellesASoif: StateFlow<Int> = combine(repo.parcellesFlow, tick) { _, _ ->
        runCatching { repo.parcellesASoifAvant(8f) }.getOrDefault(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val niveauArrosoir: StateFlow<Int> = etat
        .map { it.niveauArrosoir }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    fun ameliorerArrosoir() = viewModelScope.launch {
        val niveau = niveauArrosoir.value
        val cout = ArrosoirEngine.coutAmelioration(niveau)
        when {
            cout == null -> afficher("Ton arrosoir est déjà au maximum")
            repo.ameliorerArrosoir() -> afficher(ArrosoirEngine.libelle(niveau + 1))
            else -> afficher("Il faut $cout 🪙")
        }
    }

    val phase: StateFlow<DayNightEngine.Phase> = tick
        .map { DayNightEngine.phase() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayNightEngine.phase())

    /**
     * Ambiance lumineuse, recalculée chaque minute.
     *
     * Une interpolation continue sur l'heure : à 19 h 30 on est exactement à
     * mi-chemin entre le coucher de soleil et le crépuscule, sans qu'aucune
     * règle ne le dise.
     */
    val ambiance: StateFlow<LightingEngine.Ambiance> = tick
        .map { LightingEngine.ambiance() }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            LightingEngine.ambiance()
        )

    val intensitePluie: StateFlow<LightingEngine.IntensitePluie> = meteo
        .map { LightingEngine.intensitePluie(it) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            LightingEngine.IntensitePluie.AUCUNE
        )

    // --- Interface flottante -----------------------------------------------

    /**
     * Cartes réellement dues, tous modules confondus.
     *
     * L'heure de référence est reprise à chaque tick : une carte devient due
     * avec le temps qui passe, pas avec une écriture en base, donc un flux
     * Room seul ne se réveillerait jamais.
     */
    val cartesDues: StateFlow<Int> = tick
        .flatMapLatest {
            app.database.memoDao().compterToutesCartesDues(System.currentTimeMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Le conseil du moment, ou null.
     *
     * Un seul à la fois : la grande carte qu'il remplace affichait toujours
     * quelque chose, souvent la même phrase générique, et prenait de la hauteur
     * pour ne rien apprendre.
     */
    val conseil: StateFlow<ConseilEngine.Conseil?> = combine(
        parcelles, caisses, stock, etat, cartesDues
    ) { liste, lesCaisses, leStock, etatJardin, dues ->
        // L'heure et la météo sont des fonctions pures de l'instant : les lire
        // ici évite un septième flux, et surtout évite de dépendre d'une
        // propriété déclarée plus bas — ce qui planterait à l'initialisation.
        ConseilEngine.choisir(
            ConseilEngine.Contexte(
                cartesDues = dues,
                eau = etatJardin.eau,
                compost = etatJardin.compost,
                nombreMimos = mimos.value.size,
                parcellesPretes = liste.count { it.prete },
                parcellesSeches = liste.count { it.cultivable && it.besoinEau },
                caissesPosees = lesCaisses.size,
                terrainSature = DepotEngine.terrainSature(lesCaisses.size),
                valeurStock = leStock.sumOf { it.total },
                magasinOuvert = DayNightEngine.magasinOuvert(),
                ilVaPleuvoir = WeatherEngine.meteoActuelle().pleut
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Les Mimos tels qu'on les voit dans le jardin.
     *
     * Leur activité reflète l'état réel du terrain, mais ce n'est pas une
     * simulation : leur travail est reconstitué à l'ouverture. Voir
     * [MimoMondeEngine] pour ce que cette distinction implique.
     */
    val mimosMonde: StateFlow<List<MimoMondeEngine.MimoUi>> =
        combine(mimos, parcelles, caisses, etat) { liste, plots, lesCaisses, etatJardin ->
            val cultivables = plots.filter { it.cultivable }
            MimoMondeEngine.placer(
                mimos = liste.mapNotNull { m ->
                    MimoEngine.Type.parNom(m.type)?.let { Triple(m.id, m.nom, it) }
                },
                etat = MimoMondeEngine.EtatJardin(
                    parcellesDebloquees = cultivables.map { it.id },
                    parcellesSeches = cultivables.filter { it.besoinEau }.map { it.id },
                    parcellesPretes = cultivables.filter { it.prete }.map { it.id },
                    parcellesLibres = cultivables
                        .filter { it.etat == PlotState.EMPTY }.map { it.id },
                    caissesPosees = lesCaisses.size,
                    stockVendable = valeurStock.value > 0,
                    compost = etatJardin.compost,
                    faitJour = DayNightEngine.magasinOuvert()
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Niveau de zoom du terrain, borné par la grille elle-même. */
    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom

    fun majZoom(facteur: Float) {
        _zoom.value = (_zoom.value * facteur).coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    fun recentrer() { _zoom.value = 1f }

    /** Masque l'interface pour observer le jardin seul. */
    private val _interfaceMasquee = MutableStateFlow(false)
    val interfaceMasquee: StateFlow<Boolean> = _interfaceMasquee

    fun basculerInterface() { _interfaceMasquee.value = !_interfaceMasquee.value }

    val magasinOuvert: StateFlow<Boolean> = tick
        .map { DayNightEngine.magasinOuvert() }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            DayNightEngine.magasinOuvert()
        )

    private fun afficher(texte: String) = viewModelScope.launch {
        _message.value = texte
        delay(2600)
        _message.value = ""
    }

    companion object {
        /**
         * Bornes du zoom.
         *
         * En dessous de 0,6 les cases deviennent trop petites pour être visées
         * au doigt ; au-dessus de 2,2 les illustrations, prévues pour 256 px,
         * commencent à baver.
         */
        const val ZOOM_MIN = 0.6f
        const val ZOOM_MAX = 2.2f

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { GardenViewModel(app) }
        }
    }
}
