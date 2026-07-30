package com.sankailife.core.garden.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * État global du jardin : ressources et repères temporels.
 *
 * Ligne unique, comme `user`. Les repères d'horloge vivent ici plutôt que
 * dans DataStore pour rester dans la même transaction que les ressources —
 * créditer de l'eau et enregistrer l'heure doivent réussir ou échouer ensemble.
 */
@Entity(tableName = "garden_state")
data class GardenStateEntity(
    @PrimaryKey val id: Long = 1L,

    val eau: Int = 10,
    val compost: Int = 0,
    val cristaux: Int = 0,

    /** Gouttes en attente de conversion en unité d'eau. */
    val gouttes: Int = 0,
    /** Eau déjà obtenue par l'apprentissage aujourd'hui, pour le plafond. */
    val eauGagneeAujourdhui: Int = 0,
    /** Jour de référence du plafond, au format ISO. */
    val jourPlafond: String = "",

    /** Cartes révisées depuis la dernière ouverture, pour les bonus. */
    val revisionsDepuisOuverture: Int = 0,

    // Repères de l'horloge de confiance.
    val derniereHeureMurale: Long = 0L,
    val dernierElapsedRealtime: Long = 0L,

    /** Zone actuellement affichée, pour les extensions futures. */
    val zoneActive: String = "central"
)

/**
 * Une parcelle du jardin.
 *
 * L'identifiant est la position dans la grille : stable, lisible en base, et
 * il évite une table de positions séparée pour un prototype 3 × 3.
 */
@Entity(tableName = "garden_plot")
data class GardenPlotEntity(
    @PrimaryKey val id: Int = 0,
    /** Valeur de PlotState. */
    val etat: String = "LOCKED",
    /** Identifiant de SoilType. */
    val solId: String = "terre",
    /** Arène nécessaire pour déverrouiller cette parcelle. */
    val areneRequise: Int = 1
)

/**
 * Caisse de récolte en attente de rangement.
 *
 * Une plante récoltée ne rejoint pas directement l'inventaire : elle produit
 * une caisse qu'il faut ranger au dépôt. C'est ce qui donne un rôle au dépôt
 * et, plus tard, aux Mimos transporteurs — sans cette étape, l'automatisation
 * n'aurait rien à automatiser.
 */
@Entity(tableName = "garden_crate")
data class GardenCrateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val seedId: String = "",
    /** Valeur de HarvestQuality. */
    val qualite: String = "NORMALE",
    val creeALeMillis: Long = 0L
)

/**
 * Stock rangé au dépôt, prêt à être vendu.
 *
 * Une ligne par couple espèce + qualité. La clé est la concaténation des deux
 * plutôt qu'une clé composite : ça rend l'UPSERT trivial côté DAO.
 */
@Entity(tableName = "garden_inventory")
data class GardenInventoryEntity(
    @PrimaryKey val cle: String = "",
    val seedId: String = "",
    val qualite: String = "NORMALE",
    val quantite: Int = 0
)

/**
 * Trace d'une notification mémo envoyée, support du défi souvenir.
 *
 * Une ligne est créée à chaque notification réellement partie. Le drapeau
 * [reclame] est ce qui empêche de rejouer indéfiniment le même souvenir.
 */
@Entity(tableName = "memo_challenge")
data class MemoChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long = 0L,
    val lineId: Long = 0L,
    val texte: String = "",
    val nomModule: String = "",
    val envoyeALeMillis: Long = 0L,
    val reclame: Boolean = false
)

/**
 * Une culture en cours ou récoltée.
 *
 * `minutesCumulees` est recalculé à chaque ouverture depuis l'horloge de
 * confiance : on ne stocke pas une date de maturité figée, sinon un
 * changement d'heure la rendrait fausse définitivement.
 */
@Entity(tableName = "garden_crop")
data class GardenCropEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val plotId: Int = 0,
    val seedId: String = "",

    val plantedAtMillis: Long = 0L,
    val minutesCumulees: Long = 0L,
    val dernierArrosageMillis: Long = 0L,

    val arrosages: Int = 0,
    /** Cartes révisées pendant que cette culture poussait, pour la qualité. */
    val revisionsPendantCulture: Int = 0,

    val recoltee: Boolean = false
)
