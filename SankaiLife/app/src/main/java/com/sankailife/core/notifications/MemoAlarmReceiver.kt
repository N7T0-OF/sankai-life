package com.sankailife.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.domain.engine.MemoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reçoit l'alarme d'un module mémo : envoie la phrase, puis reprogramme
 * immédiatement le déclenchement suivant.
 *
 * Une alarme AlarmManager est à usage unique. Sans cette reprogrammation en
 * fin de traitement, le module ne notifierait qu'une seule fois.
 */
class MemoAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getLongExtra(MemoAlarmScheduler.EXTRA_PROFILE_ID, -1L)
        if (profileId <= 0L) return

        // goAsync donne ~10 s pour finir un traitement en arrière-plan sans
        // que le système ne tue le processus au retour de onReceive.
        val resultat = goAsync()
        scope.launch {
            try {
                traiter(context.applicationContext, profileId)
            } finally {
                resultat.finish()
            }
        }
    }

    private suspend fun traiter(context: Context, profileId: Long) {
        val dao = SankaiDatabase.getDatabase(context).memoDao()
        val profil = runCatching { dao.getProfile(profileId) }.getOrNull() ?: return

        // Même en silence on reprogramme : c'est le silence qui saute, pas le module.
        if (profil.isActive) {
            envoyerPhrase(context, profileId, profil.name, profil.sentLineHistory)
        }

        // Une alarme est à usage unique. La réconciliation centrale la repose
        // seulement si les notifications Mémo sont encore autorisées.
        runCatching { NotificationCoordinator.reconcile(context) }
    }

    private suspend fun envoyerPhrase(
        context: Context,
        profileId: Long,
        nomModule: String,
        historique: String
    ) {
        val dao = SankaiDatabase.getDatabase(context).memoDao()
        val lignes = dao.getLinesOnce(profileId)
        if (lignes.isEmpty()) return

        val choisie = MemoEngine.getRandomLine(lignes.map { it.id }, historique) ?: return
        val texte = lignes.firstOrNull { it.id == choisie }?.text ?: return
        // Le budget n'est consommé qu'après avoir établi qu'un contenu réel
        // peut être envoyé.
        if (!NotificationPolicy.tryAcquire(context, NotificationCategory.MEMO)) return

        SankaiNotifications.afficherMemo(context, profileId, nomModule, texte)
        dao.updateHistory(profileId, MemoEngine.updateHistory(historique, choisie))
        dao.updateLastNotified(profileId, System.currentTimeMillis())
    }
}
