package com.sankailife.core.domain.engine

import com.sankailife.core.data.db.entities.DayRecordEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Mesure de la régularité, pensée pour ne pas punir un jour manqué.
 *
 * Le reproche le plus courant fait aux applications d'habitudes est qu'une
 * seule journée ratée efface des semaines d'efforts. Trois indicateurs
 * cohabitent donc ici, et un seul d'entre eux peut retomber à zéro :
 *
 * - **série actuelle** : jours consécutifs, peut se casser ;
 * - **régularité** : pourcentage sur une fenêtre glissante, bouge lentement ;
 * - **meilleur record** : permanent, jamais repris.
 *
 * Un jour manqué fait tomber la série mais laisse la régularité à 90 % et le
 * record intact. Le sentiment de progression survit à l'accident.
 */
object RegularityEngine {

    /** État possible d'une journée. */
    object Statut {
        const val SUCCES = "SUCCESS"
        const val PARTIEL = "PARTIAL"
        const val MANQUE = "MISSED"
        const val PAUSE = "PAUSED"
        /** Journée manquée mais absorbée par un bouclier. */
        const val PROTEGE = "SHIELDED"
    }

    /** Une journée en pause ne compte ni comme réussie ni comme échouée. */
    private val NEUTRES = setOf(Statut.PAUSE)

    /** Statuts qui préservent la série. */
    private val CONSERVENT_SERIE = setOf(Statut.SUCCES, Statut.PARTIEL, Statut.PROTEGE)

    /**
     * Régularité sur les [jours] derniers jours, entre 0 et 1.
     *
     * Les journées en pause sont retirées du dénominateur : mettre une
     * habitude en pause ne doit pas dégrader le score, sinon la pause devient
     * elle-même une punition et personne ne l'utilise.
     */
    fun regularite(records: List<DayRecordEntity>, jours: Int, aujourdhui: LocalDate = LocalDate.now()): Float {
        val debut = aujourdhui.minusDays((jours - 1).toLong())
        val fenetre = records.filter {
            val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
            d != null && !d.isBefore(debut) && !d.isAfter(aujourdhui)
        }
        val comptables = fenetre.filterNot { it.status in NEUTRES }
        if (comptables.isEmpty()) return 0f

        val tenus = comptables.count { it.status in CONSERVENT_SERIE }
        // Le dénominateur est la fenêtre entière, pas seulement les jours
        // enregistrés : les jours sans trace sont des jours non tenus.
        val denominateur = (jours - (fenetre.size - comptables.size)).coerceAtLeast(1)
        return (tenus.toFloat() / denominateur).coerceIn(0f, 1f)
    }

    fun pourcentage(records: List<DayRecordEntity>, jours: Int): Int =
        (regularite(records, jours) * 100).toInt()

    /**
     * Décide de l'issue d'un retour dans l'application.
     *
     * @param dernierJour dernière date enregistrée, vide au premier lancement.
     * @param serieActuelle série avant ce retour.
     * @param boucliers boucliers disponibles.
     */
    data class Resultat(
        val nouvelleSerie: Int,
        val boucliersRestants: Int,
        val boucliersUtilises: Int,
        val serieCassee: Boolean,
        val absenceJours: Int
    )

    fun evaluerRetour(
        dernierJour: String,
        serieActuelle: Int,
        boucliers: Int,
        aujourdhui: LocalDate = LocalDate.now()
    ): Resultat {
        if (dernierJour.isBlank()) {
            return Resultat(1, boucliers, 0, serieCassee = false, absenceJours = 0)
        }
        val dernier = runCatching { LocalDate.parse(dernierJour) }.getOrNull()
            ?: return Resultat(1, boucliers, 0, serieCassee = false, absenceJours = 0)

        val ecart = ChronoUnit.DAYS.between(dernier, aujourdhui).toInt()
        return when {
            ecart <= 0 -> Resultat(serieActuelle, boucliers, 0, false, 0)
            ecart == 1 -> Resultat(serieActuelle + 1, boucliers, 0, false, 0)
            else -> {
                // Chaque jour manqué consomme un bouclier. Au-delà du stock,
                // la série tombe — mais la régularité et le record restent.
                val manques = ecart - 1
                val utilises = minOf(manques, boucliers)
                if (utilises >= manques) {
                    Resultat(serieActuelle + 1, boucliers - utilises, utilises, false, manques)
                } else {
                    Resultat(1, boucliers - utilises, utilises, serieCassee = true, absenceJours = manques)
                }
            }
        }
    }

    /** Un bouclier gagné tous les 7 jours de série, plafonné pour éviter l'accumulation. */
    fun boucliersGagnes(nouvelleSerie: Int, boucliersActuels: Int): Int =
        if (nouvelleSerie > 0 && nouvelleSerie % 7 == 0 && boucliersActuels < MAX_BOUCLIERS) 1 else 0

    const val MAX_BOUCLIERS = 3

    /** Libellé court pour l'interface. */
    fun libelleStatut(statut: String): String = when (statut) {
        Statut.SUCCES -> "Réussi"
        Statut.PARTIEL -> "Partiel"
        Statut.PAUSE -> "En pause"
        Statut.PROTEGE -> "Protégé"
        else -> "Manqué"
    }
}
