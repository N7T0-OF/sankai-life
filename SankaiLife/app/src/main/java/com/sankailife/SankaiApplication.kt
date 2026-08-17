package com.sankailife

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.sankailife.core.connectivity.ConnectivityObserver
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.preferences.AppPreferences
import com.sankailife.core.notifications.NotificationCoordinator
import com.sankailife.core.notifications.SankaiNotifications
import com.sankailife.ui.widgets.AujourdhuiWidgetProvider
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

        // Une seule réconciliation respecte le commutateur maître, les
        // catégories et les pauses, tout en réparant les alarmes perdues.
        scope.launch {
            runCatching { NotificationCoordinator.reconcile(this@SankaiApplication) }
        }

        // Le widget se met à jour à chaque ouverture de l'app ; le rafraîchissement
        // périodique ne couvre que les jours où elle n'est pas lancée.
        scope.launch {
            runCatching { AujourdhuiWidgetProvider.rafraichirTous(this@SankaiApplication) }
        }
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
                )
            ).forEach { manager.createNotificationChannel(it) }
        }
    }
}
