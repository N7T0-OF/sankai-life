package com.sankailife.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Met en place le filet de sécurité qui vérifie les alarmes mémo.
 *
 * Aucune contrainte réseau : les rappels doivent fonctionner en mode avion,
 * c'est tout l'intérêt d'une application hors ligne.
 */
object NotificationScheduler {

    private const val TACHE_VERIFICATION = "sankai_memo_watchdog"

    fun programmer(context: Context) {
        val requete = PeriodicWorkRequestBuilder<MemoNotificationWorker>(
            6, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build()
        ).build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TACHE_VERIFICATION,
                // KEEP : ne pas réarmer le compteur à chaque ouverture d'app,
                // sinon la vérification ne s'exécuterait jamais.
                ExistingPeriodicWorkPolicy.KEEP,
                requete
            )
        }
    }

    fun annuler(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(TACHE_VERIFICATION) }
    }
}
