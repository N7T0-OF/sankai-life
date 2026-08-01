package com.sankailife.core.notifications

import android.app.AlarmManager
import com.sankailife.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sankailife.SankaiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le rappel de révision.
 *
 * Une seule notification par jour, à heure fixe, et **seulement s'il y a
 * vraiment des cartes dues**. C'est ce qui la distingue d'un rappel
 * d'engagement : elle n'existe que quand elle a quelque chose à dire.
 *
 * Une notification quotidienne qui annonce « 0 carte à réviser » apprend en
 * quelques jours qu'on peut l'ignorer — et le jour où elle compte vraiment,
 * elle est déjà devenue du bruit.
 */
class RevisionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultat = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SankaiApplication ?: return@launch
                val prefs = app.preferences

                if (!prefs.notifications.first()) return@launch

                // Heures silencieuses : on ne reporte pas, on saute. Réveiller
                // quelqu'un à 7 h pour un rappel de 23 h serait pire que de ne
                // rien envoyer.
                val heures = prefs.heuresSilencieuses.first()
                val maintenant = LocalTime.now()
                if (heures.contient(maintenant.hour * 60 + maintenant.minute)) return@launch

                val dues = app.database.memoDao()
                    .compterToutesCartesDues(System.currentTimeMillis())
                    .first()
                if (dues <= 0) return@launch

                SankaiNotifications.afficherRappel(
                    context = context,
                    titre = context.getString(R.string.notif_review_title),
                    texte = if (dues == 1) context.getString(R.string.notif_review_one)
                    else context.getString(R.string.notif_review_many, dues),
                    notificationId = ID
                )
            } finally {
                // L'alarme du lendemain est reprogrammée quoi qu'il arrive :
                // même sautée, la chaîne ne doit pas s'interrompre, sinon un
                // seul jour sans carte due arrêterait les rappels pour de bon.
                programmerProchaine(context)
                resultat.finish()
            }
        }
    }

    companion object {
        private const val ID = 6000
        private const val CODE = 6001

        /** Heure du rappel. Le soir, quand on a le temps de s'y mettre. */
        const val HEURE_PAR_DEFAUT = 19

        fun programmerProchaine(context: Context, heure: Int = HEURE_PAR_DEFAUT) {
            val zone = ZoneId.systemDefault()
            val maintenant = LocalDateTime.now(zone)
            val cible = LocalDate.now(zone)
                .atTime(heure, 0)
                .let { if (it.isAfter(maintenant)) it else it.plusDays(1) }

            val quand = cible.atZone(zone).toInstant().toEpochMilli()
            val alarmes = context.getSystemService(AlarmManager::class.java) ?: return

            val enAttente = PendingIntent.getBroadcast(
                context, CODE,
                Intent(context, RevisionAlarmReceiver::class.java),
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
                Intent(context, RevisionAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            alarmes.cancel(enAttente)
        }
    }
}
