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
import com.sankailife.core.island.data.IslandStockEntity
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.core.island.domain.IslandTileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
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

    /**
     * Eau restante.
     *
     * La même réserve que le Jardin : c'est l'eau du joueur, pas celle d'un
     * lieu. Elle ne s'achète pas, elle se gagne en révisant — c'est ce qui
     * relie l'île à l'apprentissage.
     */
    val eau: StateFlow<Int> = depot.observerEau()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Récoltes entreposées. */
    val stock: StateFlow<List<IslandStockEntity>> = depot.observerStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val battement = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60_000L)
        }
    }

    /**
     * Où en est chaque culture, par clé de parcelle.
     *
     * Calculé ici plutôt que dans le `Canvas` : c'est une règle de jeu, pas du
     * dessin, et deux affichages en dépendent — le sprite de la plante et le
     * placement des Mimos. Deux calculs séparés finiraient par ne plus dire la
     * même chose, et on verrait un Mimo réclamer de l'eau pour une plante
     * affichée mûre.
     *
     * La fiche de parcelle, elle, garde son propre calcul : elle affiche un
     * temps restant, qui doit être juste à la seconde où on l'ouvre.
     *
     * Rafraîchi à la minute : une plante ne change pas d'aspect plus vite, et
     * recalculer par frame serait du travail jeté.
     */
    val cultures: StateFlow<Map<Int, com.sankailife.core.garden.domain.CropGrowthEngine.Etat>> =
        kotlinx.coroutines.flow.combine(parcelles, battement) { cases, maintenant ->
            cases.values.mapNotNull { p ->
                val graine = com.sankailife.core.garden.domain.ALL_SEEDS
                    .firstOrNull { it.id == p.graineId } ?: return@mapNotNull null
                p.cle to com.sankailife.core.garden.domain.CropGrowthEngine.etat(
                    seed = graine,
                    sol = com.sankailife.core.garden.domain.SoilType.parId(p.solId),
                    minutesCumulees = p.minutesCumulees,
                    minutesDepuisArrosage = (maintenant - p.dernierArrosageMillis) / 60_000L
                )
            }.toMap()
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // --- Mimos ---------------------------------------------------------------
    //
    // Les Mimos sont ceux du joueur, pas ceux d'un lieu : même table, même
    // équipe, comme la réserve d'eau. Ils étaient devenus inaccessibles quand
    // l'île a remplacé le Jardin — l'embauche n'existait que sur l'écran
    // disparu, et le système entier était donc mort sans que rien ne le dise.

    private val jardin = com.sankailife.core.garden.data.GardenRepository(app.database, userRepo)

    /** L'équipe employée, dans l'ordre d'embauche. */
    val mimos: StateFlow<List<com.sankailife.core.garden.data.GardenMimoEntity>> =
        jardin.mimosFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Le panneau d'équipe est-il ouvert ? */
    private val _equipeOuverte = MutableStateFlow(false)
    val equipeOuverte: StateFlow<Boolean> = _equipeOuverte.asStateFlow()

    fun ouvrirEquipe() { _equipeOuverte.value = true }
    fun fermerEquipe() { _equipeOuverte.value = false }

    /** Prix du prochain Mimo de ce métier, qui monte avec le nombre employés. */
    fun prixEmbauche(type: com.sankailife.core.garden.domain.MimoEngine.Type): Int =
        jardin.coutEmbauche(type, mimos.value.count { it.type == type.name })

    fun embaucher(type: com.sankailife.core.garden.domain.MimoEngine.Type) {
        viewModelScope.launch {
            _message.value = if (jardin.embaucherMimo(type)) {
                "${type.emoji} ${type.libelle} embauché"
            } else {
                "Pas assez de pièces pour embaucher un ${type.libelle.lowercase()}"
            }
        }
    }

    /**
     * Découpage des bois en arbres.
     *
     * Calculé une fois par île et mémorisé : c'est un parcours de 64 × 64 cases
     * qui ne dépend que du terrain, et le refaire à chaque changement de
     * parcelle serait du travail jeté.
     */
    private var arbresMemo:
        Pair<Long, List<com.sankailife.core.island.domain.IslandForetEngine.Arbre>>? = null

    private fun arbresDe(ile: IslandGenerator.Ile):
        List<com.sankailife.core.island.domain.IslandForetEngine.Arbre> {
        arbresMemo?.let { (seed, liste) -> if (seed == ile.seed) return liste }
        val liste = com.sankailife.core.island.domain.IslandForetEngine.decouper(
            largeur = ile.largeur, hauteur = ile.hauteur
        ) { x, y -> ile.type(x, y) == IslandTileType.FOREST }
        arbresMemo = ile.seed to liste
        return liste
    }

    /** Les arbres à dessiner. */
    val arbres: StateFlow<List<com.sankailife.core.island.domain.IslandForetEngine.Arbre>> =
        etat.map { it.ile?.let(::arbresDe) ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var casesArbresMemo: Pair<Long, Set<Pair<Int, Int>>>? = null

    private fun casesArbresDe(ile: IslandGenerator.Ile): Set<Pair<Int, Int>> {
        casesArbresMemo?.let { (seed, cases) -> if (seed == ile.seed) return cases }
        val cases = com.sankailife.core.island.domain.IslandForetEngine
            .casesReservees(arbresDe(ile))
        casesArbresMemo = ile.seed to cases
        return cases
    }

    /**
     * Où se tiennent les Mimos.
     *
     * Ici et non dans l'écran, parce que deux consommateurs en ont besoin : le
     * dessin de l'île et le panneau d'équipe. Le calculer dans le `Canvas`
     * obligerait le panneau à le refaire, et les deux finiraient par diverger —
     * un Mimo affiché « arrose » dans la liste et posé ailleurs sur la carte.
     */
    val mimosPlaces: StateFlow<List<com.sankailife.core.island.domain.IslandMimoMondeEngine.Place>> =
        kotlinx.coroutines.flow.combine(
            etat, parcelles, batiments, mimos, cultures
        ) { etatIle, cases, batis, equipe, pousses ->
            val ile = etatIle.ile ?: return@combine emptyList()
            if (equipe.isEmpty()) return@combine emptyList()

            val casesArbres = casesArbresDe(ile)
            val casesBaties = batis.flatMap { b ->
                IslandBuildingEngine.Type.parId(b.type)
                    ?.let { IslandBuildingEngine.casesOccupees(it, b.origineX, b.origineY) }
                    ?: emptyList()
            }.toSet()

            com.sankailife.core.island.domain.IslandMimoMondeEngine.placer(
                mimos = equipe.mapNotNull { m ->
                    com.sankailife.core.garden.domain.MimoEngine.Type.parNom(m.type)?.let {
                        com.sankailife.core.island.domain.IslandMimoMondeEngine
                            .Mimo(id = m.id, nom = m.nom, type = it)
                    }
                },
                parcelles = cases.values.map { p ->
                    val croissance = pousses[p.cle]
                    com.sankailife.core.island.domain.IslandMimoMondeEngine.Parcelle(
                        x = p.x, y = p.y,
                        aSoif = croissance?.besoinEau ?: false,
                        prete = croissance?.prete ?: false
                    )
                },
                // Les Mimos dorment quand l'île dort : même horloge que le ciel.
                faitJour = com.sankailife.core.garden.domain.DayNightEngine.phase() !=
                    com.sankailife.core.garden.domain.DayNightEngine.Phase.NUIT,
                repli = ile.ponton?.let { it.x to it.y }
            ) { x, y ->
                val type = ile.type(x, y)
                type.franchissable &&
                    (x to y) !in casesArbres &&
                    (x to y) !in casesBaties &&
                    // Jamais sur une parcelle achetée : un Mimo planté sur une
                    // culture cache exactement ce qu'on est venu regarder.
                    (y * ile.largeur + x) !in cases
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Le panneau de stock est-il ouvert ? */
    private val _stockOuvert = MutableStateFlow(false)
    val stockOuvert: StateFlow<Boolean> = _stockOuvert.asStateFlow()

    fun ouvrirStock() { _stockOuvert.value = true }
    fun fermerStock() { _stockOuvert.value = false }

    fun vendre(graineId: String, quantite: Int) = geste { depot.vendre(graineId, quantite) }

    /**
     * Construction en cours de placement.
     *
     * **On ne batissait pas, on pariait.** Choisir un batiment dans la fiche
     * d'une case le posait immediatement a cet endroit : un magasin de trois
     * cases sur deux s'installait sans qu'on ait vu ou il tomberait, et le seul
     * moyen de savoir etait de payer. Le mode visee montre l'emprise avant de
     * la poser, et refuse a l'avance en disant pourquoi.
     */
    data class Visee(
        val type: IslandBuildingEngine.Type,
        val x: Int,
        val y: Int,
        val verdict: IslandBuildingEngine.Verdict
    ) {
        val possible: Boolean get() = verdict is IslandBuildingEngine.Verdict.Oui
    }

    private val _visee = MutableStateFlow<Visee?>(null)
    val visee: StateFlow<Visee?> = _visee.asStateFlow()

    /** Entre en mode visee sur la case ou la fiche etait ouverte. */
    fun preparerConstruction(type: IslandBuildingEngine.Type, x: Int, y: Int) {
        _selection.value = null
        viser(type, x, y)
    }

    /** Deplace l'apercu. Recalcule le verdict a chaque fois : c'est lui qu'on montre. */
    fun viser(type: IslandBuildingEngine.Type, x: Int, y: Int) {
        val ile = _etat.value.ile ?: return
        val cases = parcelles.value
        val batis = batiments.value
        val occupees = batis.flatMap {
            IslandBuildingEngine.Type.parId(it.type)
                ?.let { t -> IslandBuildingEngine.casesOccupees(t, it.origineX, it.origineY) }
                ?: emptyList()
        }.toSet()

        _visee.value = Visee(
            type = type, x = x, y = y,
            verdict = IslandBuildingEngine.peutBatir(
                type = type, x = x, y = y,
                niveauJoueur = utilisateur.value.level,
                pieces = utilisateur.value.coins,
                dejaConstruit = batis.any { it.type == type.id },
                terrainDe = { cx, cy ->
                    if (cx in 0 until ile.largeur && cy in 0 until ile.hauteur) {
                        ile.type(cx, cy)
                    } else null
                },
                occupee = { cx, cy ->
                    (cx to cy) in occupees || (cy * ile.largeur + cx) in cases
                }
            )
        )
    }

    fun deplacerVisee(x: Int, y: Int) {
        _visee.value?.let { viser(it.type, x, y) }
    }

    fun annulerVisee() { _visee.value = null }

    /** Pose le batiment vise. Le verdict a deja ete montre : plus de surprise. */
    fun confirmerVisee() {
        val v = _visee.value ?: return
        _visee.value = null
        batir(v.type, v.x, v.y)
    }

    /**
     * Mimo touche sur la carte, ou `null`.
     *
     * Ils etaient dessines et muets : on voyait cinq silhouettes sans savoir
     * laquelle faisait quoi, ni pourquoi l'une dormait. Le panneau d'equipe le
     * disait, mais il fallait deja savoir qu'il existait et faire le lien entre
     * une ligne de liste et une silhouette a l'ecran.
     */
    private val _mimoTouche =
        MutableStateFlow<com.sankailife.core.island.domain.IslandMimoMondeEngine.Place?>(null)
    val mimoTouche: StateFlow<
        com.sankailife.core.island.domain.IslandMimoMondeEngine.Place?
        > = _mimoTouche.asStateFlow()

    fun fermerMimo() { _mimoTouche.value = null }

    /**
     * Toucher une case : un Mimo s'il y en a un, la case sinon.
     *
     * Le Mimo passe devant parce qu'il est **dessine** par-dessus : ouvrir la
     * fiche du terrain alors qu'on visait clairement une silhouette donnerait
     * l'impression que le toucher a rate.
     */
    fun toucher(x: Int, y: Int) {
        val mimo = mimosPlaces.value.firstOrNull { it.x == x && it.y == y }
        if (mimo != null) {
            _mimoTouche.value = mimo
            _selection.value = null
        } else {
            selectionner(x, y)
        }
    }

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
        viewModelScope.launch {
            runCatching {
                // La croissance d'abord : un Mimo ne peut récolter que ce que
                // le temps a rendu mûr.
                depot.rafraichirCultures()
                depot.travailDesMimos()
            }.onSuccess { compteRendu ->
                if (!compteRendu.isNullOrBlank()) _message.value = compteRendu
            }
        }
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

    /**
     * Heure, météo et lumière.
     *
     * Repris tels quels du Jardin : ce sont des moteurs purs qui ne connaissent
     * ni l'un ni l'autre. L'île devenant le mode de jeu, elle doit vivre au
     * même rythme — un terrain figé à midi n'a pas d'heures.
     *
     * Le battement est d'une minute : plus fin, l'écran recomposerait en
     * continu pour un ciel qui bouge à peine.
     */

    val ambiance: StateFlow<com.sankailife.core.garden.domain.LightingEngine.Ambiance> =
        battement.map { com.sankailife.core.garden.domain.LightingEngine.ambiance() }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.sankailife.core.garden.domain.LightingEngine.ambiance()
            )

    val meteo: StateFlow<com.sankailife.core.garden.domain.WeatherEngine.Meteo> =
        battement.map { com.sankailife.core.garden.domain.WeatherEngine.meteoActuelle() }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.sankailife.core.garden.domain.WeatherEngine.meteoActuelle()
            )

    val intensitePluie: StateFlow<com.sankailife.core.garden.domain.LightingEngine.IntensitePluie> =
        meteo.map { com.sankailife.core.garden.domain.LightingEngine.intensitePluie(it) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.sankailife.core.garden.domain.LightingEngine.IntensitePluie.AUCUNE
            )

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    /** Jeton de recentrage, incrémenté à chaque demande. */
    private val _recentrage = MutableStateFlow(0L)
    val recentrage: StateFlow<Long> = _recentrage.asStateFlow()

    /**
     * Case vers laquelle amener la caméra, demandée depuis la mini-carte.
     *
     * Portée par un jeton daté plutôt que par la seule case : redemander la
     * même destination doit fonctionner, or deux valeurs identiques ne
     * déclenchent rien dans un flux.
     */
    data class Destination(val x: Int, val y: Int, val jeton: Long)

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    fun allerVers(x: Int, y: Int) {
        _destination.value = Destination(x, y, System.currentTimeMillis())
    }

    /** La mini-carte est-elle dépliée ? */
    private val _miniCarte = MutableStateFlow(false)
    val miniCarte: StateFlow<Boolean> = _miniCarte.asStateFlow()

    fun basculerMiniCarte() { _miniCarte.value = !_miniCarte.value }

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
        const val ZOOM_MIN = 0.28f

        // Zoom maximum : environ trois cases de large, comme demandé.
        const val ZOOM_MAX = 3.5f

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { IslandViewModel(app) }
        }
    }
}
