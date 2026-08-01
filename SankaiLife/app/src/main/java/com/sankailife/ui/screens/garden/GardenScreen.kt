package com.sankailife.ui.screens.garden

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import com.sankailife.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.ArenaEngine
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.sankailife.core.garden.domain.ArrosoirEngine
import com.sankailife.core.garden.domain.DayNightEngine
import com.sankailife.core.garden.domain.ExpansionEngine
import com.sankailife.core.garden.domain.MoistureEngine
import com.sankailife.core.garden.domain.WeatherEngine
import com.sankailife.core.garden.domain.MemoChallengeEngine
import com.sankailife.core.garden.domain.MimoEngine
import androidx.compose.foundation.verticalScroll
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.Seed
import com.sankailife.core.haptics.LocalHaptics
import androidx.compose.ui.platform.LocalContext
import com.sankailife.core.garden.domain.ConseilEngine
import com.sankailife.core.garden.domain.GardenContextEngine
import com.sankailife.core.garden.domain.WeatherVisualEngine
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate

/**
 * Le jardin.
 *
 * Écran non défilant : tout tient dans la hauteur, la grille absorbant
 * l'espace disponible via `weight`. Aucune dimension n'est figée en dur, pour
 * que la même mise en page tienne d'un petit téléphone à une tablette.
 *
 * Rendu volontairement géométrique — formes, dégradés et emojis. Les stades,
 * sols et espèces sont des catalogues séparés de l'affichage : substituer de
 * vraies illustrations ne demandera pas de retoucher la logique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    viewModel: GardenViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    val chargement by viewModel.chargement.collectAsState()
    val parcelles by viewModel.parcelles.collectAsState()
    val etat by viewModel.etat.collectAsState()
    val user by viewModel.user.collectAsState()
    val message by viewModel.message.collectAsState()
    val pretes by viewModel.nombrePretes.collectAsState()

    val caisses by viewModel.caisses.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val valeurStock by viewModel.valeurStock.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val magasinOuvert by viewModel.magasinOuvert.collectAsState()
    val meteo by viewModel.meteo.collectAsState()
    val aSoif by viewModel.parcellesASoif.collectAsState()
    val niveauArrosoir by viewModel.niveauArrosoir.collectAsState()
    val ambiance by viewModel.ambiance.collectAsState()
    val intensitePluie by viewModel.intensitePluie.collectAsState()
    val demandeRecentrage by viewModel.demandeRecentrage.collectAsState()
    val qualiteGraphique by viewModel.qualiteGraphique.collectAsState()

    // Respect du réglage système « réduire les animations ». C'est un réglage
    // d'accessibilité, pas une préférence esthétique : une pluie animée peut
    // provoquer des nausées.
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Le Jardin est un mode de jeu isolé : les barres Android disparaissent
    // réellement, puis retrouvent exactement leur visibilité et leur contraste
    // précédents à la sortie. Le swipe système reste disponible pour les faire
    // apparaître temporairement ; le bouton Retour du HUD n'est donc jamais la
    // seule issue.
    DisposableEffect(activity) {
        val window = activity?.window
        val decor = window?.decorView
        val controller = if (window != null && decor != null) {
            WindowCompat.getInsetsController(window, decor)
        } else {
            null
        }
        val insets = decor?.let(ViewCompat::getRootWindowInsets)
        val statusWasVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationWasVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val previousBehavior = controller?.systemBarsBehavior
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNavigation = controller?.isAppearanceLightNavigationBars

        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.let { bars ->
                if (statusWasVisible) bars.show(WindowInsetsCompat.Type.statusBars())
                else bars.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationWasVisible) bars.show(WindowInsetsCompat.Type.navigationBars())
                else bars.hide(WindowInsetsCompat.Type.navigationBars())
                if (previousBehavior != null) bars.systemBarsBehavior = previousBehavior
                if (previousLightStatus != null) bars.isAppearanceLightStatusBars = previousLightStatus
                if (previousLightNavigation != null) {
                    bars.isAppearanceLightNavigationBars = previousLightNavigation
                }
            }
        }
    }

    val animationsReduites = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val renduMeteo = remember(meteo, phase, qualiteGraphique, animationsReduites) {
        WeatherVisualEngine.state(
            weather = meteo,
            phase = phase,
            quality = qualiteGraphique,
            dayId = LocalDate.now().toString(),
            reduceMotion = animationsReduites
        )
    }

    // La simulation se rattrape a l'ouverture puis toutes les minutes, mais
    // uniquement tant que cet ecran est visible. La transaction cote depot est
    // idempotente : reprise et recomposition ne peuvent pas doubler le temps.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.synchroniserTemps()
                delay(60_000L)
            }
        }
    }

    val mimos by viewModel.mimos.collectAsState()
    val offres by viewModel.offres.collectAsState()
    val rapport by viewModel.rapportMimos.collectAsState()

    val conseil by viewModel.conseil.collectAsState()
    val cartesDues by viewModel.cartesDues.collectAsState()
    val mimosMonde by viewModel.mimosMonde.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val interfaceMasquee by viewModel.interfaceMasquee.collectAsState()
    val defi by viewModel.defi.collectAsState()
    val outil by viewModel.outil.collectAsState()

    var selectionContextuelleId by remember { mutableStateOf<Int?>(null) }
    var ficheParcelleId by remember { mutableStateOf<Int?>(null) }
    var marcheOuvert by remember { mutableStateOf(false) }
    var mimosOuvert by remember { mutableStateOf(false) }
    var sacOuvert by remember { mutableStateOf(false) }
    var conseilOuvert by remember { mutableStateOf(false) }
    var mimoSelectionne by remember {
        mutableStateOf<com.sankailife.core.garden.domain.MimoMondeEngine.MimoUi?>(null)
    }
    val selectionContextuelle = parcelles.firstOrNull { it.id == selectionContextuelleId }
    val ficheParcelle = parcelles.firstOrNull { it.id == ficheParcelleId }

    LaunchedEffect(conseil) {
        if (conseil == null) conseilOuvert = false
    }

    BackHandler(
        enabled = !interfaceMasquee &&
            (selectionContextuelle != null || conseilOuvert)
    ) {
        when {
            selectionContextuelle != null -> selectionContextuelleId = null
            conseilOuvert -> conseilOuvert = false
        }
    }

    if (sacOuvert) {
        FeuilleSac(
            graines = viewModel.grainesDisponibles(user.level),
            stock = stock,
            eau = etat.eau,
            compost = etat.compost,
            pieces = user.coins,
            niveauArrosoir = niveauArrosoir,
            niveau = user.level,
            outilTenu = outil,
            onChoisir = { haptics.click(); viewModel.choisirOutil(it); sacOuvert = false },
            onOuvrirDepot = { sacOuvert = false; marcheOuvert = true },
            onOuvrirMimos = { sacOuvert = false; mimosOuvert = true },
            onFermer = { sacOuvert = false }
        )
    }

    mimoSelectionne?.let { m ->
        FeuilleMimo(
            mimo = m,
            compost = etat.compost,
            onFermer = { mimoSelectionne = null }
        )
    }

    // Le rapport passe avant le défi souvenir : il explique un jardin qui a
    // changé tout seul, ce qui est plus déroutant qu'une question de révision.
    rapport?.let { texte ->
        FeuilleRapportMimos(texte = texte, onFermer = { viewModel.fermerRapport() })
    }

    if (mimosOuvert) {
        FeuilleMimos(
            mimos = mimos,
            offres = offres,
            pieces = user.coins,
            compost = etat.compost,
            onEmbaucher = { haptics.click(); viewModel.embaucher(it) },
            onFermer = { mimosOuvert = false }
        )
    }

    if (marcheOuvert) {
        FeuilleMarche(
            stock = stock,
            valeurTotale = valeurStock,
            ouvert = magasinOuvert,
            onVendre = { haptics.reward(); viewModel.vendre(it) },
            onVendreTout = { haptics.reward(); viewModel.vendreTout(); marcheOuvert = false },
            niveauArrosoir = niveauArrosoir,
            pieces = user.coins,
            onAmeliorerArrosoir = { haptics.click(); viewModel.ameliorerArrosoir() },
            onFermer = { marcheOuvert = false }
        )
    }

    defi?.let { d ->
        FeuilleDefiSouvenir(
            defi = d,
            onRepondre = { viewModel.repondreDefi(it) },
            onFermer = { viewModel.ignorerDefi() }
        )
    }

    ficheParcelle?.let { parcelle ->
        FeuilleParcelle(
            parcelle = parcelle,
            graines = viewModel.grainesDisponibles(user.level),
            pieces = user.coins,
            eau = etat.eau,
            onNettoyer = { viewModel.nettoyer(parcelle.id); ficheParcelleId = null },
            onPlanter = { g -> viewModel.planter(parcelle.id, g); ficheParcelleId = null },
            onArroser = { viewModel.arroser(parcelle.id); ficheParcelleId = null },
            onRecolter = {
                haptics.reward()
                viewModel.recolter(parcelle.id)
                ficheParcelleId = null
            },
            onDebloquer = { viewModel.debloquer(parcelle.id); ficheParcelleId = null },
            onFermer = { ficheParcelleId = null }
        )
    }

    if (chargement) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌿", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.garden_preparing), color = c.textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    val toggleDescription = stringResource(
        if (interfaceMasquee) R.string.garden_toggle_ui_show
        else R.string.garden_toggle_ui_hide
    )
    val weatherDescription = stringResource(R.string.garden_weather)

    Box(Modifier.fillMaxSize().background(c.background)) {
        // Le monde occupe réellement l'écran entier, jusque derrière les
        // barres système. Seules les commandes respectent les zones sûres.
        GrilleJardin(
            parcelles = parcelles,
            outil = outil,
            zoom = zoom,
            mimos = mimosMonde,
            demandeRecentrage = demandeRecentrage,
            ambiance = ambiance,
            renduMeteo = renduMeteo,
            qualiteGraphique = qualiteGraphique,
            intensitePluie = intensitePluie,
            animationsReduites = animationsReduites,
            modifier = Modifier.fillMaxSize(),
            onAppliquer = { viewModel.appliquerOutil(it) },
            onOuvrirDetail = {
                conseilOuvert = false
                selectionContextuelleId = it.id
            },
            onZoom = { viewModel.majZoom(it) },
            onOuvrirMimo = { mimoSelectionne = it },
            onReposerOutil = { viewModel.choisirOutil(null) }
        )

        Box(
            Modifier.matchParentSize().windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (!interfaceMasquee) {
                BoutonsFlottants(
                    conseil = conseil,
                    cartesDues = cartesDues,
                    caisses = caisses.size,
                    pretes = pretes,
                    outilTenu = outil,
                    modifier = Modifier.matchParentSize(),
                    onSac = {
                        haptics.click()
                        selectionContextuelleId = null
                        conseilOuvert = false
                        sacOuvert = true
                    },
                    onConseil = {
                        haptics.click()
                        selectionContextuelleId = null
                        conseilOuvert = !conseilOuvert
                    },
                    onApprendre = { onNavigate(Screen.Memo.route) },
                    onRecentrer = { haptics.click(); viewModel.recentrer() },
                    onAnnulerOutil = { viewModel.choisirOutil(null) },
                    onActionPrincipale = {
                        haptics.reward()
                        if (caisses.isNotEmpty()) viewModel.rangerCaisses()
                        else viewModel.toutRecolter()
                    }
                )

                selectionContextuelle?.let { parcelle ->
                    val action = GardenContextEngine.action(
                        cultivable = parcelle.cultivable,
                        ready = parcelle.prete,
                        needsWater = parcelle.besoinEau,
                        waterAvailable = etat.eau > 0
                    )
                    BulleParcelleContextuelle(
                        parcelle = parcelle,
                        action = action,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 118.dp),
                        onAction = {
                            when (action) {
                                GardenContextEngine.Action.HARVEST -> {
                                    haptics.reward()
                                    viewModel.recolter(parcelle.id)
                                }
                                GardenContextEngine.Action.WATER -> {
                                    haptics.click()
                                    viewModel.arroser(parcelle.id)
                                }
                                GardenContextEngine.Action.DETAILS -> {
                                    ficheParcelleId = parcelle.id
                                }
                            }
                            selectionContextuelleId = null
                        },
                        onFermer = { selectionContextuelleId = null }
                    )
                }

                conseil?.takeIf { conseilOuvert }?.let { conseilActuel ->
                    MiniConseil(
                        conseil = conseilActuel,
                        cartesDues = cartesDues,
                        pretes = pretes,
                        parcellesSeches = aSoif,
                        valeurStock = valeurStock,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 66.dp, bottom = 126.dp),
                        onAgir = {
                            conseilOuvert = false
                            when (conseilActuel.type) {
                                ConseilEngine.Type.CARTES_DUES,
                                ConseilEngine.Type.PLUS_D_EAU -> onNavigate(Screen.Memo.route)
                                ConseilEngine.Type.DEPOT_PLEIN -> viewModel.rangerCaisses()
                                ConseilEngine.Type.RECOLTE_PRETE -> viewModel.toutRecolter()
                                ConseilEngine.Type.STOCK_VENDABLE -> marcheOuvert = true
                                else -> Unit
                            }
                        },
                        onFermer = { conseilOuvert = false }
                    )
                }
            }

            // Retour et bascule d'interface restent disponibles même dans la
            // vue épurée : on ne peut jamais se retrouver prisonnier du jardin.
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(CircleShape)
                        .background(Color(0xFF0C1611).copy(alpha = 0.78f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.garden_leave),
                        tint = c.textPrimary
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!interfaceMasquee) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C1611).copy(alpha = 0.78f))
                            .semantics { contentDescription = weatherDescription }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("${phase.emoji} ${meteo.emoji}", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Ressource(ArtJardin.eau, "${etat.eau}", AccentCyan)
                    Spacer(Modifier.width(6.dp))
                    Ressource(ArtJardin.piece, "${user.coins}", CoinColor)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick = {
                        if (!interfaceMasquee) {
                            selectionContextuelleId = null
                            conseilOuvert = false
                        }
                        viewModel.basculerInterface()
                    },
                    modifier = Modifier.clip(CircleShape)
                        .background(Color(0xFF0C1611).copy(alpha = 0.78f))
                        .semantics { contentDescription = toggleDescription }
                ) {
                    Text(if (interfaceMasquee) "👁" else "🌿", fontSize = 15.sp)
                }
            }

            AnimatedVisibility(
                visible = message.isNotBlank(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
            ) {
                Box(
                    Modifier.padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface3)
                        .border(1.dp, c.accent, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(
                        message,
                        color = c.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


/** Résumé compact d'une parcelle ; la fiche complète reste disponible à la demande. */
@Composable
private fun BulleParcelleContextuelle(
    parcelle: GardenViewModel.ParcelleUi,
    action: GardenContextEngine.Action,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val titre = when {
        parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER ->
            stringResource(R.string.garden_plot_building)
        !parcelle.cultivable -> stringResource(R.string.garden_plot_locked)
        parcelle.etat == PlotState.UNCLEARED ->
            stringResource(R.string.garden_plot_uncleared)
        parcelle.etat == PlotState.EMPTY || parcelle.etat == PlotState.PREPARED ->
            stringResource(R.string.garden_plot_empty)
        parcelle.prete -> stringResource(R.string.garden_plot_ready)
        parcelle.besoinEau -> stringResource(R.string.garden_plot_thirsty)
        else -> stringResource(R.string.garden_plot_growing)
    }
    val symbole = when {
        parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER -> "🚧"
        !parcelle.cultivable -> "🔒"
        parcelle.etat == PlotState.UNCLEARED -> "🪨"
        parcelle.prete -> "🧺"
        parcelle.besoinEau -> "💧"
        else -> parcelle.stage?.emoji ?: "🌱"
    }
    val (actionSymbole, actionTexte, actionCouleur) = when (action) {
        GardenContextEngine.Action.HARVEST -> Triple(
            "🧺",
            stringResource(R.string.garden_action_harvest),
            AccentGold
        )
        GardenContextEngine.Action.WATER -> Triple(
            "💧",
            stringResource(R.string.garden_action_water),
            AccentCyan
        )
        GardenContextEngine.Action.DETAILS -> Triple(
            "…",
            stringResource(R.string.garden_action_details),
            c.accent
        )
    }
    val fermerDescription = stringResource(R.string.garden_action_close)

    Row(
        modifier
            .widthIn(min = 240.dp, max = 340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101A14).copy(alpha = 0.94f))
            .border(1.dp, actionCouleur.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
            .padding(start = 13.dp, top = 10.dp, end = 7.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbole, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (parcelle.cultivable) {
                Text(
                    stringResource(
                        R.string.garden_plot_humidity,
                        (parcelle.humidite.coerceIn(0f, 1f) * 100).toInt()
                    ),
                    color = c.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(12.dp))
                .background(actionCouleur.copy(alpha = 0.18f))
                .clickable { onAction() }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text(
                "$actionSymbole $actionTexte",
                color = actionCouleur,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "✕",
            color = c.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.size(34.dp)
                .semantics { contentDescription = fermerDescription }
                .clickable { onFermer() }
                .wrapContentSize(Alignment.Center)
        )
    }
}


/**
 * Fiche d'une case à acquérir, ou en chantier.
 *
 * Le prix et le temps sont annoncés avant l'achat, pas après : une extension
 * qu'on découvre longue une fois payée serait vécue comme un piège.
 */
@Composable
private fun FicheExtension(
    parcelle: GardenViewModel.ParcelleUi,
    pieces: Int,
    onDebloquer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val enChantier = parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (enChantier) "🚧" else parcelle.terrain.emoji, fontSize = 30.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (enChantier) "Chantier en cours" else parcelle.terrain.libelle,
                color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(parcelle.terrain.note, color = c.textSecondary, fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(16.dp))

    if (enChantier) {
        Text(
            "Encore ${formaterDuree(parcelle.minutesChantier)}. " +
                "Tu peux continuer à jouer ailleurs — le chantier avance même " +
                "application fermée.",
            color = c.textSecondary, fontSize = 13.sp
        )
        return
    }

    val duree = ExpansionEngine.dureeChantierMinutes(parcelle.id, parcelle.terrain)
    Text("Sol : ${parcelle.terrain.sol.emoji} ${parcelle.terrain.sol.libelle}",
        color = c.textSecondary, fontSize = 12.sp)
    Text("Défrichage : ${formaterDuree(duree)}", color = c.textSecondary, fontSize = 12.sp)
    if (parcelle.terrain.aNettoyer) {
        Text("Terrain encombré — il faudra le nettoyer après le chantier.",
            color = c.textDisabled, fontSize = 11.sp)
    }

    Spacer(Modifier.height(18.dp))
    val abordable = pieces >= parcelle.coutDeblocage
    SankaiButton(
        if (abordable) "Défricher • ${parcelle.coutDeblocage} 🪙"
        else "Il faut ${parcelle.coutDeblocage} 🪙",
        onClick = onDebloquer,
        enabled = abordable,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * État du sol.
 *
 * Affiché ici et pas en permanence sur la grille : une jauge sur chaque case
 * rendrait le terrain illisible. La couleur du sol suffit au coup d'œil, le
 * détail vient à la demande.
 */
@Composable
private fun FicheHumidite(parcelle: GardenViewModel.ParcelleUi) {
    val c = MaterialTheme.sankaiColors
    val etat = parcelle.etatHumidite
    val heures = MoistureEngine.heuresAvantSecheresse(parcelle.humidite, parcelle.sol)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(MoistureEngine.teinteSol(parcelle.humidite)).copy(alpha = 0.35f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(etat.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${etat.libelle} • ${(parcelle.humidite * 100).toInt()} %",
                color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (heures <= 0f) "Le sol a besoin d'eau"
                else "Sec dans environ ${formaterDuree((heures * 60).toLong())}",
                color = c.textSecondary, fontSize = 11.sp
            )
        }
        parcelle.graine?.let {
            Text(it.besoinEau.libelle, color = c.textDisabled, fontSize = 10.sp)
        }
    }
}


/**
 * Rapport de retour.
 *
 * Sans lui, l'automatisation serait invisible : le joueur retrouverait un
 * jardin différent sans savoir pourquoi, ce qui ressemble à un bug. Il ne
 * s'affiche que s'il y a quelque chose à dire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleRapportMimos(texte: String, onFermer: () -> Unit) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("TON JARDIN A CONTINUÉ", color = SuccessGreen, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Rapport des Mimos", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(texte, color = c.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            SankaiButton("Continuer", onClick = onFermer, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * La maison des Mimos.
 *
 * Le prix monte à chaque embauche du même métier : sans cela, la stratégie
 * optimale serait d'aligner dix arroseurs et de ne plus jamais ouvrir le jeu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMimos(
    mimos: List<com.sankailife.core.garden.data.GardenMimoEntity>,
    offres: List<GardenViewModel.OffreMimo>,
    pieces: Int,
    compost: Int,
    onEmbaucher: (MimoEngine.Type) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("MAISON DES MIMOS", color = AccentCyan, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Ils travaillent en ton absence", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Chaque action coûte 1 🌱 de compost, produit par tes récoltes. " +
                "Ils dorment la nuit, comme le marchand.",
                color = c.textSecondary, fontSize = 12.sp
            )

            if (mimos.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Ton équipe • compost : $compost 🌱",
                    color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mimos.forEach { mimo ->
                        val type = MimoEngine.Type.parNom(mimo.type)
                        Column(
                            Modifier.clip(RoundedCornerShape(11.dp))
                                .background(c.surface2)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(type?.emoji ?: "🙂", fontSize = 18.sp)
                            Text(mimo.nom, color = c.textPrimary, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Embaucher", color = c.textSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            offres.forEach { offre ->
                val abordable = pieces >= offre.prix
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable(enabled = abordable) { onEmbaucher(offre.type) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(offre.type.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            offre.type.libelle +
                                if (offre.employes > 0) " ×${offre.employes}" else "",
                            color = if (abordable) c.textPrimary else c.textSecondary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(offre.type.role, color = c.textSecondary, fontSize = 11.sp)
                    }
                    Text("${offre.prix} 🪙",
                        color = if (abordable) CoinColor else c.textDisabled,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Le marché.
 *
 * Le cours varie d'un jour à l'autre, et il est affiché : vendre au bon moment
 * doit être une décision lisible, pas une loterie invisible. Hors des heures
 * d'ouverture, le stock reste consultable mais la vente est bloquée — on ne
 * cache jamais ce que le joueur possède.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMarche(
    stock: List<GardenViewModel.LigneStock>,
    valeurTotale: Int,
    ouvert: Boolean,
    onVendre: (GardenViewModel.LigneStock) -> Unit,
    onVendreTout: () -> Unit,
    niveauArrosoir: Int,
    pieces: Int,
    onAmeliorerArrosoir: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(
                if (ouvert) "MARCHÉ OUVERT" else "MARCHÉ FERMÉ",
                color = if (ouvert) SuccessGreen else c.textDisabled,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Dépôt central", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                if (ouvert)
                    "Ouvert de ${DayNightEngine.OUVERTURE_MAGASIN} h à ${DayNightEngine.FERMETURE_MAGASIN} h."
                else DayNightEngine.messageMagasinFerme(),
                color = c.textSecondary, fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            if (stock.isEmpty()) {
                Text(
                    "Le dépôt est vide. Récolte tes cultures, puis range les caisses.",
                    color = c.textSecondary, fontSize = 13.sp
                )
            } else {
                stock.forEach { ligne ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .clickable(enabled = ouvert) { onVendre(ligne) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ligne.graine.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ligne.graine.nom} × ${ligne.quantite}",
                                color = c.textPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${ligne.qualite.libelle} • ${ligne.prixUnitaire} 🪙 pièce",
                                color = c.textSecondary, fontSize = 11.sp
                            )
                        }
                        Text(
                            "${ligne.total} 🪙",
                            color = if (ouvert) CoinColor else c.textDisabled,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                SankaiButton(
                    if (ouvert) "Tout vendre • $valeurTotale 🪙" else "Marchand absent",
                    onClick = onVendreTout,
                    enabled = ouvert,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // L'atelier vit chez le marchand plutôt que dans une barre d'outils
            // de plus : on y vient déjà pour vendre, et c'est là qu'on a les
            // pièces en tête.
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(16.dp))

            Text("ATELIER", color = AccentGold, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))

            val cout = ArrosoirEngine.coutAmelioration(niveauArrosoir)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💧", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ArrosoirEngine.libelle(niveauArrosoir),
                        color = c.textPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cout == null) "Niveau maximum atteint"
                        else ArrosoirEngine.description(niveauArrosoir + 1),
                        color = c.textSecondary, fontSize = 11.sp
                    )
                }
            }

            if (cout != null) {
                Spacer(Modifier.height(8.dp))
                SankaiButton(
                    "Améliorer • $cout 🪙",
                    onClick = onAmeliorerArrosoir,
                    enabled = ouvert && pieces >= cout,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Un meilleur arrosoir couvre plus de cases d'un geste. " +
                        "Il ne crée pas d'eau : chaque parcelle arrosée coûte " +
                        "toujours une unité.",
                    color = c.textDisabled, fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Défi souvenir : reconnaître la phrase reçue en notification.
 *
 * Présenté à l'ouverture du jardin, une seule fois par notification. Il dure
 * une dizaine de secondes — c'est une micro-révision, pas un examen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleDefiSouvenir(
    defi: MemoChallengeEngine.Defi,
    onRepondre: (String) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("SOUVENIR DU JOUR", color = AccentCyan, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Quelle phrase as-tu reçue ?", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Module : ${defi.nomModule}", color = c.textSecondary, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            defi.options.forEach { option ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable { onRepondre(option) }
                        .padding(14.dp)
                ) {
                    Text(option, color = c.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Se tromper ne retire rien. Le défi reviendra à la prochaine notification.",
                color = c.textDisabled, fontSize = 11.sp
            )
        }
    }
}



@Composable
private fun Ressource(
    @androidx.annotation.DrawableRes art: Int,
    valeur: String,
    couleur: Color
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Même règle qu'ailleurs : l'icône prime sur le chiffre.
        IconeArt(art, taille = 22.dp)
        Spacer(Modifier.width(4.dp))
        Text(valeur, color = couleur, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Arbre central : son apparence suit l'arène atteinte. */
@Composable
private fun ArbreSankai(niveau: Int) {
    val c = MaterialTheme.sankaiColors
    val arene = ArenaEngine.areneActuelle(niveau)
    val taille = (26 + arene.id * 3).sp

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1A12))
            .border(1.dp, Color(0xFF2B4A34), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arene.emoji, fontSize = taille)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Arbre Sankai", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Stade ${arene.id} • niveau $niveau", color = c.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Parcelle(
    parcelle: GardenViewModel.ParcelleUi,
    modifier: Modifier = Modifier,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // Seule une parcelle prête est animée : si tout bougeait, plus rien
    // n'attirerait le regard.
    val transition = rememberInfiniteTransition(label = "parcelle")
    val pulse by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseParcelle"
    )

    val (fond, bordure) = when (parcelle.etat) {
        PlotState.LOCKED -> Color(0xFF1A1D1B) to c.border
        PlotState.UNCLEARED -> Color(0xFF2A2622) to Color(0xFF4A413A)
        PlotState.READY_TO_HARVEST -> Color(0xFF3B2F16) to AccentGold.copy(alpha = pulse)
        else -> Color(0xFF3A2A1C) to Color(0xFF6B4B30)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fond)
            .border(if (parcelle.prete) 2.dp else 1.dp, bordure, RoundedCornerShape(14.dp))
            .clickable { onClic() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (parcelle.etat) {
                    PlotState.LOCKED -> "🔒"
                    PlotState.UNCLEARED -> "🪨"
                    PlotState.EMPTY, PlotState.PREPARED -> "＋"
                    else -> parcelle.stage?.emoji ?: "🌱"
                },
                fontSize = 24.sp,
                color = if (parcelle.etat == PlotState.EMPTY) c.textSecondary else Color.Unspecified
            )

            val libelle = when {
                parcelle.etat == PlotState.LOCKED -> "Arène ${parcelle.areneRequise}"
                parcelle.etat == PlotState.UNCLEARED -> "Nettoyer"
                parcelle.etat == PlotState.EMPTY -> "Planter"
                parcelle.prete -> "Prêt"
                parcelle.besoinEau -> "Assoiffée"
                else -> formaterDuree(parcelle.minutesRestantes)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                libelle,
                color = when {
                    parcelle.prete -> AccentGold
                    parcelle.besoinEau -> AccentCyan
                    else -> c.textSecondary
                },
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formaterDuree(minutes: Long): String = when {
    minutes <= 0 -> "Prêt"
    minutes < 60 -> "${minutes} min"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Actions possibles sur une parcelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleParcelle(
    parcelle: GardenViewModel.ParcelleUi,
    graines: List<Seed>,
    pieces: Int,
    eau: Int,
    onNettoyer: () -> Unit,
    onPlanter: (Seed) -> Unit,
    onArroser: () -> Unit,
    onRecolter: () -> Unit,
    onDebloquer: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {

            // Une case pas encore acquise ne parle que d'acquisition : ses
            // options de culture n'auraient aucun sens tant qu'elle n'est pas
            // à nous.
            if (!parcelle.cultivable) {
                FicheExtension(parcelle = parcelle, pieces = pieces, onDebloquer = onDebloquer)
                return@Column
            }

            FicheHumidite(parcelle)
            Spacer(Modifier.height(16.dp))

            when (parcelle.etat) {
                PlotState.LOCKED -> {
                    Text("Parcelle verrouillée", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                PlotState.UNCLEARED -> {
                    Text("Terrain encombré", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Des pierres bloquent cette parcelle. " +
                        "Nettoyage : ${com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE} 🪙",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    SankaiButton(
                        "Nettoyer",
                        onClick = onNettoyer,
                        enabled = pieces >= com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PlotState.EMPTY, PlotState.PREPARED -> {
                    Text("Planter une graine", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Sol : ${parcelle.sol.emoji} ${parcelle.sol.libelle}",
                        color = c.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    val compatibles = graines.filter { it.solRequis == parcelle.sol }
                    if (compatibles.isEmpty()) {
                        Text(
                            "Aucune graine compatible avec ce sol pour l'instant.",
                            color = c.textSecondary, fontSize = 13.sp
                        )
                    } else {
                        compatibles.forEach { graine ->
                            val abordable = pieces >= graine.prixPieces
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface2)
                                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = abordable) { onPlanter(graine) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(graine.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(graine.nom,
                                        color = if (abordable) c.textPrimary else c.textSecondary,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${graine.dureeMinutes / 60} h • rapporte ${graine.rendementPieces} 🪙",
                                        color = c.textSecondary, fontSize = 11.sp
                                    )
                                }
                                Text("${graine.prixPieces} 🪙",
                                    color = if (abordable) CoinColor else c.textDisabled,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {
                    val graine = parcelle.graine
                    Text(graine?.nom ?: "Culture", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        parcelle.stage?.libelle.orEmpty() +
                        if (parcelle.enRepos) " • en repos" else "",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { parcelle.progression },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (parcelle.prete) AccentGold else SuccessGreen,
                        trackColor = c.surface3
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (parcelle.prete) "Prête à récolter"
                        else "Encore ${formaterDuree(parcelle.minutesRestantes)}",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    if (parcelle.enRepos) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cette plante était en repos. Elle n'a rien perdu : " +
                            "arrose-la pour relancer sa croissance.",
                            color = c.textDisabled, fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    if (parcelle.prete) {
                        SankaiButton("🧺  Récolter", onClick = onRecolter,
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        SankaiButton(
                            if (eau > 0) "💧  Arroser" else "Plus d'eau",
                            onClick = onArroser,
                            enabled = eau > 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
