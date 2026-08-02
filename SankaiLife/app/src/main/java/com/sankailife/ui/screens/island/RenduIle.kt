package com.sankailife.ui.screens.island

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sankailife.R
import com.sankailife.core.garden.domain.ArbreSankaiEngine
import com.sankailife.core.garden.domain.MimoEngine
import androidx.compose.ui.graphics.asImageBitmap
import com.sankailife.core.island.domain.AutotuilageEngine
import com.sankailife.core.island.domain.IslandForetEngine
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandMimoMondeEngine
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
 * Seules celles qui existent réellement sont ici. Le rocher et la rivière
 * restent des aplats : mes essais de texture pour eux donnaient un résultat
 * pire que la couleur franche, et le dire vaut mieux que de le maquiller.
 */
@Immutable
data class TexturesIle(
    val herbe: ImageBitmap,
    val solVide: ImageBitmap,
    val solPrepare: ImageBitmap,
    val solArrose: ImageBitmap,
    val arbre: ImageBitmap,
    val eauProfonde: ImageBitmap,
    val eauBasse: ImageBitmap,
    val sable: ImageBitmap,
    /**
     * Les seize variantes de chaque terrain, une par combinaison de voisins.
     *
     * Pré-composées au chargement plutôt qu'à chaque frame : appliquer un
     * masque à une texture demande un calque intermédiaire, et en ouvrir un par
     * case et par frame coûterait plus cher que tout le reste du rendu réuni.
     *
     * Trois terrains × seize masques à 96 pixels de côté, soit environ 1,8 Mo
     * en mémoire. L'eau profonde n'y est pas : c'est le fond, elle n'a pas de
     * bord à adoucir.
     */
    val variantes: Map<AutotuilageEngine.Couche, List<ImageBitmap>>,
    /**
     * Les six âges d'une plante, du semis à la récolte.
     *
     * Dans l'ordre : c'est un index, pas une liste au hasard, et
     * [spritePlante] compte dessus.
     */
    val plantes: List<ImageBitmap>
)

/**
 * Compose une texture et un masque en une seule image.
 *
 * `DST_IN` garde la texture là où le masque est opaque et l'efface ailleurs :
 * c'est ce qui donne au terrain un bord irrégulier au lieu d'un carré.
 */
private fun composer(
    texture: android.graphics.Bitmap,
    masque: android.graphics.Bitmap,
    cote: Int
): ImageBitmap {
    val sortie = android.graphics.Bitmap.createBitmap(
        cote, cote, android.graphics.Bitmap.Config.ARGB_8888
    )
    val toile = android.graphics.Canvas(sortie)
    val cadre = android.graphics.Rect(0, 0, cote, cote)
    toile.drawBitmap(texture, null, cadre, null)
    val pinceau = android.graphics.Paint().apply {
        isAntiAlias = true
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }
    toile.drawBitmap(masque, null, cadre, pinceau)
    return sortie.asImageBitmap()
}

/** Côté des variantes composées. Assez pour du bruit, assez peu pour la mémoire. */
private const val COTE_VARIANTE = 96

@Composable
fun rememberTexturesIle(): TexturesIle = TexturesIle(
    herbe = ImageBitmap.imageResource(R.drawable.plot_grass),
    solVide = ImageBitmap.imageResource(R.drawable.island_soil_empty),
    solPrepare = ImageBitmap.imageResource(R.drawable.island_soil_tilled),
    solArrose = ImageBitmap.imageResource(R.drawable.island_soil_watered),
    arbre = ImageBitmap.imageResource(R.drawable.tree_sankai),
    eauProfonde = ImageBitmap.imageResource(R.drawable.island_deep_water),
    eauBasse = ImageBitmap.imageResource(R.drawable.island_shallow_water),
    sable = ImageBitmap.imageResource(R.drawable.island_beach),
    variantes = rememberVariantes(),
    plantes = listOf(
        ImageBitmap.imageResource(R.drawable.plant_stage_0_seed),
        ImageBitmap.imageResource(R.drawable.plant_stage_1),
        ImageBitmap.imageResource(R.drawable.plant_stage_2),
        ImageBitmap.imageResource(R.drawable.plant_stage_3),
        ImageBitmap.imageResource(R.drawable.plant_stage_4),
        ImageBitmap.imageResource(R.drawable.plant_stage_5_ready)
    )
)

