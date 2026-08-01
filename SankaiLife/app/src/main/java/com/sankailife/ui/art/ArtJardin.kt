package com.sankailife.ui.art

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sankailife.R
import com.sankailife.core.garden.domain.CropStage
import com.sankailife.core.garden.domain.DayNightEngine
import com.sankailife.core.garden.domain.ExpansionEngine
import com.sankailife.core.garden.domain.MoistureEngine
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.garden.domain.WeatherEngine

/**
 * Le pont entre le domaine et les images.
 *
 * Toute la logique du jardin ignore qu'il existe des dessins : elle manipule
 * des `CropStage`, des `SoilType`, des `Meteo`. C'est ici, et nulle part
 * ailleurs, qu'on décide à quoi ça ressemble.
 *
 * **Comment remplacer les dessins par de vraies illustrations.** Chaque
 * élément est un fichier `res/drawable/art_<nom>`. Poser un PNG ou un WebP du
 * même nom à la place du XML suffit : aucun code ne change, aucune constante
 * n'est à mettre à jour. Les images actuelles sont des vectoriels générés par
 * `art/generer-assets.py` — un tracé au crayon, honnête mais sommaire, qui
 * tient lieu de brouillon en attendant mieux.
 *
 * Les éléments que le générateur ne sait pas produire — dioramas d'arène,
 * Mimos, animaux, icône de l'application — sont listés dans
 * `art/MANIFESTE-ASSETS.md` et restent des emojis pour l'instant.
 */
object ArtJardin {

    /**
     * La plante seule, sans son sol.
     *
     * Les illustrations fournies sont détourées : elles se **superposent** à
     * la parcelle au lieu de la remplacer. C'est ce qui permet à une même
     * plante de pousser sur une terre sèche ou détrempée sans dessiner six
     * variantes de chaque stade.
     *
     * `prete` n'est pas un stade de [CropStage] mais un état calculé : une
     * plante mûre reste MATURE une fois récoltable. Le distinguer compte plus
     * que la nuance de vocabulaire — c'est le seul moment où il faut agir.
     */
    @DrawableRes
    fun plante(stage: CropStage, prete: Boolean = false): Int = when {
        prete -> R.drawable.plant_stage_5_ready
        stage == CropStage.GRAINE -> R.drawable.plant_stage_0_seed
        stage == CropStage.GERME -> R.drawable.plant_stage_1
        stage == CropStage.POUSSE -> R.drawable.plant_stage_2
        stage == CropStage.JEUNE -> R.drawable.plant_stage_3
        else -> R.drawable.plant_stage_4
    }

    @DrawableRes
    fun sol(sol: SoilType): Int = when (sol) {
        SoilType.TERRE -> R.drawable.art_sol_terre
        SoilType.RICHE -> R.drawable.art_sol_riche
        SoilType.SABLE -> R.drawable.art_sol_sable
        SoilType.HUMIDE -> R.drawable.art_sol_humide
        SoilType.NOCTURNE -> R.drawable.art_sol_nocturne
        SoilType.CRISTALLIN -> R.drawable.art_sol_cristallin
    }

    /**
     * La texture d'une case cultivée, selon son humidité.
     *
     * Les images sont **opaques et carrées**, sans bord irrégulier : posées
     * côte à côte sans écart, elles forment un sol continu. Les anciennes
     * avaient des bords rongés, ce qui laissait voir le fond entre les cases
     * et donnait une grille de boutons.
     *
     * Trois textures couvrent les cinq paliers d'humidité : la nuance entre
     * « sec » et « un peu sec » se lit mal sur une case de 78 dp, et fabriquer
     * des intermédiaires par retouche jurerait avec le reste.
     */
    @DrawableRes
    fun parcelle(etat: MoistureEngine.Etat): Int = when (etat) {
        MoistureEngine.Etat.SEC,
        MoistureEngine.Etat.LEGEREMENT_SEC -> R.drawable.plot_dry
        MoistureEngine.Etat.HUMIDE -> R.drawable.plot_tilled
        MoistureEngine.Etat.BIEN_ARROSE,
        MoistureEngine.Etat.DETREMPE -> R.drawable.plot_wet
    }

    /**
     * L'herbe : le terrain tel qu'il est avant d'être cultivé.
     *
     * Sert aux cases découvertes mais pas encore achetées, et à celles en
     * chantier. Une case verrouillée garde ainsi la texture du monde et reçoit
     * seulement un cadenas par-dessus, au lieu d'un rectangle gris qui trouait
     * le paysage.
     */
    @DrawableRes val herbe = R.drawable.plot_grass

