package com.sankailife.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Programmation des notifications mémo via AlarmManager.
 *
 * Pourquoi pas WorkManager : son intervalle minimum est de 15 minutes et le
 * système regroupe les réveils pour économiser la batterie. Une notification
 * demandée à 22h00 arrivait donc vers 22h08. AlarmManager avec
 * `setExactAndAllowWhileIdle` est le seul mécanisme qui respecte l'heure
 * exacte, y compris en Doze.
 *
 * WorkManager reste utilisé, mais uniquement comme filet de sécurité qui
 * reprogramme les alarmes — jamais pour notifier.
 */
object MemoAlarmScheduler {

    const val ACTION_MEMO = "com.sankailife.action.MEMO_ALARM"
    const val EXTRA_PROFILE_ID = "profileId"

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Android 12+ demande une autorisation dédiée pour les alarmes exactes.
     * Sans elle, on retombe sur une alarme approximative — mieux que
     * WorkManager, mais avec quelques minutes de dérive possibles.
     */
    fun peutPlanifierExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager(context)?.canScheduleExactAlarms() == true
    }

    /** Intent vers l'écran système d'autorisation des alarmes exactes. */
    fun intentReglageAlarmesExactes(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    private fun pendingIntent(context: Context, profileId: Long): PendingIntent {
        val intent = Intent(context, MemoAlarmReceiver::class.java).apply {
            action = ACTION_MEMO
            putExtra(EXTRA_PROFILE_ID, profileId)
            // Sans data unique, Android considérerait toutes les alarmes comme
            // identiques et n'en garderait qu'une seule.
            data = android.net.Uri.parse("sankai://memo/$profileId")
        }
        return PendingIntent.getBroadcast(
            context,
            profileId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Annule l'alarme d'un module. */
    fun annuler(context: Context, profileId: Long) {
        runCatching { alarmManager(context)?.cancel(pendingIntent(context, profileId)) }
    }

    /**
     * Coupe réellement tous les rappels Mémo.
     *
     * Modifier seulement DataStore laissait auparavant les PendingIntent déjà
     * posés réveiller l'application. Ils n'affichaient parfois rien, mais le
     * téléphone était tout de même sollicité.
     */
    suspend fun annulerTout(context: Context) {
        val dao = SankaiDatabase.getDatabase(context).memoDao()
        val profils = runCatching { dao.getAllProfilesOnce() }.getOrElse { return }
        profils.forEach { profil ->
            annuler(context, profil.id)
            runCatching { dao.updateNextTrigger(profil.id, 0L) }
        }
    }

    /**
     * Recalcule et reprogramme l'alarme d'un module.
     * @return l'instant programmé, ou null si le module ne se déclenchera pas.
     */
    suspend fun planifier(
        context: Context,
        profil: MemoProfileEntity,
        heuresSilencieuses: QuietHours
    ): Long? {
        annuler(context, profil.id)
        if (!profil.isActive) {
            SankaiDatabase.getDatabase(context).memoDao().updateNextTrigger(profil.id, 0L)
            return null
        }

        val quand = MemoScheduleEngine.prochainDeclenchementMillis(
            profil = profil,
            maintenant = LocalDateTime.now(),
            heuresSilencieuses = heuresSilencieuses
        )
        val dao = SankaiDatabase.getDatabase(context).memoDao()

        if (quand == null) {
            dao.updateNextTrigger(profil.id, 0L)
            return null
        }

        val manager = alarmManager(context)
        val pi = pendingIntent(context, profil.id)
        val pose = runCatching {
            if (peutPlanifierExact(context)) {
                manager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
            } else {
                // Dégradation assumée et signalée dans l'écran de diagnostic.
                manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
            }
        }.isSuccess

        dao.updateNextTrigger(profil.id, if (pose) quand else 0L)
        return if (pose) quand else null
    }

    /**
     * Reprogramme tous les modules actifs.
     *
     * Appelé au démarrage de l'app, après un redémarrage du téléphone, après
     * un changement d'heure ou de fuseau, et à chaque modification d'un module.
     * Annule systématiquement avant de reposer : c'est ce qui empêche les
     * notifications en double.
     */
    suspend fun replanifierTout(context: Context) {
        val dao = SankaiDatabase.getDatabase(context).memoDao()
        val prefs = AppPreferences(context)
        val heures = prefs.heuresSilencieuses.first()

        val profils = runCatching { dao.getAllProfilesOnce() }.getOrElse { return }
        for (profil in profils) {
            runCatching { planifier(context, profil, heures) }
        }
    }
}
