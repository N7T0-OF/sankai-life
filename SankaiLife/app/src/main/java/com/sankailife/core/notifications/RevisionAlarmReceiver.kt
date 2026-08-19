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

                // Heures silencieuses : on ne reporte pas, on saute. Réveiller
                // quelqu'un à 7 h pour un rappel de 23 h serait pire que de ne
                // rien envoyer.

                val dues = app.database.memoDao()
                    .compterToutesCartesDues(System.currentTimeMillis())
                    .first()
                val learningReady = dues > 0 && app.preferences.notifyLearning.first()
                val cultureReady = app.preferences.notifyCulture.first()
                if (!learningReady && !cultureReady) return@launch
                if (!NotificationPolicy.tryAcquire(
                        context,
                        if (learningReady) NotificationCategory.LEARNING
                        else NotificationCategory.CULTURE
                    )
                ) return@launch

                // La découverte du jour porte la notification : le titre et un
                // extrait de la capsule elle-même, pas un message générique.
                // C'est le « Sankai Moment » — une chose à lire, puis à fermer.
                //
                // L'historique réel est lu (même état local que l'écran) pour
                // que la notification annonce exactement la capsule qui
                // s'ouvrira — jamais une déjà vue hier.
                val capsule = if (cultureReady) {
                    val local = com.sankailife.core.culture.CultureLocalState(context)
                    val userId = app.database.userDao().getUserOnce()?.id ?: 1L
                    val profileId = "user-$userId"
                    com.sankailife.core.culture.DailyDiscovery.duJour(
                        context,
                        profileId = profileId,
                        history = local.history(profileId),
                        // Même source de langues que l'écran Culture : la
                        // notification n'annonce jamais une capsule dans une
                        // langue que l'utilisateur n'a pas.
                        enabledLanguages = com.sankailife.core.learning
                            .AvailableLearningLanguages.pour(app.database)
                    )
                } else null

                SankaiNotifications.afficherRappel(
                    context = context,
                    titre = when {
                        cultureReady && !learningReady -> capsule?.let {
                            context.getString(R.string.notif_culture_discovery_title, it.title)
                        } ?: context.getString(R.string.notif_culture_title)
                        else -> context.getString(R.string.notif_review_title)
                    },
                    texte = when {
                        cultureReady && learningReady -> context.getString(
                            R.string.notif_daily_combined,
                            capsule?.let { titreEtExtrait(it) } ?: ""
                        )
                        cultureReady -> capsule?.let { titreEtExtrait(it) }
                            ?: context.getString(R.string.notif_culture_body)
                        dues == 1 -> context.getString(R.string.notif_review_one)
                        else -> context.getString(R.string.notif_review_many, dues)
                    },
                    notificationId = ID,
                    destination = if (cultureReady) {
                        SankaiNotifications.DESTINATION_CAPSULES
                    } else {
                        SankaiNotifications.DESTINATION_ACADEMY
                    }
                )
            } finally {
                // L'alarme du lendemain est reprogrammée quoi qu'il arrive :
                // même sautée, la chaîne ne doit pas s'interrompre, sinon un
                // seul jour sans carte due arrêterait les rappels pour de bon.
                runCatching { NotificationCoordinator.reconcile(context) }
                resultat.finish()
            }
        }
    }

    /** Le titre suivi d'un court extrait du corps, sur une ou deux lignes. */
    private fun titreEtExtrait(
        capsule: com.sankailife.core.culture.DailyCultureEntry
    ): String {
        val extrait = capsule.body
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(90)
            ?.let { "$it…" }
            ?: capsule.context ?: capsule.sourceLabel ?: ""
        return if (extrait.isBlank()) capsule.title else "${capsule.title} — $extrait"
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