    /** Terre non labourée, après une récolte. */
    @DrawableRes val terreSeche = R.drawable.plot_dry

    @DrawableRes
    fun meteo(meteo: WeatherEngine.Meteo): Int = when (meteo) {
        WeatherEngine.Meteo.CANICULE -> R.drawable.art_meteo_canicule
        WeatherEngine.Meteo.SOLEIL -> R.drawable.art_meteo_soleil
        WeatherEngine.Meteo.NUAGEUX -> R.drawable.art_meteo_nuageux
        WeatherEngine.Meteo.PLUIE -> R.drawable.art_meteo_pluie
        WeatherEngine.Meteo.ORAGE -> R.drawable.art_meteo_orage
    }

    @DrawableRes
    fun phase(phase: DayNightEngine.Phase): Int = when (phase) {
        DayNightEngine.Phase.AUBE -> R.drawable.art_phase_aube
        DayNightEngine.Phase.JOUR -> R.drawable.art_phase_jour
        DayNightEngine.Phase.CREPUSCULE -> R.drawable.art_phase_crepuscule
        DayNightEngine.Phase.NUIT -> R.drawable.art_phase_nuit
    }

    @DrawableRes
    fun outil(outil: OutilJardin): Int = when (outil) {
        is OutilJardin.Graine -> R.drawable.art_ressource_graines
        OutilJardin.Arrosoir -> R.drawable.art_outil_arrosoir
        OutilJardin.Panier -> R.drawable.art_outil_panier
        OutilJardin.Pioche -> R.drawable.art_outil_pioche
    }

    /**
     * Coffre selon son type.
     *
     * Le type vient de la base sous forme de chaîne : un type inconnu, écrit
     * par une version future ou par une donnée abîmée, retombe sur le coffre
     * commun plutôt que de faire planter l'écran.
     */
    @DrawableRes
    fun coffre(type: String, ouvert: Boolean = false): Int = when (type.uppercase()) {
        "DAILY" -> R.drawable.art_coffre_graines
        "RARE" -> R.drawable.art_coffre_rare
        "EPIC" -> R.drawable.art_coffre_epique
        "LEGENDARY" -> R.drawable.art_coffre_legendaire
        "WEEKLY" -> R.drawable.art_coffre_recolte
        // COMMON, et tout type écrit par une version future ou par une donnée
        // abîmée : mieux vaut un coffre en bois qu'un écran qui plante.
        else -> if (ouvert) R.drawable.art_coffre_ouvert else R.drawable.art_coffre_commun
    }

    @DrawableRes
    fun terrain(terrain: ExpansionEngine.Terrain): Int = when (terrain) {
        ExpansionEngine.Terrain.FERTILE -> R.drawable.art_sol_riche
        ExpansionEngine.Terrain.ORDINAIRE -> R.drawable.art_sol_terre
        ExpansionEngine.Terrain.HUMIDE -> R.drawable.art_sol_humide
        ExpansionEngine.Terrain.SABLEUX -> R.drawable.art_sol_sable
        ExpansionEngine.Terrain.ROCHEUX -> R.drawable.art_parcelle_encombree
        ExpansionEngine.Terrain.FORESTIER -> R.drawable.art_lieu_arbre
        ExpansionEngine.Terrain.ABANDONNE -> R.drawable.art_parcelle_encombree
    }

    @DrawableRes val brouillard = R.drawable.art_parcelle_brouillard
    @DrawableRes val parcelleVide = R.drawable.art_parcelle_vide
    @DrawableRes val depot = R.drawable.art_lieu_depot
    @DrawableRes val magasin = R.drawable.art_lieu_magasin
    @DrawableRes val arbre = R.drawable.art_lieu_arbre
    @DrawableRes val piece = R.drawable.currency_coin
    @DrawableRes val eau = R.drawable.art_ressource_eau
    @DrawableRes val compost = R.drawable.art_ressource_compost
    @DrawableRes val cristal = R.drawable.art_ressource_cristal
}

/**
 * Une image d'art, sans description pour les lecteurs d'écran.
 *
 * `contentDescription = null` est délibéré : ces dessins doublent toujours un
 * texte voisin. Les annoncer une seconde fois ferait répéter l'information à
 * qui utilise TalkBack.
 */
@Composable
fun IconeArt(
    @DrawableRes ressource: Int,
    taille: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(ressource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(taille)
    )
}
