package com.sankailife.ui.screens.island

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sankailife.core.island.domain.IslandGenerator
import kotlin.math.floor

/**
 * Dessine une île.
 *
 * Une seule implémentation, utilisée par la carte plein écran **et** par les
 * miniatures de l'assistant de création. Deux fonctions de rendu séparées
 * finiraient par diverger, et l'aperçu montrerait alors une île différente de
 * celle qu'on obtient en la choisissant — exactement ce qu'un assistant de
 * choix ne doit jamais faire.
 */
fun DrawScope.dessinerIle(
    ile: IslandGenerator.Ile,
    camera: Offset,
    pas: Float,
    parcelles: Set<Int> = emptySet(),
    marquerPonton: Boolean = true
) {
    if (pas <= 0f) return

    // Culling : seules les cases visibles sont peintes. Sans lui, on repeint
    // mille rectangles par frame dont la plupart hors écran.
    val premierX = floor((-camera.x) / pas).toInt().coerceIn(0, ile.largeur - 1)
    val premierY = floor((-camera.y) / pas).toInt().coerceIn(0, ile.hauteur - 1)
    val dernierX = (floor((size.width - camera.x) / pas).toInt() + 1)
        .coerceIn(0, ile.largeur - 1)
    val dernierY = (floor((size.height - camera.y) / pas).toInt() + 1)
        .coerceIn(0, ile.hauteur - 1)

    // Un pixel de recouvrement : sans lui, un pas fractionnaire laisse une
    // ligne claire entre les colonnes lointaines.
    val taille = Size(pas + 1f, pas + 1f)

    for (y in premierY..dernierY) {
        for (x in premierX..dernierX) {
            drawRect(
                color = PaletteIle.couleurCase(ile.type(x, y), x, y),
                topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                size = taille
            )
        }
    }

    // Écume : là où l'eau touche la terre. C'est ce qui fait lire une côte
    // plutôt qu'un simple changement de couleur.
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

    // Parcelles achetées : un liseré, rien de plus. Un halo permanent sur
    // chaque case rendrait la carte illisible.
    if (parcelles.isNotEmpty() && pas >= 8f) {
        for (y in premierY..dernierY) {
            for (x in premierX..dernierX) {
                if (!parcelles.contains(y * ile.largeur + x)) continue
                drawRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(camera.x + x * pas + 1f, camera.y + y * pas + 1f),
                    size = Size(pas - 2f, pas - 2f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }

    // Le ponton : seul repère dessiné par-dessus. C'est le point d'arrivée, il
    // doit se retrouver d'un coup d'œil, y compris sur une miniature.
    if (marquerPonton) {
        ile.ponton?.let { p ->
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = (pas * 0.34f).coerceAtLeast(2.5f),
                center = Offset(
                    camera.x + p.x * pas + pas / 2f,
                    camera.y + p.y * pas + pas / 2f
                )
            )
        }
    }
}

/**
 * Caméra et pas qui font tenir l'île entière dans une surface donnée.
 *
 * Sert aux miniatures : elles n'ont ni déplacement ni zoom, l'île doit
 * simplement rentrer et être centrée.
 */
fun cadrerEntier(ile: IslandGenerator.Ile, largeur: Float, hauteur: Float): Pair<Offset, Float> {
    if (ile.largeur <= 0 || ile.hauteur <= 0) return Offset.Zero to 0f
    val pas = minOf(largeur / ile.largeur, hauteur / ile.hauteur)
    val camera = Offset(
        (largeur - ile.largeur * pas) / 2f,
        (hauteur - ile.hauteur * pas) / 2f
    )
    return camera to pas
}
