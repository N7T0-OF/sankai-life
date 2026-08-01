package com.sankailife.ui.screens.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sankailife.core.island.data.IslandBuildingEntity
import com.sankailife.core.island.domain.IslandGenerator
import kotlin.math.floor

/**
 * Vue d'ensemble de l'île, et déplacement rapide.
 *
 * Sur une île de trente-deux cases de côté, on n'en voit qu'un quart à la
 * fois : sans repère global, retrouver sa ferme oblige à balayer la carte.
 *
 * Le rendu passe par la même fonction que la carte plein écran. Une seconde
 * implémentation « simplifiée » finirait par montrer une île qui n'est pas
 * celle qu'on parcourt.
 */
@Composable
fun MiniCarte(
    ile: IslandGenerator.Ile,
    parcelles: Set<Int>,
    batiments: List<IslandBuildingEntity>,
    /** Rectangle actuellement visible, en cases. */
    vue: androidx.compose.ui.geometry.Rect?,
    onAller: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .width(132.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(PaletteIle.couleur(com.sankailife.core.island.domain.IslandTileType.DEEP_WATER))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .pointerInput(ile.seed) {
                detectTapGestures { position ->
                    val pas = size.width.toFloat() / ile.largeur
                    if (pas <= 0f) return@detectTapGestures
                    val x = floor(position.x / pas).toInt().coerceIn(0, ile.largeur - 1)
                    val y = floor(position.y / pas).toInt().coerceIn(0, ile.hauteur - 1)
                    onAller(x, y)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val (camera, pas) = cadrerEntier(ile, size.width, size.height)
            // Pas de textures ici : à trois pixels par case, une image de
            // 256 px coûte cher pour rendre exactement la couleur d'un aplat.
            dessinerIle(
                ile = ile, camera = camera, pas = pas,
                parcelles = parcelles, batiments = batiments
            )

            // Le cadre de ce qu'on voit. C'est lui qui transforme une vignette
            // décorative en repère utilisable.
            vue?.let { r ->
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(camera.x + r.left * pas, camera.y + r.top * pas),
                    size = Size(r.width * pas, r.height * pas),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}
