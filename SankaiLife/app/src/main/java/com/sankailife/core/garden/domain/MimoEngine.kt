package com.sankailife.core.garden.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Les Mimos : les habitants qui travaillent pendant l'absence du joueur.
 *
 * Ils n'agissent pas en temps réel — rien ne tourne quand l'application est
 * fermée. Leur travail est **reconstitué à l'ouverture** à partir du temps
 * écoulé, comme la croissance des plantes. C'est le seul modèle honnête hors
 * ligne : un service d'arrière-plan serait tué par le système, et le joueur
 * ne comprendrait pas pourquoi ses Mimos travaillent chez lui et pas chez son
 * voisin.
 *
 * Conséquence : une absence est toujours récompensée, jamais punie. Mais elle
 * est bornée — le plafond de 24 h de [TrustedTimeEngine] s'applique en amont,
 * et les Mimos ne travaillent que le jour.
 */
object MimoEngine {

    /**
     * Chaque Mimo couvre une étape de la boucle, et une seule.
     *
     * Le découpage suit exactement le circuit de la récolte : arroser,
     * récolter, transporter, vendre, replanter. Un Mimo polyvalent aurait été
     * plus simple à écrire, mais il aurait rendu l'automatisation totale d'un
     * coup — alors que la monter étape par étape est justement la progression.
     */
    enum class Type(
        val libelle: String,
        val emoji: String,
        /** Minutes de travail nécessaires à une action. */
        val cadenceMinutes: Long,
        val coutEmbauche: Int,
        val role: String
    ) {
        ARROSEUR("Arroseur", "💧", 75, 350,
            "Arrose les plantes assoiffées pendant ton absence."),
        RECOLTEUR("Récolteur", "🧺", 110, 600,
            "Récolte les plantes mûres et pose les caisses."),
        TRANSPORTEUR("Transporteur", "📦", 55, 500,
            "Porte les caisses jusqu'au dépôt central."),
        VENDEUR("Vendeur", "🪙", 150, 900,
            "Vend le stock au marchand, aux heures d'ouverture."),
        PLANTEUR("Planteur", "🌱", 190, 1200,
            "Replante la même graine sur les parcelles libres.");

        companion object {
            fun parNom(valeur: String): Type? = entries.firstOrNull { it.name == valeur }
        }
    }

    /**
     * Chaque action consomme une unité de compost.
     *
     * Le compost s'accumulait sans emploi depuis les premières récoltes ; il
     * devient ici la contrainte qui empêche l'automatisation de tourner à
     * l'infini. La boucle se referme sur elle-même : récolter produit du
     * compost, le compost paie les Mimos, les Mimos récoltent.
     */
    const val COMPOST_PAR_ACTION = 1

    /** Plafond d'actions par Mimo et par ouverture, quelle que soit l'absence. */
    const val ACTIONS_MAX_PAR_OUVERTURE = 12

    /**
     * Minutes ouvrées entre deux instants.
     *
     * Les Mimos dorment aux mêmes heures que le marchand. Sans cette borne,
     * une absence d'une nuit rapporterait autant qu'une journée entière, et
     * le cycle jour / nuit n'aurait aucune conséquence.
     *
     * Le calcul découpe l'intervalle jour par jour plutôt que minute par
     * minute : une absence longue ne doit pas coûter des milliers d'itérations
     * au démarrage.
     */
    fun minutesOuvrees(
        debutMillis: Long,
        finMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (finMillis <= debutMillis) return 0

        val debut = Instant.ofEpochMilli(debutMillis).atZone(zone).toLocalDateTime()
        val fin = Instant.ofEpochMilli(finMillis).atZone(zone).toLocalDateTime()

        var total = 0L
        var jour: LocalDate = debut.toLocalDate()
        val dernierJour = fin.toLocalDate()

        while (!jour.isAfter(dernierJour)) {
            val ouverture = jour.atTime(LocalTime.of(DayNightEngine.OUVERTURE_MAGASIN, 0))
            val fermeture = jour.atTime(LocalTime.of(DayNightEngine.FERMETURE_MAGASIN, 0))

            val debutUtile = maxOf(debut, ouverture)
            val finUtile = minOf(fin, fermeture)

            if (finUtile.isAfter(debutUtile)) {
                total += java.time.Duration.between(debutUtile, finUtile).toMinutes()
            }
            jour = jour.plusDays(1)
        }
        return total
    }

