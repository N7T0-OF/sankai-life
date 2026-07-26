package com.sankailife.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.preferences.AppPreferences
import com.sankailife.core.domain.engine.MemoEngine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tâche périodique qui envoie les phrases mémo.
 *
 * Elle se réveille toutes les 15 minutes (minimum imposé par WorkManager) et,
 * pour chaque module actif, regarde si un créneau de la journée vient d'être
 * dépassé sans avoir encore été notifié. Cette approche « on rattrape le
 * créneau manqué » survit au redémarrage du téléphone et au Doze mode, là où
 * une alarme exacte serait perdue ou demanderait une permission spéciale.
 *
 * 100 % local : aucune requête réseau n'est faite ici.
 */
class MemoNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexte = applicationContext

        // Réglage utilisateur : notifications coupées => on ne fait rien, mais
        // on renvoie success pour que la tâche périodique reste programmée.
        val prefs = AppPreferences(contexte)
        if (!prefs.notifications.first()) return Result.success()
        if (!SankaiNotifications.peutNotifier(contexte)) return Result.success()

        val db = SankaiDatabase.getDatabase(contexte)
        val memoDao = db.memoDao()

        val maintenant = LocalDateTime.now()
        val modules = runCatching { memoDao.getActiveProfilesOnce() }.getOrElse { return Result.retry() }

        for (module in modules) {
            val creneau = dernierCreneauDepasse(module, maintenant) ?: continue
            if (module.lastNotifiedAtMillis >= creneau) continue

            val lignes = memoDao.getLinesOnce(module.id)
            if (lignes.isEmpty()) continue

            // Tirage anti-répétition : on évite les phrases déjà envoyées récemment.
            val choisie = MemoEngine.getRandomLine(lignes.map { it.id }, module.sentLineHistory)
                ?: continue
            val texte = lignes.firstOrNull { it.id == choisie }?.text ?: continue

            SankaiNotifications.afficherMemo(contexte, module.id, module.name, texte)

            memoDao.updateHistory(module.id, MemoEngine.updateHistory(module.sentLineHistory, choisie))
            memoDao.updateLastNotified(module.id, System.currentTimeMillis())
        }

        return Result.success()
    }

    /**
     * Renvoie l'instant (epoch ms) du dernier créneau du jour déjà passé, ou
     * null si aucun créneau n'est encore arrivé aujourd'hui.
     */
    private fun dernierCreneauDepasse(module: MemoProfileEntity, maintenant: LocalDateTime): Long? {
        val aujourdhui: LocalDate = maintenant.toLocalDate()
        val zone = ZoneId.systemDefault()

        return MemoEngine
            .slotsDuJour(module.scheduledHour, module.scheduledMinute, module.frequencyPerDay)
            .map { (h, m) -> aujourdhui.atTime(h.coerceIn(0, 23), m.coerceIn(0, 59)) }
            .filter { !it.isAfter(maintenant) }
            .maxOrNull()
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
    }
}
