package com.sankailife.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.learning.AvailableLearningLanguages
import com.sankailife.core.motdujour.MotDuJourSelector
import com.sankailife.core.motdujour.MotDuJourStore
import com.sankailife.core.motdujour.drapeau
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * La notification quotidienne du mot du jour.
 *
 * Une seule par jour, à l'heure choisie dans les réglages, et **seulement si
 * la politique le permet** (interrupteur général, catégorie, heures
 * silencieuses, budget quotidien). Le mot lui-même est calculé hors-ligne par
 * le sélecteur : la notification annonce exactement ce que l'écran montrera.
 *
 * Toucher la notification ouvre l'écran du mot du jour, pas l'accueil : c'est
 * la « petite découverte » — une chose à lire, puis à fermer.
 */
class MotDuJourAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultat = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SankaiApplication ?: return@launch

                // Porte unique : maître, catégorie, heures silencieuses et
                // budget quotidien sont décidés au même endroit.
                if (!NotificationPolicy.tryAcquire(
                        context,
                        NotificationCategory.MOT_DU_JOUR
                    )
                ) return@launch

                // Même filtre que l'écran : la notification n'annonce jamais
                // un mot dans une langue que l'utilisateur n'a pas.
                val langues = AvailableLearningLanguages.pour(app.database)
                val mot = MotDuJourSelector.selectionner(
                    MotDuJourStore.lire(context).filter { it.codeLangue in langues },
                    LocalDate.now()
                ) ?: return@launch

                SankaiNotifications.afficherRappel(
                    context = context,
                    titre = context.getString(R.string.notif_word_title),
                    texte = context.getString(
                        R.string.notif_word_body,
                        mot.mot,
                        mot.definition
                    ).let { "${mot.drapeau()} $it" },
                    notificationId = ID,
                    destination = SankaiNotifications.DESTINATION_MOT_DU_JOUR
                )
            } finally {
                // L'alarme du lendemain est reprogrammée quoi qu'il arrive :
                // un seul déclenchement sauté ne doit pas arrêter la chaîne.
                runCatching { NotificationCoordinator.reconcile(context) }
                resultat.finish()
            }
        }
    }

    companion object {
        private const val ID = 6100
        private const val CODE = 6101

        fun programmerProchaine(context: Context, heureMinutes: Int) {
            val zone = ZoneId.systemDefault()
            val quand = MotDuJourNotifEngine.prochaineHeure(
                heureMinutes = heureMinutes,
                maintenant = LocalDateTime.now(zone)
            ).atZone(zone).toInstant().toEpochMilli()

            val alarmes = context.getSystemService(AlarmManager::class.java) ?: return
            val enAttente = PendingIntent.getBroadcast(
                context, CODE,
                Intent(context, MotDuJourAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmes.canScheduleExactAlarms()
            runCatching {
                if (exact) {
                    alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, enAttente)
                } else {
                    alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, enAttente)
                }
            }
        }

        fun annuler(context: Context) {
            val alarmes = context.getSystemService(AlarmManager::class.java) ?: return
            val enAttente = PendingIntent.getBroadcast(
                context, CODE,
                Intent(context, MotDuJourAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            alarmes.cancel(enAttente)
        }
    }
}
