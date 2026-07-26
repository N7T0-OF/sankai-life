package com.sankailife

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.sankailife.core.ads.AdsManager
import com.sankailife.core.connectivity.ConnectivityObserver
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.preferences.AppPreferences
import com.sankailife.core.notifications.MemoAlarmScheduler
import com.sankailife.core.notifications.NotificationScheduler
import com.sankailife.core.notifications.SankaiNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SankaiApplication : Application() {

    val database: SankaiDatabase by lazy { SankaiDatabase.getDatabase(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val connectivity: ConnectivityObserver by lazy { ConnectivityObserver(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Les mémos partent via AlarmManager, à l'heure exacte. On reprogramme
        // à chaque lancement : c'est ce qui rattrape une alarme perdue après un
        // force stop ou un nettoyage agressif du constructeur.
        scope.launch { runCatching { MemoAlarmScheduler.replanifierTout(this@SankaiApplication) } }

        // Filet de sécurité périodique, qui replanifie sans jamais notifier.
        NotificationScheduler.programmer(this)

        // AdMob s'initialise en tâche de fond. S'il échoue (hors ligne, SDK
        // indisponible), l'app continue exactement pareil, sans pub.
        AdsManager.initialize(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel(
                    SankaiNotifications.CHANNEL_MEMO, "Mémo du jour",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    SankaiNotifications.CHANNEL_REMINDER, "Rappels",
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    SankaiNotifications.CHANNEL_REWARD, "Récompenses",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    SankaiNotifications.CHANNEL_FOCUS, "Session Focus",
                    NotificationManager.IMPORTANCE_LOW
                )
            ).forEach { manager.createNotificationChannel(it) }
        }
    }
}
