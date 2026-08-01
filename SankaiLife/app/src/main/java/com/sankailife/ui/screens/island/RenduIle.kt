package com.sankailife.ui.screens.island

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sankailife.R
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandTileType
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
/**
 * Textures du terrain, chargées une fois.
 *
 * Seules celles qui existent réellement sont ici. L'eau, le sable, le bois et
 * le rocher restent des aplats : inventer une texture pour eux en recolorant
 * l'herbe donnerait un résultat pire que la couleur franche, et le dire vaut
 * mieux que de le maquiller.
 */
@Immutable
data class TexturesIle(
    val herbe: ImageBitmap,
    val solVide: ImageBitmap,
    val solPrepare: ImageBitmap,
    val solArrose: ImageBitmap
)

@Composable
fun rememberTexturesIle(): TexturesIle = TexturesIle(
    herbe = ImageBitmap.imageResource(R.drawable.plot_grass),
    solVide = ImageBitmap.imageResource(R.drawable.island_soil_empty),
    solPrepare = ImageBitmap.imageResource(R.drawable.island_soil_tilled),
    solArrose = ImageBitmap.imageResource(R.drawable.island_soil_watered)
)

/** Sol à poser sur une parcelle, selon son état. */
private fun solPour(
    textures: TexturesIle,
    parcelle: com.sankailife.core.island.data.IslandSlotEntity
): ImageBitmap = when {
    parcelle.graineId.isNotBlank() -> textures.solArrose
    parcelle.etat == "PREPARED" -> textures.solPrepare
    else -> textures.solVide
}

/**
 * De combien le sol d'une parcelle déborde de sa case.
 *
 * Réglé en comparant les deux rendus côte à côte : en dessous, les jointures
 * restent visibles ; au-delà, les parcelles mangent leurs voisines.
 */
private const val DEBORDEMENT_SOL = 1.34f

fun DrawScope.dessinerIle(
    ile: IslandGenerator.Ile,
    camera: Offset,
    pas: Float,
    parcelles: Set<Int> = emptySet(),
    batiments: List<com.sankailife.core.island.data.IslandBuildingEntity> = emptyList(),
    parcellesDetail: Map<Int, com.sankailife.core.island.data.IslandSlotEntity> = emptyMap(),
    textures: TexturesIle? = null,
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

    val entier = IntSize(pas.toInt() + 1, pas.toInt() + 1)
    for (y in premierY..dernierY) {
        for (x in premierX..dernierX) {
            val type = ile.type(x, y)
            // L'herbe a une vraie texture ; le reste reste un aplat, faute
            // d'illustration correspondante.
            if (textures != null && type == IslandTileType.GRASS && pas >= 6f) {
                drawImage(
                    image = textures.herbe,
                    dstOffset = IntOffset(
                        (camera.x + x * pas).toInt(), (camera.y + y * pas).toInt()
                    ),
                    dstSize = entier
                )
            } else {
                drawRect(
                    color = PaletteIle.couleurCase(type, x, y),
                    topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                    size = taille
                )
            }
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

    // Parcelles achetées.
    //
    // Le sol est posé PAR-DESSUS le terrain, avec ses bords irréguliers et ses
    // coins transparents : c'est ce qui fait qu'une parcelle se fond dans
    // l'herbe au lieu d'y être collée en carré. C'était le défaut le plus
    // visible de l'ancien Jardin.
    if (parcelles.isNotEmpty() && pas >= 6f) {
        for (y in premierY..dernierY) {
            for (x in premierX..dernierX) {
                val cle = y * ile.largeur + x
                if (cle !in parcelles) continue
                val parcelle = parcellesDetail[cle]
                if (textures != null && parcelle != null) {
                    // Le sol déborde de sa case, et c'est tout l'intérêt.
                    //
                    // Dessiné à la taille exacte, chaque parcelle garde ses
                    // bords irréguliers et laisse voir l'herbe entre elles :
                    // seize parcelles voisines font seize blocs séparés au lieu
                    // d'un champ. En débordant, les bords se recouvrent, le
                    // champ devient continu, et seule sa bordure extérieure
                    // reste découpée.
                    val cote = (pas * DEBORDEMENT_SOL).toInt() + 1
                    val marge = (cote - pas.toInt()) / 2
                    drawImage(
                        image = solPour(textures, parcelle),
                        dstOffset = IntOffset(
                            (camera.x + x * pas).toInt() - marge,
                            (camera.y + y * pas).toInt() - marge
                        ),
                        dstSize = IntSize(cote, cote)
                    )
                } else {
                    drawRect(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = Offset(camera.x + x * pas + 1f, camera.y + y * pas + 1f),
                        size = Size(pas - 2f, pas - 2f),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }

    // Les bâtiments, dessinés sur toute leur emprise et non case par case :
    // un bâtiment 2 × 2 est un objet, pas quatre carrés voisins.
    batiments.forEach { batiment ->
        val type = com.sankailife.core.island.domain.IslandBuildingEngine.Type
            .parId(batiment.type) ?: return@forEach
        drawRect(
            color = Color(0xFF8D6E45),
            topLeft = Offset(
                camera.x + batiment.origineX * pas,
                camera.y + batiment.origineY * pas
            ),
            size = Size(type.largeur * pas, type.hauteur * pas)
        )
        drawRect(
            color = Color(0xFF5C452B),
            topLeft = Offset(
                camera.x + batiment.origineX * pas,
                camera.y + batiment.origineY * pas
            ),
            size = Size(type.largeur * pas, type.hauteur * pas),
            style = Stroke(width = (pas * 0.08f).coerceIn(1f, 4f))
        )
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
