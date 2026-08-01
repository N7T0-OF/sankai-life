package com.sankailife.ui.screens.garden

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import com.sankailife.core.garden.domain.MimoMondeEngine
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.garden.domain.ExpansionEngine
import com.sankailife.core.garden.domain.MoistureEngine
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sankailife.ui.theme.AccentCyan
import com.sankailife.ui.theme.AccentGold
import com.sankailife.ui.theme.sankaiColors
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le terrain, en plan cartésien.
 *
 * Chaque parcelle est placée par ses coordonnées, plus par son rang dans une
 * liste. Le jardin peut donc s'étendre dans les quatre directions autour de
 * son centre, au lieu de descendre en colonnes.
 *
 * Deux gestes cohabitent sans se gêner, selon ce que le joueur tient :
 *
 * - **aucun outil** : le doigt déplace la caméra ;
 * - **un outil** : le doigt l'applique à chaque parcelle traversée.
 *
 * C'est ce qui permet d'arroser six cases d'un seul mouvement sans jamais
 * demander de confirmation.
 *
 * Seules les cases connues de la base sont composées. Le brouillard est dessiné
 * une case au-delà, sans exister en données : afficher les 1 600 positions de
 * la grille logique coûterait cher pour montrer du vide.
 */
@Composable
fun GrilleJardin(
    parcelles: List<GardenViewModel.ParcelleUi>,
    outil: OutilJardin?,
    zoom: Float,
    mimos: List<MimoMondeEngine.MimoUi>,
    modifier: Modifier = Modifier,
    onAppliquer: (Int) -> Unit,
    onOuvrirDetail: (GardenViewModel.ParcelleUi) -> Unit,
    onZoom: (Float) -> Unit,
    onOuvrirMimo: (MimoMondeEngine.MimoUi) -> Unit,
    onReposerOutil: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val densite = LocalDensity.current

    var camera by remember { mutableStateOf(Offset.Zero) }
    var cameraInitialisee by remember { mutableStateOf(false) }
    var tailleVue by remember { mutableStateOf(IntOffset.Zero) }

    // Le zoom est relu à chaque geste : une lambda de `pointerInput` capture
    // la valeur du moment où elle a été créée, et calculerait le recentrage
    // avec une échelle périmée.
    val zoomCourant = rememberUpdatedState(zoom)

    // Le maintien en cours, s'il y en a un.
    var maintien by remember { mutableStateOf<Maintien?>(null) }
    var progression by remember { mutableStateOf(0f) }

    // Compte à rebours du maintien.
    //
    // Le glissement n'applique plus rien tout seul : il fallait deux secondes
    // de doigt immobile sur la case. C'est ce qui empêche de planter six
    // graines par erreur en balayant le terrain.
    LaunchedEffect(maintien?.cle) {
        val m = maintien
        if (m == null || m.cle < 0) { progression = 0f; return@LaunchedEffect }

        progression = 0f
        val pas = 40L
        var ecoule = 0L
        while (ecoule < DUREE_MAINTIEN_MS) {
            delay(pas)
            ecoule += pas
            progression = ecoule / DUREE_MAINTIEN_MS.toFloat()
        }
        haptics.reward()
        onAppliquer(m.cle)
        progression = 0f
        // La case reste sous le doigt : on repart pour un tour, ce qui permet
        // d'enchaîner sans relever la main.
        maintien = m.copy(cle = -1)
    }

    // Le zoom agit sur la taille des cases, pas sur une transformation
    // graphique : les illustrations restent dessinées à leur résolution
    // naturelle au lieu d'être étirées, donc elles ne bavent pas.
    //
    // L'écart entre cases est négatif : les textures se chevauchent de deux
    // points et forment un terrain continu. Avec un écart positif, on voyait
    // le fond du cadre entre chaque parcelle et le jardin ressemblait à une
    // grille de boutons. Les bords irréguliers des illustrations font le
    // raccord tout seuls.
    val tailleCase = with(densite) { (78.dp * zoom).toPx() }
    val ecart = with(densite) { (-2).dp.toPx() * zoom }
    val pas = tailleCase + ecart

    val parId = remember(parcelles) { parcelles.associateBy { it.id } }

    // Cadre du terrain connu, élargi d'une case pour laisser place au
    // brouillard. Il se redimensionne tout seul à chaque déblocage.
    val minX = (parcelles.minOfOrNull { it.x } ?: ExpansionEngine.CENTRE) - 1
    val maxX = (parcelles.maxOfOrNull { it.x } ?: ExpansionEngine.CENTRE) + 1
    val minY = (parcelles.minOfOrNull { it.y } ?: ExpansionEngine.CENTRE) - 1
    val maxY = (parcelles.maxOfOrNull { it.y } ?: ExpansionEngine.CENTRE) + 1

    val colonnes = maxX - minX + 1
    val lignes = maxY - minY + 1
    val largeurTotale = colonnes * pas
    val hauteurTotale = lignes * pas

    // Les cases de brouillard : voisines du connu, mais pas encore connues.
    // Purement visuelles, elles ne reçoivent aucun geste.
    val brouillard = remember(parcelles) {
        val connues = parcelles.map { it.id }.toSet()
        connues.flatMap { ExpansionEngine.voisines(it) }
            .filter { it !in connues }
            .distinct()
    }

    /** Convertit une position à l'écran en identifiant de parcelle, ou -1. */
    fun parcelleSous(position: Offset): Int {
        val px = position.x - camera.x
        val py = position.y - camera.y
        if (px < 0 || py < 0) return -1
        val col = (px / pas).toInt()
        val ligne = (py / pas).toInt()
        if (col !in 0 until colonnes || ligne !in 0 until lignes) return -1
        // Plus de rejet dans l'entre-deux : les cases se touchent désormais,
        // il n'y a plus d'interstice où un appui pourrait se perdre.

        val cle = ExpansionEngine.cle(minX + col, minY + ligne)
        return if (parId.containsKey(cle)) cle else -1
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF16301F), Color(0xFF0E2116))))
            .border(1.dp, Color(0xFF2E5238), RoundedCornerShape(22.dp))
            .onSizeChanged { taille ->
                tailleVue = IntOffset(taille.width, taille.height)
                // Au premier affichage, la caméra se pose sur le centre du
                // terrain connu. Sans ça le joueur arriverait dans un coin.
                if (!cameraInitialisee && taille.width > 0) {
                    camera = Offset(
                        (taille.width - largeurTotale) / 2f,
                        (taille.height - hauteurTotale) / 2f
                    )
                    cameraInitialisee = true
                }
            }
            // Pincement : zoom ancré sous les doigts, et déplacement à deux
            // doigts. Capté avant le glissement, sinon un pincement passerait
            // pour deux doigts qui déplacent la caméra.
            .pointerInput(parcelles.size) {
                detectTransformGestures { centroide, deplacement, facteur, _ ->
                    if (facteur != 1f) {
                        val avant = zoomCourant.value
                        val apres = (avant * facteur).coerceIn(
                            GardenViewModel.ZOOM_MIN, GardenViewModel.ZOOM_MAX
                        )
                        // Le point du jardin sous les doigts doit rester sous
                        // les doigts. Sans cette correction, la caméra reste
                        // ancrée à son origine et tout le terrain fuit vers le
                        // coin supérieur gauche — c'était le bug signalé.
                        //
                        // On raisonne sur le rapport réel, pas sur le facteur
                        // demandé : aux bornes du zoom, le facteur est écrêté
                        // et l'appliquer tel quel décalerait la vue.
                        val rapport = apres / avant
                        camera = centroide - (centroide - camera) * rapport
                        onZoom(apres)
                    }
                    if (deplacement != Offset.Zero) {
                        camera += deplacement
                    }
                }
            }
            .pointerInput(outil, parcelles.size, zoom) {
                detectDragGestures(
                    onDragStart = { position ->
                        if (outil == null) return@detectDragGestures
                        // Le maintien démarre ici. Rien n'est appliqué : c'est
                        // le compte à rebours qui décidera.
                        maintien = Maintien(parcelleSous(position), position)
                    },
                    onDragEnd = { maintien = null },
                    onDragCancel = { maintien = null }
                ) { changement, delta ->
                    changement.consume()
                    if (outil == null) {
                        // Déplacement borné : sortir des limites donnerait
                        // l'impression d'un jardin perdu dans le vide. Quand le
                        // terrain est plus petit que l'écran, il reste centré.
                        val minCamX = (tailleVue.x - largeurTotale).coerceAtMost(0f)
                        val minCamY = (tailleVue.y - hauteurTotale).coerceAtMost(0f)
                        val maxCamX = maxOf(0f, tailleVue.x - largeurTotale)
                        val maxCamY = maxOf(0f, tailleVue.y - hauteurTotale)
                        camera = Offset(
                            (camera.x + delta.x).coerceIn(minCamX, maxCamX),
                            (camera.y + delta.y).coerceIn(minCamY, maxCamY)
                        )
                    } else {
                        // Changer de case relance le compte à rebours. Le
                        // glissement n'applique plus rien de lui-même : c'était
                        // la cause des plantations par erreur.
                        val cle = parcelleSous(changement.position)
                        if (cle != maintien?.cle) {
                            maintien = Maintien(cle, changement.position)
                        } else {
                            maintien = maintien?.copy(position = changement.position)
                        }
                    }
                }
            }
            .pointerInput(parcelles.size, outil) {
                detectTapGestures(
                    onTap = { position ->
                        val cle = parcelleSous(position)
                        val parcelle = parId[cle]
                        when {
                            // Toucher le vide en tenant un outil le repose.
                            // Sans ça, on reste prisonnier de l'objet en main.
                            parcelle == null && outil != null -> {
                                haptics.click(); onReposerOutil()
                            }
                            parcelle == null -> Unit
                            outil == null -> { haptics.click(); onOuvrirDetail(parcelle) }
                            // Avec un outil, un simple appui n'agit plus : il
                            // faut maintenir. Le détail reste consultable.
                            else -> onOuvrirDetail(parcelle)
                        }
                    },
                    onLongPress = { position ->
                        if (parcelleSous(position) < 0 && outil != null) {
                            haptics.click(); onReposerOutil()
                        }
                    }
                )
            }
    ) {
        fun placement(x: Int, y: Int) = Modifier
            .offset {
                IntOffset(
                    (camera.x + (x - minX) * pas).roundToInt(),
                    (camera.y + (y - minY) * pas).roundToInt()
                )
            }
            .size(with(densite) { tailleCase.toDp() })

        // Le brouillard d'abord : il doit passer sous les parcelles.
        //
        // Il est dessiné d'un seul tenant, en nappes qui débordent largement
        // des cases. Une image par case redonnait au brouillard la forme d'un
        // damier, et le joueur devinait le découpage du terrain qu'il est
        // justement censé ignorer.
        NappeBrouillard(
            cases = brouillard,
            minX = minX,
            minY = minY,
            pas = pas,
            camera = camera,
            modifier = Modifier.matchParentSize()
        )

        parcelles.forEach { parcelle ->
            CaseParcelle(
                parcelle = parcelle,
                surbrillance = parcelle.cultivable &&
                    outil?.applicableA(parcelle.etat) == true,
                modifier = placement(parcelle.x, parcelle.y)
            )
        }

        // Les Mimos, par-dessus les parcelles.
        //
        // Au zoom le plus faible ils sont masqués : à cette taille, cinq
        // personnages sur des cases de trente pixels forment une bouillie
        // illisible, et c'est justement la vue qu'on utilise pour embrasser
        // le terrain d'un coup d'œil.
        if (zoom > 0.75f) {
            mimos.forEach { mimo ->
                val cle = mimo.cible ?: mimo.station
                if (parId.containsKey(cle)) {
                    MimoDansLeJardin(
                        mimo = mimo,
                        modifier = placement(
                            ExpansionEngine.xDe(cle), ExpansionEngine.yDe(cle)
                        ),
                        onClic = { onOuvrirMimo(mimo) }
                    )
                }
            }
        }

        // Le cercle de maintien, dessiné sur la case visée.
        maintien?.takeIf { it.cle >= 0 && progression > 0f }?.let { m ->
            val p = parId[m.cle]
            if (p != null) {
                Box(
                    placement(p.x, p.y),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progression },
                        color = AccentCyan,
                        trackColor = Color.Black.copy(alpha = 0.35f),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (outil != null) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface1.copy(alpha = 0.9f))
                    .border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${outil.emoji}  ${outil.libelle} — glisse sur les parcelles",
                    color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Un Mimo posé sur le terrain.
 *
 * Il flotte légèrement quand il travaille, reste immobile quand il n'a rien à
 * faire, et porte un `Zzz` quand il dort. L'immobilité est un choix : un
 * personnage qui déambule sans raison donne l'impression d'un travail qui
 * n'existe pas, alors qu'un personnage arrêté dit tout de suite qu'il est
 * libre — c'est la convention des jeux de construction, et elle marche.
 */
@Composable
private fun MimoDansLeJardin(
    mimo: MimoMondeEngine.MimoUi,
    modifier: Modifier = Modifier,
    onClic: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "mimo")
    val flottement by transition.animateFloat(
        initialValue = 0f, targetValue = if (mimo.actif) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "flottementMimo"
    )

    Box(modifier, contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier.offset(y = (-6 - flottement * 4).dp).clickable { onClic() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // La bulle d'état se lit avant le personnage : c'est elle qui
            // porte l'information utile.
            if (mimo.activite.emoji.isNotEmpty() || mimo.endormi) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C1611).copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        if (mimo.endormi) "💤" else mimo.activite.emoji,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                mimo.type.emoji,
                fontSize = 18.sp,
                modifier = Modifier.alpha(if (mimo.endormi) 0.6f else 1f)
            )
        }
    }
}

