package com.sankailife.ui.screens.garden

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
    onAppliquer: (Int) -> Unit,
    onOuvrirDetail: (GardenViewModel.ParcelleUi) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val densite = LocalDensity.current

    var camera by remember { mutableStateOf(Offset.Zero) }
    var cameraInitialisee by remember { mutableStateOf(false) }
    var tailleVue by remember { mutableStateOf(IntOffset.Zero) }

    // Parcelles déjà traitées pendant le glissement courant : sans cette
    // mémoire, un doigt qui tremble sur une case l'arroserait plusieurs fois.
    val dejaTraitees = remember { mutableStateListOf<Int>() }

    val tailleCase = with(densite) { 76.dp.toPx() }
    val ecart = with(densite) { 8.dp.toPx() }
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
        // Rejette les touches tombant dans l'écart entre deux cases.
        if (px % pas > tailleCase || py % pas > tailleCase) return -1

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
            .pointerInput(outil, parcelles.size) {
                detectDragGestures(
                    onDragStart = { position ->
                        dejaTraitees.clear()
                        if (outil != null) {
                            val cle = parcelleSous(position)
                            if (cle >= 0) {
                                dejaTraitees.add(cle)
                                haptics.click()
                                onAppliquer(cle)
                            }
                        }
                    },
                    onDragEnd = { dejaTraitees.clear() },
                    onDragCancel = { dejaTraitees.clear() }
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
                        val cle = parcelleSous(changement.position)
                        if (cle >= 0 && cle !in dejaTraitees) {
                            dejaTraitees.add(cle)
                            haptics.click()
                            onAppliquer(cle)
                        }
                    }
                }
            }
            .pointerInput(parcelles.size, outil) {
                detectTapGestures { position ->
                    val cle = parcelleSous(position)
                    val parcelle = parId[cle] ?: return@detectTapGestures
                    haptics.click()
                    // Un tap avec outil applique, sans outil il inspecte.
                    if (outil != null) onAppliquer(cle) else onOuvrirDetail(parcelle)
                }
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
        brouillard.forEach { cle ->
            CaseBrouillard(
                modifier = placement(ExpansionEngine.xDe(cle), ExpansionEngine.yDe(cle))
            )
        }

        parcelles.forEach { parcelle ->
            CaseParcelle(
                parcelle = parcelle,
                surbrillance = parcelle.cultivable &&
                    outil?.applicableA(parcelle.etat) == true,
                modifier = placement(parcelle.x, parcelle.y)
            )
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
 * Une case inexplorée.
 *
 * Elle n'existe pas en base et ne réagit à rien : c'est une silhouette qui
 * indique qu'il y a quelque chose plus loin, sans dire quoi.
 */
@Composable
private fun CaseBrouillard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF0A1610).copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        IconeArt(ArtJardin.brouillard, taille = 34.dp, modifier = Modifier.alpha(0.4f))
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
        parcelle.deblocage == ExpansionEngine.Deblocage.DECOUVERTE -> AccentCyan.copy(alpha = 0.45f)
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
            Spacer(Modifier.height(1.dp))
            Text(
                when {
                    parcelle.deblocage == ExpansionEngine.Deblocage.DECOUVERTE ->
                        "${parcelle.coutDeblocage} 🪙"
                    parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER ->
                        formaterCourt(parcelle.minutesChantier)
                    parcelle.etat == PlotState.UNCLEARED -> "Pierres"
                    parcelle.etat == PlotState.EMPTY -> parcelle.etatHumidite.libelle
                    parcelle.prete -> "Prêt"
                    parcelle.besoinEau -> "Soif"
                    else -> formaterCourt(parcelle.minutesRestantes)
                },
                color = when {
                    parcelle.deblocage == ExpansionEngine.Deblocage.DECOUVERTE -> AccentCyan
                    parcelle.prete -> AccentGold
                    parcelle.besoinEau -> AccentCyan
                    else -> c.textSecondary
                },
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
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

private fun formaterCourt(minutes: Long): String = when {
    minutes <= 0 -> "Prêt"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h"
}
