package com.sankailife.ui.screens.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
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
    val couleurs = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }

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
                    Text("Création de ton île…", color = Color.White, fontSize = 14.sp)
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

            etat.ile != null -> CarteIle(
                ile = etat.ile!!,
                parcelles = parcelles,
                zoom = zoom,
                demandeRecentrage = recentrage,
                niveau = utilisateur.level,
                pieces = utilisateur.coins,
                onZoom = viewModel::definirZoom,
                onAcheter = viewModel::acheter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Barre haute, flottante : elle ne prend pas de hauteur à la carte.
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
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
                    "🪙 ${utilisateur.coins}   •   ${parcelles.size}/" +
                        "${IslandSlotEngine.plafond(utilisateur.level)} parcelles",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = { viewModel.recentrer() }) {
                Icon(Icons.Filled.MyLocation, "Recentrer", tint = Color.White)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun CarteIle(
    ile: IslandGenerator.Ile,
    parcelles: Set<Int>,
    zoom: Float,
    demandeRecentrage: Long,
    niveau: Int,
    pieces: Int,
    onZoom: (Float) -> Unit,
    onAcheter: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val densite = LocalDensity.current
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
            // Guetteur de levée des doigts : sans lui le mode resterait bloqué
            // sur « pincement » et plus rien ne se déplacerait ensuite.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Final)
                        if (e.changes.none { it.pressed }) mode = false
                    }
                }
            }
            .pointerInput(ile.seed) {
                detectTransformGestures(panZoomLock = true) { centroide, _, facteur, _ ->
                    if (!CameraJardinEngine.franchitSeuil(facteur)) return@detectTransformGestures
                    mode = true
                    dernierZoomMs = System.currentTimeMillis()
                    val r = CameraJardinEngine.pincer(
                        camera = CameraJardinEngine.Point(camera.x, camera.y),
                        centroide = CameraJardinEngine.Point(centroide.x, centroide.y),
                        echelleAvant = zoomCourant.value,
                        facteur = facteur,
                        zoomMin = IslandViewModel.ZOOM_MIN,
                        zoomMax = IslandViewModel.ZOOM_MAX,
                        cadreApres = ::cadrePour
                    )
                    camera = Offset(r.camera.x, r.camera.y)
                    onZoom(r.echelle)
                }
            }
            // Pas de `zoom` en clé : le recréer à chaque frame du pincement
            // annulerait le geste en boucle. C'est le défaut corrigé sur le
            // Jardin, autant ne pas le refaire ici.
            .pointerInput(ile.seed) {
                detectDragGestures { changement, delta ->
                    changement.consume()
                    if (!CameraJardinEngine.peutDeplacer(
                            enZoom = mode,
                            maintenantMs = System.currentTimeMillis(),
                            dernierZoomMs = dernierZoomMs
                        )
                    ) return@detectDragGestures
                    camera = borner(camera + delta)
                }
            }
            .pointerInput(ile.seed, parcelles.size, niveau, pieces) {
                detectTapGestures { position ->
                    val x = floor((position.x - camera.x) / pas).toInt()
                    val y = floor((position.y - camera.y) / pas).toInt()
                    if (x in 0 until ile.largeur && y in 0 until ile.hauteur) onAcheter(x, y)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Culling : seules les cases visibles sont dessinées. Sans lui, on
            // peint mille rectangles à chaque frame, dont la plupart hors écran.
            val premierX = floor((-camera.x) / pas).toInt().coerceIn(0, ile.largeur - 1)
            val premierY = floor((-camera.y) / pas).toInt().coerceIn(0, ile.hauteur - 1)
            val dernierX = (floor((size.width - camera.x) / pas).toInt() + 1)
                .coerceIn(0, ile.largeur - 1)
            val dernierY = (floor((size.height - camera.y) / pas).toInt() + 1)
                .coerceIn(0, ile.hauteur - 1)

            val taille = Size(pas + 1f, pas + 1f)

            for (y in premierY..dernierY) {
                for (x in premierX..dernierX) {
                    val type = ile.type(x, y)
                    drawRect(
                        color = PaletteIle.couleurCase(type, x, y),
                        topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                        size = taille
                    )

                    // Parcelle achetée : un liseré clair, rien de plus. Un halo
                    // permanent sur chaque case rendrait la carte illisible.
                    if (parcelles.contains(y * ile.largeur + x)) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.55f),
                            topLeft = Offset(camera.x + x * pas + 1f, camera.y + y * pas + 1f),
                            size = Size(pas - 2f, pas - 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            }

            // Écume : une ligne claire là où l'eau touche la terre. C'est ce qui
            // fait lire une côte plutôt qu'un changement de couleur.
            for (y in premierY..dernierY) {
                for (x in premierX..dernierX) {
                    if (!ile.type(x, y).estEau) continue
                    val borde = ile.type(x - 1, y).estTerre || ile.type(x + 1, y).estTerre ||
                        ile.type(x, y - 1).estTerre || ile.type(x, y + 1).estTerre
                    if (!borde) continue
                    drawRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                        size = taille
                    )
                }
            }

            // Le ponton, seul repère dessiné par-dessus : c'est le point
            // d'arrivée, il doit se retrouver d'un coup d'œil.
            ile.ponton?.let { p ->
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = pas * 0.32f,
                    center = Offset(
                        camera.x + p.x * pas + pas / 2f,
                        camera.y + p.y * pas + pas / 2f
                    )
                )
            }
        }
    }
}