/**
 * Le brouillard de l'inexploré.
 *
 * Dessiné en nappes de cercles flous qui débordent des cases et se recouvrent :
 * la limite du terrain devient irrégulière et on ne devine plus le découpage.
 * L'ancienne version posait une image carrée par case, ce qui dessinait
 * exactement la grille qu'elle devait masquer.
 *
 * Aucun modificateur de saisie : les gestes passent au travers.
 */
@Composable
private fun NappeBrouillard(
    cases: List<Int>,
    minX: Int,
    minY: Int,
    pas: Float,
    camera: Offset,
    modifier: Modifier = Modifier
) {
    if (cases.isEmpty()) return

    // Une lente respiration, pour que la nappe ne soit pas figée. Les décalages
    // sont dérivés de la position de la case : deux nappes voisines ne
    // bougent donc pas ensemble.
    val transition = rememberInfiniteTransition(label = "brouillard")
    val souffle by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "souffleBrouillard"
    )

    Canvas(modifier) {
        cases.forEach { cle ->
            val cx = camera.x + (ExpansionEngine.xDe(cle) - minX) * pas + pas / 2f
            val cy = camera.y + (ExpansionEngine.yDe(cle) - minY) * pas + pas / 2f
            val graine = (cle * 2654435761u.toInt())

            // Quatre bulles par case, décalées et de tailles différentes.
            // C'est leur recouvrement d'une case à l'autre qui efface la grille.
            for (i in 0 until 4) {
                val angle = souffle + (graine + i * 977) % 628 / 100f
                val rayon = pas * (0.42f + ((graine / (i + 3)) % 24) / 100f)
                val amplitude = pas * 0.10f
                drawCircle(
                    color = Color(0xFF0A1610).copy(alpha = 0.30f),
                    radius = rayon,
                    center = Offset(
                        cx + cos(angle) * amplitude + ((graine / (i + 5)) % 30 - 15) * pas / 100f,
                        cy + sin(angle) * amplitude + ((graine / (i + 7)) % 30 - 15) * pas / 100f
                    )
                )
            }
        }
    }
}

