package com.sankailife.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reprogramme toutes les alarmes après un événement système qui les efface ou
 * les invalide.
 *
 * Android supprime toutes les alarmes au redémarrage, et un changement d'heure
 * ou de fuseau rendrait les instants déjà calculés faux (une notification
 * prévue à 22h00 locale ne doit pas devenir 21h00 après un vol).
 */
class SystemEventsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pertinent = intent.action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (!pertinent) return

        val resultat = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                MemoAlarmScheduler.replanifierTout(appContext)
                // Le rappel de révision se reprogramme lui-même chaque soir,
                // mais un redémarrage efface toutes les alarmes du système :
                // sans cette ligne, la chaîne s'arrêterait au premier reboot.
                RevisionAlarmReceiver.programmerProchaine(appContext)
            } finally {
                resultat.finish()
            }
        }
    }
}
