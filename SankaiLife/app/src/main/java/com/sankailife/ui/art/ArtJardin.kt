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
     * Dessin d'une culture.
     *
     * `prete` n'est pas un stade de [CropStage] mais un état calculé : une
     * plante mûre reste MATURE une fois récoltable. Le distinguer visuellement
     * compte plus que la nuance de vocabulaire — c'est le seul moment où le
     * joueur doit agir.
     */
    @DrawableRes
    fun stade(stage: CropStage, prete: Boolean = false): Int = when {
        prete -> R.drawable.art_croissance_recoltable
        stage == CropStage.GRAINE -> R.drawable.art_croissance_graine
        stage == CropStage.GERME -> R.drawable.art_croissance_germe
        stage == CropStage.POUSSE -> R.drawable.art_croissance_pousse
        stage == CropStage.JEUNE -> R.drawable.art_croissance_jeune
        else -> R.drawable.art_croissance_mature
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
     * Sol teinté par son humidité.
     *
     * Prioritaire sur [sol] pour une parcelle cultivable : l'humidité est
     * l'information qui change d'heure en heure, le type de sol ne bouge
     * jamais. Montrer le second cacherait le premier.
     */
    @DrawableRes
    fun humidite(etat: MoistureEngine.Etat): Int = when (etat) {
        MoistureEngine.Etat.SEC -> R.drawable.art_humidite_sec
        MoistureEngine.Etat.LEGEREMENT_SEC -> R.drawable.art_humidite_legerement
        MoistureEngine.Etat.HUMIDE -> R.drawable.art_humidite_humide
        MoistureEngine.Etat.BIEN_ARROSE -> R.drawable.art_humidite_bien
        MoistureEngine.Etat.DETREMPE -> R.drawable.art_humidite_detrempe
    }

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
    fun coffre(type: String, ouvert: Boolean = false): Int = when {
        ouvert -> R.drawable.art_coffre_ouvert
        type.equals("DAILY", true) -> R.drawable.art_coffre_graines
        type.equals("SILVER", true) -> R.drawable.art_coffre_recolte
        type.equals("GOLD", true) -> R.drawable.art_coffre_rare
        type.equals("EPIC", true) -> R.drawable.art_coffre_epique
        type.equals("LEGENDARY", true) -> R.drawable.art_coffre_legendaire
        else -> R.drawable.art_coffre_commun
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
    @DrawableRes val piece = R.drawable.art_ressource_piece
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