    /**
     * Nombre d'actions qu'un Mimo a pu accomplir.
     *
     * Borné par trois choses : le temps ouvré, le compost disponible, et un
     * plafond fixe. Le plafond existe pour que revenir après une semaine ne
     * vide pas le jardin d'un coup — il resterait plus rien à faire au joueur,
     * ce qui est l'échec exact que l'automatisation doit éviter.
     */
    fun actions(type: Type, minutesOuvrees: Long, compostDisponible: Int): Int {
        if (minutesOuvrees <= 0 || compostDisponible <= 0) return 0
        val parLeTemps = (minutesOuvrees / type.cadenceMinutes).toInt()
        val parLeCompost = compostDisponible / COMPOST_PAR_ACTION
        return minOf(parLeTemps, parLeCompost, ACTIONS_MAX_PAR_OUVERTURE)
    }

    /**
     * Budget commun d'une equipe du meme metier.
     *
     * Le compost borne le groupe apres multiplication par l'effectif. Le
     * borner Mimo par Mimo puis multiplier permettrait par exemple a cinq
     * Mimos d'effectuer cinq actions avec une seule unite de compost.
     */
    fun actionsEquipe(
        type: Type,
        minutesOuvrees: Long,
        effectif: Int,
        compostDisponible: Int
    ): Int {
        if (effectif <= 0 || compostDisponible < COMPOST_PAR_ACTION) return 0
        val parMimo = actions(type, minutesOuvrees, Int.MAX_VALUE)
        val parLeTemps = parMimo.toLong() * effectif.toLong()
        val parLeCompost = compostDisponible / COMPOST_PAR_ACTION
        return minOf(parLeTemps, parLeCompost.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /** Ce que les Mimos ont fait, pour le raconter au retour. */
    data class Rapport(
        val arrosages: Int = 0,
        val recoltes: Int = 0,
        val transports: Int = 0,
        val ventes: Int = 0,
        val piecesGagnees: Int = 0,
        val plantations: Int = 0,
        val compostManquant: Boolean = false
    ) {
        val vide: Boolean
            get() = arrosages == 0 && recoltes == 0 && transports == 0 &&
                    ventes == 0 && plantations == 0
    }

    /**
     * Résumé lisible du rapport, ou null s'il n'y a rien à dire.
     * Le silence est volontaire : afficher « 0 action » à chaque ouverture
     * transformerait une fonctionnalité en reproche.
     */
    fun resume(rapport: Rapport): String? {
        if (rapport.vide) {
            return if (rapport.compostManquant) {
                "Tes Mimos ont manqué de compost. Récolte pour en produire."
            } else null
        }

        val morceaux = buildList {
            if (rapport.arrosages > 0) add("${rapport.arrosages} arrosage(s)")
            if (rapport.recoltes > 0) add("${rapport.recoltes} récolte(s)")
            if (rapport.transports > 0) add("${rapport.transports} caisse(s) rangée(s)")
            if (rapport.ventes > 0) add("${rapport.ventes} vente(s) • +${rapport.piecesGagnees} 🪙")
            if (rapport.plantations > 0) add("${rapport.plantations} plantation(s)")
        }
        return "Pendant ton absence : " + morceaux.joinToString(", ") + "."
    }

    /** Noms tirés au sort à l'embauche, pour que chaque Mimo soit distinct. */
    val NOMS = listOf(
        "Pim", "Nao", "Sora", "Loulou", "Kira", "Tami", "Bo", "Yuna",
        "Miko", "Renn", "Ilo", "Suki", "Tao", "Nima", "Ezo", "Poppy"
    )
}
