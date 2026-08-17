package com.sankailife.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Filet de sécurité des alarmes mémo.
 *
 * Cette tâche **n'envoie aucune notification** : c'est AlarmManager qui s'en
 * charge, à l'heure exacte. Elle se contente de vérifier périodiquement que
 * les alarmes sont bien posées et de les reprogrammer sinon.
 *
 * Elle existe parce qu'une alarme peut disparaître sans passer par un
 * redémarrage : force stop de l'application, nettoyage agressif par le
 * constructeur (Xiaomi, Huawei, Oppo), restauration de sauvegarde. Sans ce
 * rattrapage, les mémos s'arrêteraient définitivement et en silence.
 *
 * Cette séparation est aussi ce qui garantit l'absence de double notification :
 * un seul composant notifie, l'autre ne fait que planifier.
 */
class MemoNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            NotificationCoordinator.reconcile(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
