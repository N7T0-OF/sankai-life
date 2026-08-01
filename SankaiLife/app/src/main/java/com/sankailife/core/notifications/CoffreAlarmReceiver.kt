package com.sankailife.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sankailife.SankaiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * La notification « coffre prêt ».
 *
 * Un coffre met des heures à s'ouvrir. Sans rappel, on revient bien après —
 * et un coffre qui a fini depuis longtemps bloque son emplacement, donc toute
 * la progression derrière.
 *
 * **Une seule notification par coffre.** L'identifiant de la notification est
 * celui du coffre : Android remplace alors l'ancienne au lieu d'en empiler
 * une deuxième, même si l'alarme est reprogrammée après un redémarrage.
 */
class CoffreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val coffreId = intent.getLongExtra(EXTRA_COFFRE, -1L)
        if (coffreId < 0) return

        val resultat = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SankaiApplication ?: return@launch

                // Le coffre est relu avant d'annoncer quoi que ce soit : il a
                // pu être ouvert entre la programmation de l'alarme et son
                // déclenchement, et annoncer un coffre déjà ouvert ferait
                // revenir quelqu'un pour rien.
                val coffre = app.database.chestDao().getActiveChestsOnce()
                    .firstOrNull { it.id == coffreId } ?: return@launch
                if (coffre.isOpened) return@launch

                SankaiNotifications.afficherRecompense(
                    context = context,
                    titre = "Ton coffre est prêt",
                    texte = "Ouvre Sankai Life pour récupérer ta récompense.",
                    notificationId = ID_BASE + coffreId.toInt()
                )
            } finally {
                resultat.finish()
            }
        }
    }

    companion object {
        const val EXTRA_COFFRE = "coffreId"

        /**
         * Plage d'identifiants réservée aux coffres.
         *
         * Assez loin des autres canaux pour qu'un coffre ne remplace jamais un
         * rappel de révision, et inversement.
         */
        private const val ID_BASE = 5000

        /**
         * Programme le rappel d'un coffre.
         *
         * L'alarme exacte n'est demandée que si le système l'autorise. Sans
         * cette permission, Android refuse l'appel et lève une exception :
         * mieux vaut un rappel approximatif qu'une application qui plante.
         */
        fun programmer(context: Context, coffreId: Long, pretALeMillis: Long) {
            if (pretALeMillis <= System.currentTimeMillis()) return

            val alarmes = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, CoffreAlarmReceiver::class.java)
                .putExtra(EXTRA_COFFRE, coffreId)

            val enAttente = PendingIntent.getBroadcast(
                context,
                ID_BASE + coffreId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmes.canScheduleExactAlarms()

            runCatching {
                if (exact) {
                    alarmes.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, pretALeMillis, enAttente
                    )
                } else {
                    alarmes.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, pretALeMillis, enAttente
                    )
                }
            }
        }

        /** Annule le rappel d'un coffre ouvert avant l'heure. */
        fun annuler(context: Context, coffreId: Long) {
            val alarmes = context.getSystemService(AlarmManager::class.java) ?: return
            val enAttente = PendingIntent.getBroadcast(
                context,
                ID_BASE + coffreId.toInt(),
                Intent(context, CoffreAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            alarmes.cancel(enAttente)
        }
    }
}
