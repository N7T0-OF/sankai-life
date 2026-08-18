package com.sankailife.core.notifications

import android.content.Context
import com.sankailife.core.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class NotificationCategory {
    LEARNING,
    MEMO,
    CULTURE,
    FOCUS,
    MOT_DU_JOUR
}

data class NotificationPolicyInput(
    val masterEnabled: Boolean,
    val categoryEnabled: Boolean,
    val quietHours: QuietHours,
    val minuteOfDay: Int,
    val epochDay: Long,
    val pauseUntilEpochDay: Long,
    val weekendQuiet: Boolean,
    val dayOfWeek: DayOfWeek
)

/** Décision pure, testable sans Android. */
fun notificationAllowed(input: NotificationPolicyInput): Boolean {
    if (!input.masterEnabled || !input.categoryEnabled) return false
    if (input.pauseUntilEpochDay >= input.epochDay) return false
    if (input.weekendQuiet && input.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        return false
    }
    return !input.quietHours.contient(input.minuteOfDay)
}

/**
 * Porte unique des notifications spontanées.
 *
 * Le compteur est synchrone et minuscule afin que deux alarmes déclenchées la
 * même seconde ne dépassent pas le maximum quotidien choisi. Les notifications
 * de test n'utilisent pas cette porte et restent donc toujours testables.
 */
object NotificationPolicy {
    private const val STORE = "sankai_notification_budget"
    private const val KEY_DAY = "day"
    private const val KEY_COUNT = "count"
    private val lock = Any()

    suspend fun tryAcquire(
        context: Context,
        category: NotificationCategory,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Boolean {
        if (!SankaiNotifications.peutNotifier(context)) return false
        val prefs = AppPreferences(context)
        val categoryEnabled = when (category) {
            NotificationCategory.LEARNING -> prefs.notifyLearning.first()
            NotificationCategory.MEMO -> prefs.notifyMemo.first()
            NotificationCategory.CULTURE -> prefs.notifyCulture.first()
            NotificationCategory.FOCUS -> prefs.notifyFocus.first()
            NotificationCategory.MOT_DU_JOUR -> prefs.notifyMotDuJour.first()
        }
        val allowed = notificationAllowed(
            NotificationPolicyInput(
                masterEnabled = prefs.notifications.first(),
                categoryEnabled = categoryEnabled,
                quietHours = prefs.heuresSilencieuses.first(),
                minuteOfDay = now.hour * 60 + now.minute,
                epochDay = now.toLocalDate().toEpochDay(),
                pauseUntilEpochDay = prefs.notificationPauseUntilEpochDay.first(),
                weekendQuiet = prefs.weekendQuiet.first(),
                dayOfWeek = now.dayOfWeek
            )
        )
        if (!allowed) return false

        val maximum = prefs.notificationDailyMax.first().coerceIn(1, 3)
        return synchronized(lock) {
            val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val today = now.toLocalDate().toEpochDay()
            val storedDay = store.getLong(KEY_DAY, Long.MIN_VALUE)
            val count = if (storedDay == today) store.getInt(KEY_COUNT, 0) else 0
            if (count >= maximum) {
                false
            } else {
                store.edit().putLong(KEY_DAY, today).putInt(KEY_COUNT, count + 1).apply()
                true
            }
        }
    }
}