/**
 * Sprite correspondant à l'état d'une culture.
 *
 * `prete` n'est pas un stade de croissance mais un état calculé : une plante
 * arrivée au bout du cycle change d'aspect pour se signaler, sinon rien ne
 * distingue une récolte qui attend d'une plante qui pousse encore. Même règle
 * qu'au Jardin, et volontairement la même image.
 */
private fun spritePlante(
    textures: TexturesIle,
    culture: com.sankailife.core.garden.domain.CropGrowthEngine.Etat
): ImageBitmap {
    if (culture.prete) return textures.plantes.last()
    val index = com.sankailife.core.garden.domain.CropStage.entries
        .indexOf(culture.stage).coerceIn(0, textures.plantes.size - 2)
    return textures.plantes[index]
}

/**
 * Les variantes masquées de chaque terrain.
 *
 * `remember` sans clé : les seize masques et les trois textures ne changent
 * jamais, donc la composition n'a lieu qu'une fois par écran.
 */
@Composable
private fun rememberVariantes(): Map<AutotuilageEngine.Couche, List<ImageBitmap>> {
    val contexte = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        val options = android.graphics.BitmapFactory.Options().apply {
            inScaled = false
        }
        fun charger(id: Int) =
            android.graphics.BitmapFactory.decodeResource(contexte.resources, id, options)

        val masques = (0 until AutotuilageEngine.MASQUES).map { i ->
            charger(
                contexte.resources.getIdentifier(
                    "tuile_transition_%02d".format(i), "drawable", contexte.packageName
                )
            )
        }
        mapOf(
            AutotuilageEngine.Couche.BASSE to R.drawable.island_shallow_water,
            AutotuilageEngine.Couche.SABLE to R.drawable.island_beach,
            AutotuilageEngine.Couche.TERRE to R.drawable.plot_grass
        ).mapValues { (_, res) ->
            val texture = charger(res)
            masques.map { composer(texture, it, COTE_VARIANTE) }
        }
    }
}

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

/**
 * De combien le dessin d'une plante déborde de sa case.
 *
 * Légèrement plus grand que la case : une plante mûre qui tient pile dans son
 * carré a l'air taillée aux ciseaux. Bien moins que le sol, en revanche — au
 * -delà, les feuilles de deux parcelles voisines se chevauchent et le champ
 * devient une bouillie verte.
 */
private const val DEBORDEMENT_PLANTE = 1.12f

/**
 * Cases d'océan dessinées au-delà des bords de l'île.
 *
 * Assez pour qu'aucun zoom raisonnable ne montre la fin du monde, assez peu
 * pour ne pas peindre un océan sans fin au dézoom maximum.
 */
private const val MARGE_OCEAN = 24

