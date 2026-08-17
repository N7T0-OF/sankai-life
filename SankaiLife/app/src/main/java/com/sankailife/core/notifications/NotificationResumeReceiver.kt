package com.sankailife.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Réactive les planifications à la fin d'une pause choisie. */
class NotificationResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationCoordinator.reconcile(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 6010

        /** La pause est inclusive ; reprise le lendemain à 9 h locale. */
        fun schedule(context: Context, pauseUntilEpochDay: Long) {
            val zone = ZoneId.systemDefault()
            val triggerAt = LocalDate.ofEpochDay(pauseUntilEpochDay + 1L)
                .atTime(9, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            runCatching {
                if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }

        fun cancel(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
            alarms.cancel(pending)
        }

        private fun pendingIntent(context: Context, flag: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, NotificationResumeReceiver::class.java),
                flag or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
