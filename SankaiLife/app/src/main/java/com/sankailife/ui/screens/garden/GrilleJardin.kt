package com.sankailife.ui.screens.garden

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.theme.AccentCyan
import com.sankailife.ui.theme.AccentGold
import com.sankailife.ui.theme.sankaiColors
import kotlin.math.roundToInt

/**
 * Terrain déplaçable avec application d'outil par glissement.
 *
 * Deux gestes cohabitent sans se gêner, grâce à un principe simple : le sens
 * du glissement dépend de ce que le joueur tient en main.
 *
 * - **Aucun outil** : le doigt déplace la caméra.
 * - **Un outil sélectionné** : le doigt l'applique à chaque parcelle traversée.
 *
 * C'est ce qui permet d'arroser six cases d'un seul mouvement sans jamais
 * demander de confirmation, tout en gardant un terrain plus grand que l'écran.
 */
@Composable
fun GrilleJardin(
    parcelles: List<GardenViewModel.ParcelleUi>,
    colonnes: Int,
    outil: OutilJardin?,
    modifier: Modifier = Modifier,
    onAppliquer: (Int) -> Unit,
    onOuvrirDetail: (GardenViewModel.ParcelleUi) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val densite = LocalDensity.current

    // Position de la caméra, en pixels.
    var camera by remember { mutableStateOf(Offset.Zero) }
    var tailleVue by remember { mutableStateOf(IntOffset.Zero) }

    // Parcelles déjà traitées pendant le glissement courant : sans cette
    // mémoire, un doigt qui tremble sur une case l'arroserait plusieurs fois.
    val dejaTraitees = remember { mutableStateListOf<Int>() }

    val tailleCase = with(densite) { 78.dp.toPx() }
    val ecart = with(densite) { 8.dp.toPx() }
    val pas = tailleCase + ecart
    val lignes = (parcelles.size + colonnes - 1) / colonnes

    val largeurTotale = colonnes * pas
    val hauteurTotale = lignes * pas

    /** Convertit une position à l'écran en index de parcelle, ou -1. */
    fun indexDepuisPosition(position: Offset): Int {
        val x = position.x - camera.x
        val y = position.y - camera.y
        if (x < 0 || y < 0) return -1
        val col = (x / pas).toInt()
        val ligne = (y / pas).toInt()
        if (col !in 0 until colonnes || ligne !in 0 until lignes) return -1
        // Rejette les touches tombant dans l'écart entre deux cases.
        if (x % pas > tailleCase || y % pas > tailleCase) return -1
        val index = ligne * colonnes + col
        return if (index in parcelles.indices) index else -1
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF16301F), Color(0xFF0E2116)))
            )
            .border(1.dp, Color(0xFF2E5238), RoundedCornerShape(22.dp))
            .onSizeChanged { tailleVue = IntOffset(it.width, it.height) }
            .pointerInput(outil, parcelles.size) {
                detectDragGestures(
                    onDragStart = { position ->
                        dejaTraitees.clear()
                        if (outil != null) {
                            val index = indexDepuisPosition(position)
                            if (index >= 0) {
                                dejaTraitees.add(index)
                                haptics.click()
                                onAppliquer(index)
                            }
                        }
                    },
                    onDragEnd = { dejaTraitees.clear() },
                    onDragCancel = { dejaTraitees.clear() }
                ) { changement, delta ->
                    changement.consume()
                    if (outil == null) {
                        // Déplacement de caméra, borné au terrain : sortir des
                        // limites donnerait l'impression d'un jardin perdu.
                        val minX = (tailleVue.x - largeurTotale).coerceAtMost(0f)
                        val minY = (tailleVue.y - hauteurTotale).coerceAtMost(0f)
                        camera = Offset(
                            (camera.x + delta.x).coerceIn(minX, 0f),
                            (camera.y + delta.y).coerceIn(minY, 0f)
                        )
                    } else {
                        val index = indexDepuisPosition(changement.position)
                        if (index >= 0 && index !in dejaTraitees) {
                            dejaTraitees.add(index)
                            haptics.click()
                            onAppliquer(index)
                        }
                    }
                }
            }
            .pointerInput(parcelles.size, outil) {
                detectTapGestures { position ->
                    val index = indexDepuisPosition(position)
                    if (index >= 0) {
                        val parcelle = parcelles[index]
                        haptics.click()
                        // Un tap avec outil applique, sans outil il inspecte.
                        if (outil != null) onAppliquer(index) else onOuvrirDetail(parcelle)
                    }
                }
            }
    ) {
        parcelles.forEachIndexed { index, parcelle ->
            val col = index % colonnes
            val ligne = index / colonnes
            CaseParcelle(
                parcelle = parcelle,
                surbrillance = outil?.applicableA(parcelle.etat) == true,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (camera.x + col * pas).roundToInt(),
                            (camera.y + ligne * pas).roundToInt()
                        )
                    }
                    .size(with(densite) { tailleCase.toDp() })
            )
        }

        // Indication de l'outil tenu, en surimpression.
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

    val (fond, bordure) = when {
        parcelle.etat == PlotState.LOCKED -> Color(0xFF1A1D1B) to c.border
        parcelle.etat == PlotState.UNCLEARED -> Color(0xFF2A2622) to Color(0xFF4A413A)
        parcelle.prete -> Color(0xFF3B2F16) to AccentGold.copy(alpha = pulse)
        else -> Color(0xFF3A2A1C) to Color(0xFF6B4B30)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(fond)
            .border(
                width = if (surbrillance || parcelle.prete) 2.dp else 1.dp,
                // La surbrillance guide le geste : seules les cases où l'outil
                // agit vraiment s'allument.
                color = if (surbrillance) AccentCyan else bordure,
                shape = RoundedCornerShape(13.dp)
            ),
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
                fontSize = 22.sp,
                color = if (parcelle.etat == PlotState.EMPTY) c.textSecondary else Color.Unspecified
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    parcelle.etat == PlotState.LOCKED -> "Arène ${parcelle.areneRequise}"
                    parcelle.etat == PlotState.UNCLEARED -> "Pierres"
                    parcelle.etat == PlotState.EMPTY -> "Libre"
                    parcelle.prete -> "Prêt"
                    parcelle.besoinEau -> "Soif"
                    parcelle.minutesRestantes < 60 -> "${parcelle.minutesRestantes}m"
                    else -> "${parcelle.minutesRestantes / 60}h"
                },
                color = when {
                    parcelle.prete -> AccentGold
                    parcelle.besoinEau -> AccentCyan
                    else -> c.textSecondary
                },
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
