package com.sankailife.core.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Intégration du calendrier Android, **lecture seule**.
 *
 * La règle de Sankai : si Android sait déjà faire quelque chose, Sankai ne le
 * recrée pas — il s'y connecte et valorise son utilisation. Un événement
 * terminé devient une progression symbolique, plafonnée par le moteur
 * anti-farm. Sankai ne lit que ce dont il a besoin, ne modifie jamais le
 * calendrier, et tout reste sur l'appareil.
 */
object CalendrierIntegration {

    /** Un événement, réduit à ce dont Sankai a besoin. */
    data class Evenement(
        /** Identifiant de l'événement : une occurrence d'un événement récurrent le partage. */
        val id: String,
        val titre: String,
        val finMillis: Long
    )

    fun permissionAccordee(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Les événements terminés aujourd'hui, depuis le début du jour jusqu'à
     * maintenant.
     *
     * Un événement en cours n'est pas « terminé » : il compte quand il finit.
     * Un événement à la journée (all-day) occupe le jour entier et compte le
     * jour où il a lieu. Chaque occurrence d'un événement récurrent est lue
     * dans la table Instances — la table Events ne montrerait que la première.
     */
    fun evenementsTerminesAujourdhui(
        context: Context,
        maintenantMillis: Long = System.currentTimeMillis()
    ): List<Evenement> {
        if (!permissionAccordee(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val debutJour = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val finJour = debutJour + TimeUnit.DAYS.toMillis(1)
        return lireInstances(
            resolver = context.contentResolver,
            debutJour = debutJour,
            finJour = finJour,
            maintenant = maintenantMillis
        )
    }

    /**
     * La sélection d'un événement terminé, depuis la table Instances.
     *
     * Séparée de l'appel Android pour être testable sans appareil.
     */
    internal fun lireInstances(
        resolver: ContentResolver,
        debutJour: Long,
        finJour: Long,
        maintenant: Long
    ): List<Evenement> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val instances = try {
            CalendarContract.Instances.query(resolver, projection, debutJour, finJour)
        } catch (error: SecurityException) {
            // Permission retirée entre la vérification et la requête.
            return emptyList()
        } ?: return emptyList()

        val resultat = mutableListOf<Evenement>()
        instances.use { curseur ->
            val colId = curseur.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val colTitre = curseur.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val colBegin = curseur.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val colEnd = curseur.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val colAllDay = curseur.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (curseur.moveToNext()) {
                val allDay = curseur.getInt(colAllDay) == 1
                val fin = curseur.getLong(colEnd)
                val debut = curseur.getLong(colBegin)
                val termine = when {
                    allDay -> debut >= debutJour && debut < finJour
                    else -> fin > debutJour && fin <= maintenant
                }
                if (!termine) continue
                resultat.add(
                    Evenement(
                        id = curseur.getString(colId) ?: "${debut}_${curseur.getLong(colEnd)}",
                        titre = curseur.getString(colTitre).orEmpty().ifBlank { "Événement" },
                        finMillis = fin
                    )
                )
            }
        }
        return resultat
    }

    /**
     * Les événements pas encore crédités aujourd'hui, dédupliqués.
     *
     * Un événement récurrent compte une fois par jour, pas une fois par
     * occurrence : l'anti-farm plafonne déjà la source, et compter deux cours
     * de piano identiques serait compter deux fois la même chose.
     */
    fun aCrediter(dejaCredites: Set<String>, evenements: List<Evenement>): List<Evenement> =
        evenements.asSequence()
            .filter { it.id !in dejaCredites }
            .distinctBy { it.id }
            .toList()
}
