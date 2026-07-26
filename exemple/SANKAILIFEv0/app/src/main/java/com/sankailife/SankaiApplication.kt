package com.sankailife

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.preferences.AppPreferences

class SankaiApplication : Application() {

    val database: SankaiDatabase by lazy { SankaiDatabase.getDatabase(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel("sankai_memo",    "Mémo du jour",        NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("sankai_reminder", "Rappels",             NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("sankai_reward",   "Récompenses",         NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("sankai_focus",    "Session Focus",       NotificationManager.IMPORTANCE_LOW)
            ).forEach { manager.createNotificationChannel(it) }
        }
    }
}
