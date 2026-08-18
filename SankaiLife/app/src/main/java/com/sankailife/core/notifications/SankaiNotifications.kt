package com.sankailife.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sankailife.MainActivity
import com.sankailife.R

/**
 * Point unique d'envoi des notifications locales.
 *
 * Tout passe par ici pour garantir deux choses : la permission POST_NOTIFICATIONS
 * est vérifiée avant chaque envoi (sinon Android 13+ jette silencieusement la
 * notification), et l'appui sur une notification rouvre toujours l'app avec le
 * contexte qui va bien.
 */
object SankaiNotifications {

    const val CHANNEL_MEMO = "sankai_memo"
    const val CHANNEL_REMINDER = "sankai_reminder"
    const val CHANNEL_REWARD = "sankai_reward"

    /** Extra posé sur l'intent quand l'app est ouverte depuis un mémo (donne de l'XP). */
    const val EXTRA_FROM_MEMO = "sankai.from_memo"
    const val EXTRA_MEMO_PROFILE_ID = "sankai.memo_profile_id"
    const val EXTRA_DESTINATION = "sankai.destination"
    const val DESTINATION_MEMO = "memo"
    const val DESTINATION_CAPSULES = "capsules"
    const val DESTINATION_ACADEMY = "academy"
    const val DESTINATION_MOT_DU_JOUR = "mot_du_jour"

    /**
     * Android 13+ exige une permission runtime. Sans elle on n'envoie rien :
     * ce n'est pas une erreur, juste un utilisateur qui a refusé.
     */
    fun peutNotifier(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun afficherMemo(context: Context, profileId: Long, nomModule: String, phrase: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_MEMO, true)
            putExtra(EXTRA_MEMO_PROFILE_ID, profileId)
            putExtra(EXTRA_DESTINATION, DESTINATION_MEMO)
        }
        afficher(
            context = context,
            canalId = CHANNEL_MEMO,
            notificationId = (1000 + profileId).toInt(),
            titre = "💡 $nomModule",
            texte = phrase,
            intent = intent
        )
    }

    fun afficherRappel(
        context: Context,
        titre: String,
        texte: String,
        notificationId: Int = 2000,
        destination: String? = null
    ) {
        val intent = destination?.let { route ->
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DESTINATION, route)
            }
        }
        afficher(context, CHANNEL_REMINDER, notificationId, titre, texte, intent)
    }

    fun afficherRecompense(context: Context, titre: String, texte: String, notificationId: Int = 3000) {
        afficher(context, CHANNEL_REWARD, notificationId, titre, texte)
    }

    // La permission est vérifiée juste au-dessus par peutNotifier(). Lint ne
    // suit pas ce garde centralisé jusqu'à l'appel de NotificationManagerCompat.
    @SuppressLint("MissingPermission")
    private fun afficher(
        context: Context,
        canalId: String,
        notificationId: Int,
        titre: String,
        texte: String,
        intent: Intent? = null
    ) {
        if (!peutNotifier(context)) return

        val ouvrir = intent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            ouvrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // Peut encore échouer si l'utilisateur a coupé le canal : on l'ignore.
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