@Composable
private fun CaseParcelle(
    parcelle: GardenViewModel.ParcelleUi,
    surbrillance: Boolean,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors

    val transition = rememberInfiniteTransition(label = "case")
    val pulse by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseCase"
    )

    // La couleur du sol suit l'humidité, et la transition est animée : un sol
    // qui vire brutalement au brun sombre ressemblerait à un défaut d'affichage
    // plutôt qu'à de l'eau qui pénètre.
    val teinteCible = Color(MoistureEngine.teinteSol(parcelle.humidite))
    val melange by animateFloatAsState(
        targetValue = parcelle.humidite,
        animationSpec = tween(600),
        label = "humiditeSol"
    )

    val cultivable = parcelle.cultivable
    val bordure = when {
        // Une case pas encore achetée reste grisée et muette. Le contour bleu
        // qu'elle portait la faisait passer pour une case sélectionnable, alors
        // qu'elle n'appartient pas encore au joueur.
        parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER -> AccentGold.copy(alpha = 0.5f)
        parcelle.prete -> AccentGold.copy(alpha = pulse)
        else -> Color.Transparent
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        // Couche 1 — la case elle-même.
        //
        // L'illustration porte sa propre forme, ses bords irréguliers et sa
        // texture : aucun fond ni arrondi n'est dessiné par l'interface. Poser
        // une couleur derrière ferait réapparaître le carré que l'image est
        // justement censée remplacer.
        if (cultivable) {
            Image(
                painter = painterResource(ArtJardin.parcelle(parcelle.etatHumidite)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Le fondu suit l'humidité : un sol qui vire brutalement au
                // brun sombre ressemblerait à un défaut d'affichage plutôt
                // qu'à de l'eau qui pénètre la terre.
                alpha = 0.88f + 0.12f * melange,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF14201A))
            )
        }

        // Couche 2 — ce qui pousse dessus, ou l'état de la case.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER ->
                    Text("🚧", fontSize = 22.sp)

                parcelle.deblocage == ExpansionEngine.Deblocage.DECOUVERTE ->
                    IconeArt(ArtJardin.terrain(parcelle.terrain), taille = 34.dp)

                parcelle.etat == PlotState.UNCLEARED ->
                    IconeArt(
                        ArtJardin.terrain(ExpansionEngine.Terrain.ROCHEUX),
                        taille = 34.dp
                    )

                parcelle.stage != null ->
                    IconeArt(
                        ArtJardin.plante(parcelle.stage, parcelle.prete),
                        taille = 46.dp
                    )

                else -> Spacer(Modifier.height(30.dp))
            }
            // Aucun texte sur la case.
            //
            // Le prix, le temps restant, « Prêt », « Soif » : tout ça vivait
            // ici et décalait les plantes vers le haut. L'information n'a pas
            // disparu — elle est dans la fiche, qui s'ouvre en touchant la
            // case. Une case doit montrer ce qui pousse, pas le raconter.
            //
            // Seul un point de couleur subsiste : il attire l'œil sans occuper
            // de place ni pousser le dessin hors du centre.
            if (parcelle.prete || parcelle.besoinEau ||
                parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER
            ) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                parcelle.prete -> AccentGold
                                parcelle.besoinEau -> AccentCyan
                                else -> c.textSecondary
                            }
                        )
                )
            }
        }

        // Couche 3 — le cadre de guidage.
        //
        // Posé par-dessus l'illustration plutôt que dessiné avec elle : c'est
        // une indication d'interface, pas une partie du jardin. La surbrillance
        // ne s'allume que là où l'outil tenu agit vraiment.
        val cadre = if (surbrillance) AccentCyan else bordure
        if (cadre != Color.Transparent) {
            Box(
                Modifier.fillMaxSize()
                    .border(
                        width = if (surbrillance || parcelle.prete) 2.dp else 1.dp,
                        color = cadre,
                        shape = RoundedCornerShape(13.dp)
                    )
            )
        }
    }
}

/** Doigt posé sur une case, en attente de validation. */
private data class Maintien(val cle: Int, val position: Offset)

/**
 * Durée du maintien avant qu'un outil ne s'applique.
 *
 * Deux secondes : assez long pour qu'un balayage accidentel ne déclenche rien,
 * assez court pour ne pas rendre le travail à la chaîne pénible.
 */
private const val DUREE_MAINTIEN_MS = 2000L

private fun formaterCourt(minutes: Long): String = when {
    minutes <= 0 -> "Prêt"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h"
}