fun DrawScope.dessinerIle(
    ile: IslandGenerator.Ile,
    camera: Offset,
    pas: Float,
    parcelles: Set<Int> = emptySet(),
    batiments: List<com.sankailife.core.island.data.IslandBuildingEntity> = emptyList(),
    parcellesDetail: Map<Int, com.sankailife.core.island.data.IslandSlotEntity> = emptyMap(),
    textures: TexturesIle? = null,
    arbres: List<com.sankailife.core.island.domain.IslandForetEngine.Arbre> = emptyList(),
    mimos: List<IslandMimoMondeEngine.Place> = emptyList(),
    cultures: Map<Int, com.sankailife.core.garden.domain.CropGrowthEngine.Etat> = emptyMap(),
    /** Emprise en cours de placement : type, coin haut-gauche, et validite. */
    apercu: Triple<com.sankailife.core.island.domain.IslandBuildingEngine.Type, IntOffset, Boolean>?
        = null,
    marquerPonton: Boolean = true
) {
    if (pas <= 0f) return

    // Culling : seules les cases visibles sont peintes. Sans lui, on repeint
    // des milliers de rectangles par frame dont la plupart hors écran.
    //
    // Le balayage déborde volontairement de l'île. En s'arrêtant à ses bords,
    // l'océan cessait net là où la grille s'arrêtait : on voyait un carré d'eau
    // texturée posé sur un fond uni, et donc la limite du monde. `ile.type()`
    // rend de l'eau profonde hors des bornes, il suffit de le laisser faire.
    //
    // La marge est bornée pour que dézoomer au maximum ne fasse pas peindre un
    // océan sans fin.
    val premierX = maxOf(floor((-camera.x) / pas).toInt(), -MARGE_OCEAN)
    val premierY = maxOf(floor((-camera.y) / pas).toInt(), -MARGE_OCEAN)
    val dernierX = minOf(
        floor((size.width - camera.x) / pas).toInt() + 1,
        ile.largeur - 1 + MARGE_OCEAN
    )
    val dernierY = minOf(
        floor((size.height - camera.y) / pas).toInt() + 1,
        ile.hauteur - 1 + MARGE_OCEAN
    )
    if (dernierX < premierX || dernierY < premierY) return

    // Un pixel de recouvrement : sans lui, un pas fractionnaire laisse une
    // ligne claire entre les colonnes lointaines.
    val taille = Size(pas + 1f, pas + 1f)

    val entier = IntSize(pas.toInt() + 1, pas.toInt() + 1)

    // Le terrain, en couches successives plutôt qu'en carrés juxtaposés.
    //
    // C'était le défaut le plus visible de l'île : chaque case étant un carré
    // plein, une côte était un escalier, et le terrain se lisait comme une
    // grille au lieu d'un paysage.
    //
    // Le fond est peint d'abord — l'océan, partout — puis chaque terrain
    // supérieur vient par-dessus à travers un masque au bord irrégulier. Une
    // case d'herbe porte donc aussi le sable et l'eau basse qu'elle recouvre,
    // sans quoi on verrait l'océan à travers ses bords adoucis.
    //
    // Les masques ne servent qu'à partir d'une certaine taille : en dessous, un
    // bord irrégulier de deux pixels ne se distingue pas d'un bord droit, et on
    // paierait trois passes de dessin pour rien.
    val autotuilage = textures != null && pas >= 10f

    for (y in premierY..dernierY) {
        for (x in premierX..dernierX) {
            val type = ile.type(x, y)
            val fond = when {
                // L'ocean n'est jamais texture, a aucun zoom.
                //
                // La texture se repetait sur toute la surface : a l'echelle d'un
                // ecran, l'oeil retrouve immediatement le carreau et le motif
                // attire l'attention loin de l'ile, qui est le sujet. La regle
                // passe avant l'autotuilage, sinon la miniature et l'assistant
                // de creation — qui dessinent trop petit pour l'autotuilage —
                // continuaient d'afficher le motif.
                type == IslandTileType.DEEP_WATER -> null
                autotuilage -> textures!!.eauProfonde
                textures == null || pas < 6f -> null
                // Sans autotuilage, chaque case garde sa propre texture.
                type == IslandTileType.GRASS || type == IslandTileType.FOREST -> textures.herbe
                type == IslandTileType.SHALLOW_WATER -> textures.eauBasse
                type == IslandTileType.BEACH || type == IslandTileType.DOCK -> textures.sable
                else -> null
            }
            if (fond != null) {
                drawImage(
                    image = fond,
                    dstOffset = IntOffset(
                        (camera.x + x * pas).toInt(), (camera.y + y * pas).toInt()
                    ),
                    dstSize = entier
                )
            } else {
                // Le rocher et la rivière restent des aplats : mes essais de
                // texture pour eux donnaient un résultat pire que la couleur
                // franche, et le dire vaut mieux que de le maquiller.
                //
                // L'océan, lui, est volontairement **uni** : la variation par
                // case y dessinait un damier visible sur les grandes surfaces,
                // exactement ce qu'on cherche à faire disparaître.
                val couleur = if (type == IslandTileType.DEEP_WATER) {
                    PaletteIle.couleur(type)
                } else {
                    PaletteIle.couleurCase(type, x, y)
                }
                drawRect(
                    color = couleur,
                    topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                    size = taille
                )
            }
        }
    }

    if (autotuilage) {
        val variantes = textures!!.variantes
        AutotuilageEngine.COUCHES_SUPERIEURES.forEach { couche ->
            val images = variantes[couche] ?: return@forEach
            for (y in premierY..dernierY) {
                for (x in premierX..dernierX) {
                    val type = ile.type(x, y)
                    if (!AutotuilageEngine.concernee(type, couche)) continue
                    val code = AutotuilageEngine.code(x, y, couche) { cx, cy -> ile.type(cx, cy) }
                    drawImage(
                        image = images[code],
                        dstOffset = IntOffset(
                            (camera.x + x * pas).toInt(), (camera.y + y * pas).toInt()
                        ),
                        dstSize = entier
                    )
                }
            }
        }

        // Le rocher et la rivière par-dessus : ils partagent la couche de leurs
        // voisins mais gardent leur couleur propre.
        for (y in premierY..dernierY) {
            for (x in premierX..dernierX) {
                val type = ile.type(x, y)
                if (type != IslandTileType.ROCK && type != IslandTileType.RIVER &&
                    type != IslandTileType.POND
                ) continue
                drawRect(
                    color = PaletteIle.couleurCase(type, x, y),
                    topLeft = Offset(camera.x + x * pas, camera.y + y * pas),
                    size = taille
                )
            }
        }
    }

    // L'ecume est retiree.
    //
    // Elle peignait un carre blanc a 22 % sur chaque case d'eau bordant la
    // terre. L'intention etait de faire lire une cote ; le resultat etait une
    // rangee de carres pales parfaitement alignes sur la grille — c'est-a-dire
    // exactement ce que l'autotuilage venait d'effacer. Un carre ne suggere pas
    // une ecume, il montre une case.
    //
    // Le bord irregulier du sable sur l'eau fait desormais ce travail, et le
    // fait mieux : il suit la cote au lieu de la quadriller.

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

        // La plante, posée sur son sol.
        //
        // Sans elle, semer puis arroser ne changeait rien à l'écran : la
        // parcelle restait un rectangle de terre du semis à la récolte. Toute
        // la boucle du jeu — réviser pour gagner de l'eau, arroser, revenir
        // voir — n'avait aucun retour visible, et c'est bien cette boucle qui
        // relie l'île à l'apprentissage.
        //
        // Dessinée avec le sol et non avec les arbres : une pousse est basse,
        // et un Mimo qui passe devant une parcelle doit la masquer, pas
        // l'inverse.
        if (textures != null && cultures.isNotEmpty()) {
            for (y in premierY..dernierY) {
                for (x in premierX..dernierX) {
                    val culture = cultures[y * ile.largeur + x] ?: continue
                    // Le sprite déborde un peu de sa case et s'ancre en bas :
                    // une plante pousse depuis la terre, elle n'est pas centrée
                    // dans un carré.
                    val cote = (pas * DEBORDEMENT_PLANTE).toInt().coerceAtLeast(1)
                    drawImage(
                        image = spritePlante(textures, culture),
                        dstOffset = IntOffset(
                            (camera.x + x * pas + (pas - cote) / 2f).toInt(),
                            (camera.y + (y + 1) * pas - cote).toInt()
                        ),
                        dstSize = IntSize(cote, cote)
                    )
                }
            }
        }
    }

    // Les bâtiments, dessinés sur toute leur emprise et non case par case :
    // un bâtiment 2 × 2 est un objet, pas quatre carrés voisins.
    val maintenant = System.currentTimeMillis()
    batiments.forEach { batiment ->
        val type = com.sankailife.core.island.domain.IslandBuildingEngine.Type
            .parId(batiment.type) ?: return@forEach

        // Un chantier se distingue d'un bâtiment fini : sinon on croit avoir
        // construit et on s'étonne que rien ne fonctionne.
        val fini = com.sankailife.core.island.domain.IslandBuildingEngine
            .enService(batiment.chantierFinMillis, maintenant)

        drawRect(
            color = if (fini) Color(0xFF8D6E45) else Color(0xFF6B5B46),
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

        // Avancement, dessiné comme une bande qui monte : on voit d'un coup
        // d'œil où en est le chantier sans ouvrir la fiche.
        if (!fini) {
            val avancement = com.sankailife.core.island.domain.IslandBuildingEngine
                .avancement(batiment.chantierFinMillis, type.chantierMinutes, maintenant)
            val hauteur = type.hauteur * pas * avancement
            drawRect(
                color = Color(0xFFFFD54F).copy(alpha = 0.45f),
                topLeft = Offset(
                    camera.x + batiment.origineX * pas,
                    camera.y + (batiment.origineY + type.hauteur) * pas - hauteur
                ),
                size = Size(type.largeur * pas, hauteur)
            )
        }
    }

    // Arbres et Mimos, dessinés ensemble et triés par profondeur.
    //
    // Ensemble, et c'est le point : deux passes séparées mettraient toujours
    // l'une devant l'autre. Un Mimo posé au sud d'un arbre passerait derrière
    // son feuillage, ou un Mimo lointain se dessinerait par-dessus la cime d'un
    // arbre proche. La profondeur est le bas de l'objet — pied du Mimo, base de
    // l'emprise de l'arbre — parce que c'est là qu'il touche le sol.
    //
    // L'arbre : le tronc est aligné sur l'ancrage mesuré du fichier — ni le
    // centre ni le bas de l'image — et le feuillage déborde volontairement de
    // l'emprise, une couronne ronde limitée à ses cases ayant l'air taillée au
    // carré.
    if (pas >= 5f && (arbres.isNotEmpty() || mimos.isNotEmpty())) {
        val visibles = buildList<Pair<Float, () -> Unit>> {
            if (textures != null) {
                arbres.forEach { arbre ->
                    if (arbre.x + 2 < premierX || arbre.x - 2 > dernierX ||
                        arbre.y + 2 < premierY || arbre.y - 2 > dernierY
                    ) return@forEach
                    val profondeur = arbre.y + arbre.taille.emprise.hauteur.toFloat()
                    add(profondeur to {
                        val tronc = ArbreSankaiEngine.troncEcran(
                            origineEcran = Pair(
                                camera.x + arbre.x * pas,
                                camera.y + arbre.y * pas
                            ),
                            pas = pas,
                            taille = arbre.taille
                        )
                        val (gauche, haut, cote) = ArbreSankaiEngine.cadreDessin(
                            tronc = tronc,
                            pas = pas * IslandForetEngine.echelle(arbre.x, arbre.y),
                            taille = arbre.taille
                        )
                        drawImage(
                            image = textures.arbre,
                            dstOffset = IntOffset(gauche.toInt(), haut.toInt()),
                            dstSize = IntSize(
                                cote.toInt().coerceAtLeast(1), cote.toInt().coerceAtLeast(1)
                            )
                        )
                    })
                }
            }
            mimos.forEach { mimo ->
                if (mimo.x + 1 < premierX || mimo.x - 1 > dernierX ||
                    mimo.y + 1 < premierY || mimo.y - 1 > dernierY
                ) return@forEach
                add((mimo.y + 1f) to { dessinerMimo(mimo, camera, pas) })
            }
        }
        visibles.sortedBy { it.first }.forEach { it.second() }
    }

    // L'emprise du batiment qu'on est en train de placer.
    //
    // Dessinee par-dessus tout le reste, y compris les arbres : c'est une
    // intention, pas un objet du monde, et elle doit rester visible meme
    // au-dessus d'un feuillage.
    //
    // La couleur dit le verdict avant qu'on paie. Le contour plein plutot qu'un
    // simple remplissage : sur un terrain deja vert, un voile vert ne se
    // distingue pas.
    apercu?.let { (type, coin, possible) ->
        val couleur = if (possible) Color(0xFF6FD17A) else Color(0xFFE06C6C)
        val largeur = type.largeur * pas
        val hauteur = type.hauteur * pas
        val coinHaut = Offset(camera.x + coin.x * pas, camera.y + coin.y * pas)

        drawRect(
            color = couleur.copy(alpha = 0.28f),
            topLeft = coinHaut,
            size = Size(largeur, hauteur)
        )
        drawRect(
            color = couleur,
            topLeft = coinHaut,
            size = Size(largeur, hauteur),
            style = Stroke(width = (pas * 0.10f).coerceIn(2f, 6f))
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
 * Couleur de la blouse d'un Mimo, par métier.
 *
 * Le métier doit se lire de loin, quand l'emoji d'activité est encore trop
 * petit pour être dessiné : sans cela, cinq employés identiques ne disent rien
 * de ce qu'on a acheté.
 */
private fun couleurMimo(type: MimoEngine.Type): Color = when (type) {
    MimoEngine.Type.ARROSEUR -> Color(0xFF4FA8D8)
    MimoEngine.Type.RECOLTEUR -> Color(0xFFE0A03C)
    MimoEngine.Type.TRANSPORTEUR -> Color(0xFF9C7A56)
    MimoEngine.Type.VENDEUR -> Color(0xFFD4B44A)
    MimoEngine.Type.PLANTEUR -> Color(0xFF6FA84F)
}

/**
 * Pinceau à texte, réutilisé.
 *
 * Un `Paint` neuf par Mimo et par frame allouerait des dizaines d'objets par
 * seconde pour trois caractères. Le dessin se fait sur un seul fil, donc le
 * partager est sans danger ici.
 */
private val pinceauEmoji = android.graphics.Paint().apply {
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
}

/** En dessous, l'emoji d'activité serait une tache illisible. */
private const val PAS_MIN_EMOJI = 26f

/**
 * Dessine un Mimo.
 *
 * Une figure simple, tracée et non illustrée : le projet n'a pas de sprite de
 * Mimo, et je ne sais pas en peindre un. Une silhouette lisible vaut mieux
 * qu'un carré, et mieux qu'une illustration empruntée.
 *
 * L'emoji au-dessus de la tête dit l'activité — mais l'activité est un **état
 * de l'île**, pas un travail en cours : voir [IslandMimoMondeEngine]. Un Mimo
 * marqué 💧 signale une parcelle assoiffée ; il ne l'arrose pas sous vos yeux.
 */
private fun DrawScope.dessinerMimo(
    mimo: IslandMimoMondeEngine.Place,
    camera: Offset,
    pas: Float
) {
    val cx = camera.x + (mimo.x + 0.5f) * pas
    // Les pieds ne touchent pas le bas de la case : posé pile sur la limite, le
    // Mimo a l'air de flotter sur la case du dessous.
    val sol = camera.y + (mimo.y + 0.86f) * pas

    val opacite = if (mimo.endormi) 0.72f else 1f
    val hauteurCorps = pas * 0.46f
    val largeurCorps = pas * 0.40f
    val rayonTete = pas * 0.19f

    // L'ombre ancre la figure au sol. Sans elle, la silhouette flotte.
    drawOval(
        color = Color.Black.copy(alpha = 0.20f * opacite),
        topLeft = Offset(cx - pas * 0.25f, sol - pas * 0.08f),
        size = Size(pas * 0.50f, pas * 0.16f)
    )

    drawRoundRect(
        color = couleurMimo(mimo.type).copy(alpha = opacite),
        topLeft = Offset(cx - largeurCorps / 2f, sol - hauteurCorps),
        size = Size(largeurCorps, hauteurCorps),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(largeurCorps * 0.38f)
    )

    drawCircle(
        color = Color(0xFFF3D9B0).copy(alpha = opacite),
        radius = rayonTete,
        center = Offset(cx, sol - hauteurCorps - rayonTete * 0.72f)
    )

    // Un liseré sombre détache la figure de l'herbe, qui est de la même famille
    // de valeurs que certaines blouses.
    drawCircle(
        color = Color(0xFF4A3A2A).copy(alpha = 0.35f * opacite),
        radius = rayonTete,
        center = Offset(cx, sol - hauteurCorps - rayonTete * 0.72f),
        style = Stroke(width = (pas * 0.03f).coerceAtLeast(1f))
    )

    val emoji = if (mimo.endormi) "💤" else mimo.activite.emoji
    if (pas >= PAS_MIN_EMOJI && emoji.isNotEmpty()) {
        pinceauEmoji.textSize = pas * 0.34f
        pinceauEmoji.alpha = (255 * opacite).toInt()
        drawContext.canvas.nativeCanvas.drawText(
            emoji, cx, sol - hauteurCorps - rayonTete * 2.1f, pinceauEmoji
        )
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
