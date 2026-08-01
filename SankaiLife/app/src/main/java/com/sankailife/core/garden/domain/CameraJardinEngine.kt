package com.sankailife.core.garden.domain

import kotlin.math.abs

/**
 * Caméra du Jardin : zoom, déplacement et limites.
 *
 * Toute la géométrie vit ici, en flottants purs, pour une raison précise : les
 * défauts de caméra — tremblement, saut en fin de geste, terrain qui glisse
 * pendant qu'on l'agrandit — sont des erreurs d'arithmétique, pas de rendu. Les
 * laisser dans le composable, c'est ne pouvoir les constater qu'à la main sur
 * un téléphone.
 *
 * Le composable garde une seule responsabilité : lire les doigts et afficher.
 */
object CameraJardinEngine {

    /**
     * Variation d'échelle en dessous de laquelle on ne touche à rien.
     *
     * Deux doigts posés ne sont jamais parfaitement immobiles. Sans ce seuil,
     * chaque micro-tremblement de la main produit un facteur légèrement
     * différent de 1, recalcule la caméra, et le terrain vibre alors que
     * personne n'a voulu zoomer.
     */
    const val SEUIL_ZOOM = 0.015f

    /**
     * Durée pendant laquelle le déplacement reste refusé après un pincement.
     *
     * Les doigts ne se lèvent jamais ensemble. Sans ce délai, le dernier doigt
     * encore posé est aussitôt compris comme un glissement, et la vue part de
     * côté juste après un zoom réussi.
     */
    const val STABILISATION_MS = 90L

    /** Un point du plan. Volontairement pas `Offset` : ce moteur ne dépend pas de Compose. */
    data class Point(val x: Float, val y: Float) {
        operator fun plus(autre: Point) = Point(x + autre.x, y + autre.y)
    }

    /**
     * Ce qu'il faut savoir pour borner la vue.
     *
     * `pas` est la taille d'une case à l'écran ; il change avec le zoom, donc
     * les limites aussi. Les recalculer à partir du cadre plutôt que de les
     * mémoriser évite de raisonner sur des bornes périmées d'une frame.
     */
    data class Cadre(
        val largeurVue: Float,
        val hauteurVue: Float,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val pas: Float
    ) {
        val largeurTerrain: Float get() = (maxX - minX + 1) * pas
        val hauteurTerrain: Float get() = (maxY - minY + 1) * pas
    }

    /**
     * Le pincement mérite-t-il d'être pris en compte ?
     *
     * Rejette aussi les facteurs absurdes que peut produire un doigt qui
     * apparaît ou disparaît en cours de geste.
     */
    fun franchitSeuil(facteur: Float): Boolean =
        facteur.isFinite() && facteur > 0f && abs(facteur - 1f) >= SEUIL_ZOOM

    /** Position qui centre le terrain connu dans la vue. */
    fun centree(cadre: Cadre): Point = Point(
        (cadre.largeurVue - cadre.largeurTerrain) / 2f - cadre.minX * cadre.pas,
        (cadre.hauteurVue - cadre.hauteurTerrain) / 2f - cadre.minY * cadre.pas
    )

    /**
     * Garde le terrain visible ; centre l'axe dont le terrain est plus petit
     * que l'écran.
     *
     * Sans le cas « plus petit que l'écran », un jardin de début de partie
     * pourrait être poussé dans un coin et entouré de vide.
     */
    fun borner(candidat: Point, cadre: Cadre): Point {
        val x = if (cadre.largeurTerrain <= cadre.largeurVue) {
            (cadre.largeurVue - cadre.largeurTerrain) / 2f - cadre.minX * cadre.pas
        } else {
            candidat.x.coerceIn(
                cadre.largeurVue - (cadre.maxX + 1) * cadre.pas,
                -cadre.minX * cadre.pas
            )
        }
        val y = if (cadre.hauteurTerrain <= cadre.hauteurVue) {
            (cadre.hauteurVue - cadre.hauteurTerrain) / 2f - cadre.minY * cadre.pas
        } else {
            candidat.y.coerceIn(
                cadre.hauteurVue - (cadre.maxY + 1) * cadre.pas,
                -cadre.minY * cadre.pas
            )
        }
        return Point(x, y)
    }

    /** Résultat d'un pincement : la nouvelle échelle et la caméra qui va avec. */
    data class Zoom(val echelle: Float, val camera: Point)

    /**
     * Applique un pincement autour du point situé entre les doigts.
     *
     * Le point du Jardin sous le centre du pincement doit rester exactement
     * sous les doigts. On raisonne sur le rapport **réellement appliqué** et
     * non sur le facteur demandé : aux bornes du zoom le facteur est écrêté, et
     * l'utiliser tel quel ferait fuir le terrain alors que l'échelle, elle, ne
     * bouge plus.
     *
     * Le bornage est fait ici, dans le même calcul. C'était la cause du saut de
     * fin de geste : la caméra partait librement pendant le pincement et un
     * effet différé la ramenait d'un coup une fois les doigts levés.
     *
     * @param cadreApres cadre recalculé avec le pas correspondant à la nouvelle
     *   échelle — borner avec l'ancien pas laisserait passer une position que
     *   la frame suivante corrigerait, ce qui est précisément le tremblement.
     */
    fun pincer(
        camera: Point,
        centroide: Point,
        echelleAvant: Float,
        facteur: Float,
        zoomMin: Float,
        zoomMax: Float,
        cadreApres: (Float) -> Cadre
    ): Zoom {
        val echelleApres = (echelleAvant * facteur).coerceIn(zoomMin, zoomMax)
        val rapport = if (echelleAvant <= 0f) 1f else echelleApres / echelleAvant

        val brute = Point(
            centroide.x - (centroide.x - camera.x) * rapport,
            centroide.y - (centroide.y - camera.y) * rapport
        )
        return Zoom(echelleApres, borner(brute, cadreApres(echelleApres)))
    }

    /**
     * Le glissement à un doigt est-il permis ?
     *
     * Refusé pendant un pincement et pendant la stabilisation qui le suit.
     * `dernierZoomMs` vaut 0 tant qu'aucun pincement n'a eu lieu.
     *
     * L'état du geste entre sous forme de booléen plutôt que d'un second
     * énuméré : l'écran a déjà le sien, et en tenir deux en correspondance
     * finirait par les laisser diverger.
     */
    fun peutDeplacer(enZoom: Boolean, maintenantMs: Long, dernierZoomMs: Long): Boolean =
        !enZoom && (dernierZoomMs <= 0L || maintenantMs - dernierZoomMs >= STABILISATION_MS)
}
