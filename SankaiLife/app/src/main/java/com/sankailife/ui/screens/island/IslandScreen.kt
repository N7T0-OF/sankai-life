package com.sankailife.ui.screens.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.garden.domain.CameraJardinEngine
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors
import kotlin.math.floor

/**
 * L'île, dessinée.
 *
 * Tout le terrain tient dans un seul `Canvas`. Composer mille cases en objets
 * distincts coûterait mille mesures et mille placements à chaque frame, pour
 * afficher des rectangles de couleur — c'est le genre de choix qui rend un jeu
 * saccadé sur un téléphone d'entrée de gamme.
 *
 * La caméra réutilise `CameraJardinEngine` sans rien y ajouter : les défauts de
 * zoom corrigés sur le Jardin se reposeraient à l'identique ici.
 */
@Composable
fun IslandScreen(
    viewModel: IslandViewModel,
    onBack: () -> Unit
) {
    val etat by viewModel.etat.collectAsState()
    val parcelles by viewModel.parcelles.collectAsState()
    val utilisateur by viewModel.utilisateur.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val message by viewModel.message.collectAsState()
    val recentrage by viewModel.recentrage.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val batiments by viewModel.batiments.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val stockOuvert by viewModel.stockOuvert.collectAsState()
    val eau by viewModel.eau.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val miniCarteOuverte by viewModel.miniCarte.collectAsState()
    var vueVisible by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val couleurs = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }

    // Rattrapage au retour de l'arrière-plan.
    //
    // Le faire seulement à la création de l'écran ne suffit pas : la
    // ViewModel survit à une mise en arrière-plan, et le joueur qui revient
    // deux heures plus tard verrait ses cultures figées là où il les a
    // laissées. C'est précisément le moment où l'avancée doit se voir.
    val cycleDeVie = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cycleDeVie) {
        val observateur = androidx.lifecycle.LifecycleEventObserver { _, evenement ->
            if (evenement == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.rafraichir()
            }
        }
        cycleDeVie.addObserver(observateur)
        onDispose { cycleDeVie.removeObserver(observateur) }
    }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            viewModel.messageAffiche()
        }
    }

    Box(Modifier.fillMaxSize().background(PaletteIle.couleur(com.sankailife.core.island.domain.IslandTileType.DEEP_WATER))) {
        when {
            etat.chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = couleurs.accent)
                    Spacer(Modifier.height(12.dp))
                    // Deux attentes differentes, deux phrases. Annoncer une
                    // creation en rouvrant une ile qui existe deja ferait
                    // craindre qu'elle soit en train d'etre remplacee.
                    Text(
                        if (etat.candidates.isEmpty()) "Chargement de ton île…"
                        else "Création de ton île…",
                        color = Color.White, fontSize = 14.sp
                    )
                }
            }

            etat.erreur.isNotBlank() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(etat.erreur, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                    SankaiButton("Réessayer", onClick = { viewModel.charger() })
                }
            }

            etat.enAssistant -> AssistantIle(
                etat = etat,
                onChoisir = viewModel::choisirCandidate,
                onNom = viewModel::definirNom,
                onRelancer = viewModel::relancer,
                onValider = viewModel::validerChoix,
                modifier = Modifier.fillMaxSize()
            )

            etat.ile != null -> CarteIle(
                ile = etat.ile!!,
                parcelles = parcelles,
                batiments = batiments,
                zoom = zoom,
                demandeRecentrage = recentrage,
                destination = destination,
                onVueChangee = { vueVisible = it },
                niveau = utilisateur.level,
                pieces = utilisateur.coins,
                onZoom = viewModel::definirZoom,
                onToucher = viewModel::selectionner,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Barre haute, flottante : elle ne prend pas de hauteur à la carte.
        //
        // Les marges viennent des `WindowInsets`, jamais d'une valeur en dp.
        // Une constante choisie sur un téléphone passe derrière l'encoche du
        // suivant : il n'existe aucune hauteur de barre système universelle.
        // `safeDrawing` couvre la barre d'état, les encoches et les caméras
        // percées, y compris latérales en paysage.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "🪙 ${utilisateur.coins}   💧 $eau   " +
                        "🌱 ${parcelles.size}/${IslandSlotEngine.plafond(utilisateur.level)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Row {
                IconButton(onClick = { viewModel.basculerMiniCarte() }) {
                    Text("🗺️", fontSize = 20.sp)
                }
                IconButton(onClick = { viewModel.ouvrirStock() }) {
                    Text("📦", fontSize = 20.sp)
                }
                IconButton(onClick = { viewModel.recentrer() }) {
                    Icon(Icons.Filled.MyLocation, "Recentrer", tint = Color.White)
                }
            }
        }

        // Fiche de la case touchee. Rien de tout cela n'apparait sur la carte
        // elle-meme : un prix et un compte a rebours sur chaque case rendraient
        // l'ile illisible des la vingtieme parcelle.
        val ileCourante = etat.ile
        selection?.let { case ->
            if (ileCourante != null) {
                BulleParcelle(
                    x = case.x,
                    y = case.y,
                    type = ileCourante.type(case.x, case.y),
                    parcelle = parcelles[case.y * ileCourante.largeur + case.x],
                    parcellesPossedees = parcelles.size,
                    batiments = batiments,
                    niveau = utilisateur.level,
                    onFermer = viewModel::fermerSelection,
                    onAcheter = { viewModel.acheter(case.x, case.y) },
                    onDegager = { viewModel.degager(case.x, case.y) },
                    onPreparer = { viewModel.preparer(case.x, case.y) },
                    onSemer = { graine -> viewModel.semer(case.x, case.y, graine) },
                    onArroser = { viewModel.arroser(case.x, case.y) },
                    onRecolter = { viewModel.recolter(case.x, case.y) },
                    onBatir = { type -> viewModel.batir(type, case.x, case.y) },
                    onOuvrirStock = {
                        viewModel.fermerSelection()
                        viewModel.ouvrirStock()
                    }
                )
            }
        }

        val ileAffichee = etat.ile
        if (miniCarteOuverte && ileAffichee != null) {
            MiniCarte(
                ile = ileAffichee,
                parcelles = parcelles.keys,
                batiments = batiments,
                vue = vueVisible,
                onAller = viewModel::allerVers,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(16.dp)
            )
        }

        if (stockOuvert) {
            PanneauStock(
                stock = stock,
                batiments = batiments,
                onFermer = viewModel::fermerStock,
                onVendre = viewModel::vendre
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun CarteIle(
    ile: IslandGenerator.Ile,
    parcelles: Map<Int, com.sankailife.core.island.data.IslandSlotEntity>,
    batiments: List<com.sankailife.core.island.data.IslandBuildingEntity>,
    zoom: Float,
    demandeRecentrage: Long,
    destination: IslandViewModel.Destination?,
    onVueChangee: (androidx.compose.ui.geometry.Rect) -> Unit,
    niveau: Int,
    pieces: Int,
    onZoom: (Float) -> Unit,
    onToucher: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val densite = LocalDensity.current
    val textures = rememberTexturesIle()

    // Découpage des bois en arbres : calculé une fois par île, jamais par
    // frame. C'est du parcours de grille, pas du rendu.
    val arbres = remember(ile.seed, ile.largeur) {
        com.sankailife.core.island.domain.IslandForetEngine.decouper(
            largeur = ile.largeur,
            hauteur = ile.hauteur
        ) { x, y ->
            ile.type(x, y) == com.sankailife.core.island.domain.IslandTileType.FOREST
        }
    }
    var camera by remember { mutableStateOf(Offset.Zero) }
    var initialisee by remember { mutableStateOf(false) }
    var tailleVue by remember { mutableStateOf(IntOffset.Zero) }
    var mode by remember { mutableStateOf(false) } // true pendant un pincement
    var dernierZoomMs by remember { mutableStateOf(0L) }
    val zoomCourant = rememberUpdatedState(zoom)

    fun pasPour(echelle: Float): Float =
        with(densite) { (26.dp * echelle).toPx() }.let { floor(it) }.coerceAtLeast(1f)

    val pas = pasPour(zoom)

    fun cadrePour(echelle: Float) = CameraJardinEngine.Cadre(
        largeurVue = tailleVue.x.toFloat(),
        hauteurVue = tailleVue.y.toFloat(),
        minX = 0, maxX = ile.largeur - 1,
        minY = 0, maxY = ile.hauteur - 1,
        pas = pasPour(echelle)
    )

    fun borner(c: Offset): Offset =
        CameraJardinEngine.borner(CameraJardinEngine.Point(c.x, c.y), cadrePour(zoomCourant.value))
            .let { Offset(it.x, it.y) }

    // Déplacement demandé depuis la mini-carte : la case visée est amenée au
    // centre de l'écran, puis bornée comme n'importe quel déplacement.
    LaunchedEffect(destination?.jeton) {
        val d = destination ?: return@LaunchedEffect
        if (!initialisee || tailleVue.x <= 0) return@LaunchedEffect
        camera = borner(
            Offset(
                tailleVue.x / 2f - (d.x + 0.5f) * pas,
                tailleVue.y / 2f - (d.y + 0.5f) * pas
            )
        )
    }

    // Ce que la caméra montre, en cases. Sert au cadre de la mini-carte.
    LaunchedEffect(camera, pas, tailleVue) {
        if (pas <= 0f || tailleVue.x <= 0) return@LaunchedEffect
        onVueChangee(
            androidx.compose.ui.geometry.Rect(
                left = (-camera.x) / pas,
                top = (-camera.y) / pas,
                right = (-camera.x + tailleVue.x) / pas,
                bottom = (-camera.y + tailleVue.y) / pas
            )
        )
    }

    LaunchedEffect(demandeRecentrage) {
        if (initialisee && tailleVue.x > 0) {
            camera = CameraJardinEngine.centree(cadrePour(zoomCourant.value))
                .let { Offset(it.x, it.y) }
        }
    }

    Box(
        modifier
            .onSizeChanged { taille ->
                tailleVue = IntOffset(taille.width, taille.height)
                if (!initialisee && taille.width > 0) {
                    camera = CameraJardinEngine.centree(cadrePour(zoom)).let { Offset(it.x, it.y) }
                    initialisee = true
                } else if (initialisee) {
                    camera = borner(camera)
                }
            }
            // Un seul détecteur pour tous les gestes.
            //
            // Il y en avait trois — pincement, glissement, tape — et ils se
            // disputaient le même flux de doigts. `detectTransformGestures`
            // avec `panZoomLock` verrouille le zoom dès que le geste est lu
            // comme un déplacement : deux doigts posés en bougeant un peu
            // partaient en pan, et le zoom ne se déclenchait presque jamais.
            //
            // La règle est maintenant celle des jeux de gestion : le **nombre
            // de doigts** décide, pas la direction du mouvement.
            //
            //   deux doigts ou plus -> zoom, et rien d'autre
            //   un doigt            -> déplacement
            //   un doigt immobile   -> sélection
            //
            // Un geste ayant touché le zoom ne repasse jamais en déplacement
            // avant que tous les doigts soient levés : c'est ce qui supprime le
            // saut quand la seconde main se retire en dernier.
            .pointerInput(ile.seed) {
                awaitEachGesture {
                    val premier = awaitFirstDown(requireUnconsumed = false)
                    var aZoome = false
                    var aGlisse = false
                    var distancePrecedente = 0f

                    while (true) {
                        val evenement = awaitPointerEvent()
                        val presses = evenement.changes.filter { it.pressed }
                        if (presses.isEmpty()) break

                        if (presses.size >= 2) {
                            val distance = evenement.calculateCentroidSize(useCurrent = true)
                            val centroide = evenement.calculateCentroid(useCurrent = true)

                            if (distancePrecedente > 0f && distance > 0f &&
                                centroide != Offset.Unspecified
                            ) {
                                val facteur = distance / distancePrecedente
                                if (CameraJardinEngine.franchitSeuil(facteur)) {
                                    aZoome = true
                                    val r = CameraJardinEngine.pincer(
                                        camera = CameraJardinEngine.Point(camera.x, camera.y),
                                        centroide = CameraJardinEngine.Point(
                                            centroide.x, centroide.y
                                        ),
                                        echelleAvant = zoomCourant.value,
                                        facteur = facteur,
                                        zoomMin = IslandViewModel.ZOOM_MIN,
                                        zoomMax = IslandViewModel.ZOOM_MAX,
                                        cadreApres = ::cadrePour
                                    )
                                    camera = Offset(r.camera.x, r.camera.y)
                                    onZoom(r.echelle)
                                    distancePrecedente = distance
                                }
                            } else if (distance > 0f) {
                                distancePrecedente = distance
                            }
                            // Consommé dans tous les cas : c'est ce qui empêche
                            // le déplacement de s'emparer du même geste.
                            presses.forEach { it.consume() }
                        } else if (!aZoome) {
                            val delta = evenement.calculatePan()
                            if (delta != Offset.Zero) {
                                if (delta.getDistance() > viewConfiguration.touchSlop / 2f) {
                                    aGlisse = true
                                }
                                camera = borner(camera + delta)
                                presses.forEach { it.consume() }
                            }
                        } else {
                            // Doigt restant après un pincement : on l'ignore
                            // jusqu'à la levée complète.
                            presses.forEach { it.consume() }
                        }
                    }

                    // Sélection : un doigt, posé et relevé sans avoir glissé ni
                    // zoomé. Le pas est relu ici, pas capturé — c'était le
                    // défaut qui faisait désigner la mauvaise case après un
                    // zoom.
                    if (!aZoome && !aGlisse) {
                        val pasReel = pasPour(zoomCourant.value)
                        val x = floor((premier.position.x - camera.x) / pasReel).toInt()
                        val y = floor((premier.position.y - camera.y) / pasReel).toInt()
                        if (x in 0 until ile.largeur && y in 0 until ile.hauteur) {
                            onToucher(x, y)
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            dessinerIle(
                ile = ile, camera = camera, pas = pas,
                parcelles = parcelles.keys, batiments = batiments,
                parcellesDetail = parcelles, textures = textures, arbres = arbres
            )
        }
    }
}
