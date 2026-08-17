package com.sankailife.core.concentration

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ProgressSourceEngine
import com.sankailife.core.notifications.NotificationCategory
import com.sankailife.core.notifications.NotificationPolicy
import com.sankailife.core.notifications.SankaiNotifications
import com.sankailife.ui.widgets.AujourdhuiWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Observe la fin d'un minuteur système et la transforme en progression.
 *
 * Le service ne fait rien tant que deux conditions ne sont pas réunies :
 * l'utilisateur a accordé l'accès aux notifications (réglage système, jamais
 * demandé en bloc) et il a activé la source Concentration dans les
 * Paramètres. Il ne lit que les notifications qui ressemblent à une fin de
 * minuteur d'une Horloge connue — jamais le contenu des autres — et ne
 * retient qu'une clé pour ne pas créditer deux fois.
 */
class MinuteurListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        if (!ConcentrationIntegration.estMinuteurFini(
                paquet = sbn.packageName,
                canal = notification.channelId,
                ongoing = sbn.isOngoing
            )
        ) {
            return
        }
        crediter(sbn.key)
    }

    /**
     * Au raccordement (boot, mise à jour, accès accordé), les notifications
     * déjà présentes sont relues : une fin de minuteur survenue pendant que
     * le service n'était pas connecté ne doit pas être perdue.
     */
    override fun onListenerConnected() {
        runCatching {
            activeNotifications?.forEach { sbn ->
                val notification = sbn.notification ?: return@forEach
                if (ConcentrationIntegration.estMinuteurFini(
                        paquet = sbn.packageName,
                        canal = notification.channelId,
                        ongoing = sbn.isOngoing
                    )
                ) {
                    crediter(sbn.key)
                }
            }
        }
    }

    private fun crediter(cle: String) {
        scope.launch {
            runCatching { crediterEnBase(cle) }
        }
    }

    private suspend fun crediterEnBase(cle: String) {
        val app = applicationContext as? SankaiApplication ?: return
        val prefs = app.preferences

        // Sans l'accord explicite de l'utilisateur, rien n'est crédité :
        // l'accès aux notifications reste un choix, pas une conséquence.
        if (!prefs.concentrationActif.first()) return

        val jour = LocalDate.now().toString()
        val deja = prefs.concentrationDejaCredites(jour)
        val aCrediter = ConcentrationIntegration.aCrediter(
            deja,
            listOf(ConcentrationIntegration.MinuteurFini(cle))
        )
        if (aCrediter.isEmpty()) return

        val gain = UserRepository(app.database).addSourceXp(
            ProgressSourceEngine.Source.CONCENTRATION,
            prefs
        )
        if (gain <= 0) return

        prefs.concentrationCrediter(jour, aCrediter.map { it.cle }.toSet())

        // Le widget affiche l'XP du jour : il doit refléter la concentration.
        AujourdhuiWidgetProvider.rafraichirTous(app)

        // Une confirmation discrète, dans la limite du budget quotidien et des
        // catégories. Le crédit d'XP, lui, n'est jamais une notification.
        if (NotificationPolicy.tryAcquire(app, NotificationCategory.FOCUS)) {
            SankaiNotifications.afficherRecompense(
                app,
                app.getString(R.string.concentration_done_title),
                app.getString(R.string.concentration_done_xp, gain)
            )
        }
    }
}
