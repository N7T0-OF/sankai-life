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
 * L'identifiant est la coordonnée sur le plan, `y * 40 + x`. Il valait
 * autrefois l'index dans une liste de seize cases lues comme quatre colonnes —
 * un tableau déguisé en base. Un index linéaire ne peut pas exprimer « la case
 * au nord de celle-ci », d'où le changement.
 *
 * Deux états cohabitent, volontairement séparés : [deblocage] dit si la case
 * appartient au joueur, [etat] ce qui pousse dessus. Les fusionner créerait
 * des combinaisons impossibles à représenter — une case ne peut pas être à la
 * fois « en chantier » et « prête à récolter », mais elle peut être
 * « débloquée » et « en friche ».
 */
@Entity(tableName = "garden_plot")
data class GardenPlotEntity(
    @PrimaryKey val id: Int = 0,
    /** Valeur de PlotState : ce qui pousse. */
    val etat: String = "LOCKED",
    /** Identifiant de SoilType. */
    val solId: String = "terre",
    /** Arène nécessaire — conservé pour les anciennes parcelles. */
    val areneRequise: Int = 1,

    /** Valeur de ExpansionEngine.Deblocage : à qui appartient la case. */
    val deblocage: String = "CACHEE",
    /** Valeur de ExpansionEngine.Terrain. */
    val terrain: String = "ORDINAIRE",
    /** Fin du chantier en cours, 0 si aucun. */
    val chantierFinMillis: Long = 0L,

    /** Humidité du sol, de 0 (poussière) à 1 (détrempé). */
    val humidite: Float = 0.5f,
    /** Repère du dernier calcul d'évaporation. */
    val dernierCalculHumidite: Long = 0L
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
 * Un Mimo embauché.
 *
 * Aucun état de progression n'est stocké : leur travail est reconstitué à
 * l'ouverture depuis le temps écoulé. Une file de tâches persistée aurait dû
 * être réconciliée à chaque changement d'heure, pour un résultat identique.
 */
@Entity(tableName = "garden_mimo")
data class GardenMimoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Valeur de MimoEngine.Type. */
    val type: String = "",
    val nom: String = "",
    val embaucheMillis: Long = 0L
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
