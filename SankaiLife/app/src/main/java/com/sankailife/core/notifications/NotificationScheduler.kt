package com.sankailife.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programme la tâche de fond qui envoie les mémos.
 *
 * Aucune contrainte réseau n'est posée : les notifications doivent tomber même
 * en mode avion, c'est tout l'intérêt d'une app offline-first.
 */
object NotificationScheduler {

    private const val TACHE_MEMO = "sankai_memo_tick"

    fun programmer(context: Context) {
        val requete = PeriodicWorkRequestBuilder<MemoNotificationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build()
        ).build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TACHE_MEMO,
                // KEEP : ne pas réinitialiser le compteur à chaque ouverture d'app,
                // sinon la tâche ne se déclencherait jamais chez un utilisateur
                // qui ouvre l'app plus souvent que toutes les 15 minutes.
                ExistingPeriodicWorkPolicy.KEEP,
                requete
            )
        }
    }

    fun annuler(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(TACHE_MEMO) }
    }
}
