package com.sankailife.core.notifications

import android.content.Context
import com.sankailife.SankaiApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Réconcilie les alarmes Android avec une seule source de vérité.
 *
 * La politique décide si une notification peut être montrée. Ce coordinateur
 * va plus loin : quand une catégorie est coupée ou mise en pause, il retire
 * aussi ses réveils afin de ne pas solliciter inutilement le téléphone.
 */
object NotificationCoordinator {

    suspend fun reconcile(context: Context) {
        val app = context.applicationContext as? SankaiApplication ?: return
        val prefs = app.preferences
        val today = LocalDate.now().toEpochDay()
        val masterEnabled = prefs.notifications.first()
        val paused = prefs.notificationPauseUntilEpochDay.first() >= today

        if (!masterEnabled || paused) {
            MemoAlarmScheduler.annulerTout(app)
            RevisionAlarmReceiver.annuler(app)
            NotificationScheduler.annuler(app)
            if (masterEnabled && paused) {
                NotificationResumeReceiver.schedule(
                    app,
                    prefs.notificationPauseUntilEpochDay.first()
                )
            } else {
                NotificationResumeReceiver.cancel(app)
            }
            return
        }

        NotificationResumeReceiver.cancel(app)

        if (prefs.notifyMemo.first()) {
            MemoAlarmScheduler.replanifierTout(app)
            NotificationScheduler.programmer(app)
        } else {
            MemoAlarmScheduler.annulerTout(app)
            NotificationScheduler.annuler(app)
        }

        if (prefs.notifyLearning.first() || prefs.notifyCulture.first()) {
            RevisionAlarmReceiver.programmerProchaine(app)
        } else {
            RevisionAlarmReceiver.annuler(app)
        }
    }
}
