package com.sankailife.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sankailife.MainActivity
import com.sankailife.R
import com.sankailife.core.data.db.SankaiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Widget « Aujourd'hui » : la journée en un coup d'œil, sans ouvrir l'app.
 *
 * Il affiche deux informations utiles et rien d'autre : le nombre de cartes à
 * réviser et l'heure du prochain rappel. Aucun compteur de jeu, aucune
 * récompense — le widget est aussi sobre que l'écran qu'il représente.
 */
class AujourdhuiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> mettreAJour(context, appWidgetManager, id) }
    }

    override fun onEnabled(context: Context) {
        planifierRafraichissement(context)
        rafraichirTous(context)
    }

    override fun onDisabled(context: Context) {
        runCatching {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TACHE_RAFRAICHISSEMENT)
        }
    }

    companion object {
        private const val TACHE_RAFRAICHISSEMENT = "widget_aujourdhui"

        /** Met à jour tous les widgets posés, sans en connaître les ids. */
        fun rafraichirTous(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AujourdhuiWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            ids.forEach { id -> mettreAJour(context, manager, id) }
        }

        private fun mettreAJour(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val vues = RemoteViews(context.packageName, R.layout.widget_aujourdhui)
            val nuit = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val texte = if (nuit) Color.WHITE else Color.rgb(0x1F, 0x23, 0x2B)
            val secondaire = if (nuit) Color.rgb(0xB0, 0xB6, 0xC3) else Color.rgb(0x6B, 0x72, 0x80)
            val fond = if (nuit) Color.rgb(0x16, 0x1A, 0x21) else Color.rgb(0xFF, 0xFF, 0xFF)

            vues.setTextColor(R.id.widget_titre, texte)
            vues.setTextColor(R.id.widget_cartes, texte)
            vues.setTextColor(R.id.widget_memo, secondaire)
            vues.setInt(R.id.widget_racine, "setBackgroundColor", fond)

            // Ouvrir l'app sur l'écran Aujourd'hui au toucher.
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            vues.setOnClickPendingIntent(R.id.widget_racine, pending)

            // Lecture locale : le widget ne quitte jamais l'appareil.
            CoroutineScope(Dispatchers.IO).launch {
                val base = SankaiDatabase.getDatabase(context)
                val maintenant = System.currentTimeMillis()
                val dues = base.memoDao()
                    .compterToutesCartesDues(maintenant)
                    .first()
                val prochainMemo = base.memoDao()
                    .getAllProfilesOnce()
                    .asSequence()
                    .filter { it.isActive && it.nextTriggerAtMillis > maintenant }
                    .minByOrNull { it.nextTriggerAtMillis }

                val libelleCartes = when {
                    dues <= 0 -> context.getString(R.string.widget_no_due)
                    dues == 1 -> context.getString(R.string.widget_due_one)
                    else -> context.getString(R.string.widget_due_many, dues)
                }
                val libelleMemo = prochainMemo?.nextTriggerAtMillis?.let { millis ->
                    val heure = DateTimeFormatter.ofPattern("HH'h'mm")
                        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
                    context.getString(R.string.widget_next_memo, heure)
                } ?: context.getString(R.string.widget_no_memo)

                vues.setTextViewText(R.id.widget_cartes, libelleCartes)
                vues.setTextViewText(R.id.widget_memo, libelleMemo)
                manager.updateAppWidget(widgetId, vues)
            }
        }

        /**
         * Rafraîchissement périodique discret.
         *
         * WorkManager se charge des contraintes système ; le widget se met
         * aussi à jour à l'ouverture de l'application, donc l'intervalle long
         * ne fait que couvrir les jours où l'app n'est pas lancée.
         */
        private fun planifierRafraichissement(context: Context) {
            val requete = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                6, TimeUnit.HOURS
            ).build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    TACHE_RAFRAICHISSEMENT,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    requete
                )
            }
        }
    }
}

/** Tâche de fond discrète qui actualise le widget sans toucher aux alarmes. */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AujourdhuiWidgetProvider.rafraichirTous(applicationContext)
        return Result.success()
    }
}
