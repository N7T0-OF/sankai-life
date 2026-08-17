package com.sankailife.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sankailife.core.notifications.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sankai_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE       = stringPreferencesKey("theme_mode")
        val COULEURS_SYSTEME = booleanPreferencesKey("couleurs_systeme")
        val PALETTE          = stringPreferencesKey("palette")
        val SHOW_NAV_LABELS  = booleanPreferencesKey("show_nav_labels")
        val VIBRATIONS       = booleanPreferencesKey("vibrations")
        val NOTIFICATIONS    = booleanPreferencesKey("notifications")
        val BATTERY_SAVER    = booleanPreferencesKey("battery_saver")
        val GRAPHICS_QUALITY = stringPreferencesKey("graphics_quality")
        val STREAK_REMINDER  = booleanPreferencesKey("streak_reminder")
        val FOCUS_KEEP_SCREEN= booleanPreferencesKey("focus_keep_screen")
        val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")

        // Audio d'apprentissage. La lecture automatique est active par defaut :
        // entendre un mot etranger des qu'il apparait est la premiere chose qui
        // manque a qui apprend une langue, et l'exiger d'un appui rend l'ecoute
        // optionnelle alors qu'elle ne devrait pas l'etre.
        val LECTURE_AUTO     = booleanPreferencesKey("lecture_auto")
        val VITESSE_VOIX     = stringPreferencesKey("vitesse_voix")
        val REPETITIONS_VOIX = intPreferencesKey("repetitions_voix")

        // Heures silencieuses, stockées en minutes depuis minuit : un seul
        // entier évite les incohérences entre un champ heure et un champ minute.
        val QUIET_ENABLED    = booleanPreferencesKey("quiet_enabled")
        val QUIET_START      = intPreferencesKey("quiet_start_minutes")
        val QUIET_END        = intPreferencesKey("quiet_end_minutes")

        // Bien-etre numerique : une limite choisie, jamais un objectif qui
        // pousse a rester dans l'application.
        val MINIMAL_MODE           = booleanPreferencesKey("minimal_mode")
        val DAILY_MINUTES          = intPreferencesKey("daily_minutes")
        val TODAY_COMPLETED_DATE   = stringPreferencesKey("today_completed_date")
        val NOTIFICATION_DAILY_MAX = intPreferencesKey("notification_daily_max")
        val NOTIFICATION_PAUSE_UNTIL = longPreferencesKey("notification_pause_until_epoch_day")
        val WEEKEND_QUIET          = booleanPreferencesKey("weekend_quiet")

        // Categories independantes. Le Jardin reste silencieux par defaut.
        val NOTIFY_LEARNING = booleanPreferencesKey("notify_learning")
        val NOTIFY_MEMO     = booleanPreferencesKey("notify_memo")
        val NOTIFY_CULTURE  = booleanPreferencesKey("notify_culture")
        val NOTIFY_FOCUS    = booleanPreferencesKey("notify_focus")
        val NOTIFY_GARDEN   = booleanPreferencesKey("notify_garden")

        val CULTURE_ORIENTATION = stringPreferencesKey("culture_orientation")
        val CULTURE_STYLE       = stringPreferencesKey("culture_style")
    }

    val themeMode: Flow<String>       = pref(Keys.THEME_MODE, "dark")

    /**
     * Reprendre les couleurs du téléphone (Material You).
     *
     * Activé par défaut : sur un appareil qui les fournit, une application qui
     * ignore la palette du système jure avec tout le reste. Le réglage existe
     * quand même — quelqu'un qui a choisi un thème dans le jeu doit pouvoir le
     * garder intact.
     */
    val couleursSysteme: Flow<Boolean> = pref(Keys.COULEURS_SYSTEME, true)

    /** Lire automatiquement chaque nouveau mot ou phrase. */
    val lectureAuto: Flow<Boolean> = pref(Keys.LECTURE_AUTO, true)

    /** « lente », « normale » ou « rapide ». */
    val vitesseVoix: Flow<String> = pref(Keys.VITESSE_VOIX, "normale")

    /** Relectures apres la premiere, de 0 a 2. */
    val repetitionsVoix: Flow<Int> = pref(Keys.REPETITIONS_VOIX, 0)

    /**
     * Palette active : « sankai » ou « systeme ».
     *
     * Sans valeur enregistrée, on retombe sur l'ancien réglage booléen. C'est
     * ce qui garde son apparence à quelqu'un qui jouait déjà : écraser une
     * préférence existante parce qu'on a changé sa forme serait le pire des
     * accueils pour une mise à jour.
     */
    val palette: Flow<String> = pref(Keys.PALETTE, "")
    val showNavLabels: Flow<Boolean>  = pref(Keys.SHOW_NAV_LABELS, true)
    val vibrations: Flow<Boolean>     = pref(Keys.VIBRATIONS, true)
    val notifications: Flow<Boolean>  = pref(Keys.NOTIFICATIONS, true)
    val batterySaver: Flow<Boolean>   = pref(Keys.BATTERY_SAVER, false)
    val graphicsQuality: Flow<String> = pref(Keys.GRAPHICS_QUALITY, "normal")
    val streakReminder: Flow<Boolean> = pref(Keys.STREAK_REMINDER, false)
    val focusKeepScreen: Flow<Boolean> = pref(Keys.FOCUS_KEEP_SCREEN, true)

    val quietEnabled: Flow<Boolean> = pref(Keys.QUIET_ENABLED, true)
    val quietStartMinutes: Flow<Int> = pref(Keys.QUIET_START, 21 * 60)
    val quietEndMinutes: Flow<Int> = pref(Keys.QUIET_END, 9 * 60)

    val minimalMode: Flow<Boolean> = pref(Keys.MINIMAL_MODE, false)
    /** 0 signifie « sans objectif ». */
    val dailyMinutes: Flow<Int> = pref(Keys.DAILY_MINUTES, 5)
    val todayCompletedDate: Flow<String> = pref(Keys.TODAY_COMPLETED_DATE, "")
    val notificationDailyMax: Flow<Int> = pref(Keys.NOTIFICATION_DAILY_MAX, 1)
    val notificationPauseUntilEpochDay: Flow<Long> =
        pref(Keys.NOTIFICATION_PAUSE_UNTIL, 0L)
    val weekendQuiet: Flow<Boolean> = pref(Keys.WEEKEND_QUIET, false)
    val notifyLearning: Flow<Boolean> = pref(Keys.NOTIFY_LEARNING, true)
    val notifyMemo: Flow<Boolean> = pref(Keys.NOTIFY_MEMO, true)
    val notifyCulture: Flow<Boolean> = pref(Keys.NOTIFY_CULTURE, false)
    val notifyFocus: Flow<Boolean> = pref(Keys.NOTIFY_FOCUS, true)
    val notifyGarden: Flow<Boolean> = pref(Keys.NOTIFY_GARDEN, false)
    val cultureOrientation: Flow<String> = pref(Keys.CULTURE_ORIENTATION, "mixed")
    val cultureStyle: Flow<String> = pref(Keys.CULTURE_STYLE, "mixed")

    /** Les trois réglages ci-dessus regroupés, tels que les consomme le planificateur. */
    val heuresSilencieuses: Flow<QuietHours> =
        context.dataStore.data.catch { erreur ->
            if (erreur is IOException) emit(emptyPreferences()) else throw erreur
        }.map { p ->
            QuietHours(
                enabled = p[Keys.QUIET_ENABLED] ?: true,
                startMinute = p[Keys.QUIET_START] ?: (21 * 60),
                endMinute = p[Keys.QUIET_END] ?: (9 * 60)
            )
        }

    private fun <T> pref(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.catch { erreur ->
            if (erreur is IOException) emit(emptyPreferences()) else throw erreur
        }.map { it[key] ?: default }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }

    suspend fun setLectureAuto(actif: Boolean) =
        context.dataStore.edit { it[Keys.LECTURE_AUTO] = actif }

    suspend fun setVitesseVoix(valeur: String) =
        context.dataStore.edit { it[Keys.VITESSE_VOIX] = valeur }

    suspend fun setRepetitionsVoix(nombre: Int) =
        context.dataStore.edit { it[Keys.REPETITIONS_VOIX] = nombre.coerceIn(0, 2) }

    suspend fun setCouleursSysteme(actif: Boolean) =
        context.dataStore.edit { it[Keys.COULEURS_SYSTEME] = actif }

    suspend fun setPalette(valeur: String) =
        context.dataStore.edit { it[Keys.PALETTE] = valeur }
    suspend fun setShowNavLabels(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_NAV_LABELS] = v }
    suspend fun setVibrations(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATIONS] = v }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setBatterySaver(v: Boolean) = context.dataStore.edit { it[Keys.BATTERY_SAVER] = v }
    suspend fun setGraphicsQuality(v: String) = context.dataStore.edit {
        it[Keys.GRAPHICS_QUALITY] = v.lowercase().takeIf { id -> id in setOf("low", "normal", "high") }
            ?: "normal"
    }
    suspend fun setStreakReminder(v: Boolean) = context.dataStore.edit { it[Keys.STREAK_REMINDER] = v }
    suspend fun setFocusKeepScreen(v: Boolean) = context.dataStore.edit { it[Keys.FOCUS_KEEP_SCREEN] = v }

    /**
     * Le tutoriel a-t-il déjà été vu ?
     *
     * La valeur par défaut est `false` : une installation neuve le montre, une
     * mise à jour ne le remontre pas puisque la préférence est déjà écrite.
     * C'est ce qui évite de réexpliquer l'application à chaque version.
     */
    val onboardingDone: Flow<Boolean> = pref(Keys.ONBOARDING_DONE, false)
    suspend fun setOnboardingDone(v: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = v }

    suspend fun setQuietEnabled(v: Boolean) = context.dataStore.edit { it[Keys.QUIET_ENABLED] = v }
    suspend fun setQuietStart(minutes: Int) =
        context.dataStore.edit { it[Keys.QUIET_START] = minutes.coerceIn(0, 24 * 60 - 1) }
    suspend fun setQuietEnd(minutes: Int) =
        context.dataStore.edit { it[Keys.QUIET_END] = minutes.coerceIn(0, 24 * 60 - 1) }

    suspend fun setMinimalMode(enabled: Boolean) =
        context.dataStore.edit { it[Keys.MINIMAL_MODE] = enabled }

    suspend fun setDailyMinutes(minutes: Int) = context.dataStore.edit {
        it[Keys.DAILY_MINUTES] = minutes.takeIf { value -> value in setOf(2, 5, 10, 15) } ?: 0
    }

    suspend fun setTodayCompletedDate(date: String) =
        context.dataStore.edit { it[Keys.TODAY_COMPLETED_DATE] = date }

    suspend fun setNotificationDailyMax(maximum: Int) =
        context.dataStore.edit { it[Keys.NOTIFICATION_DAILY_MAX] = maximum.coerceIn(1, 3) }

    suspend fun setNotificationPauseUntilEpochDay(epochDay: Long) =
        context.dataStore.edit { it[Keys.NOTIFICATION_PAUSE_UNTIL] = epochDay.coerceAtLeast(0L) }

    suspend fun setWeekendQuiet(enabled: Boolean) =
        context.dataStore.edit { it[Keys.WEEKEND_QUIET] = enabled }

    suspend fun setNotifyLearning(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_LEARNING] = enabled }
    suspend fun setNotifyMemo(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_MEMO] = enabled }
    suspend fun setNotifyCulture(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_CULTURE] = enabled }
    suspend fun setNotifyFocus(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_FOCUS] = enabled }
    suspend fun setNotifyGarden(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_GARDEN] = enabled }

    suspend fun setCultureOrientation(value: String) =
        context.dataStore.edit { it[Keys.CULTURE_ORIENTATION] = value }
    suspend fun setCultureStyle(value: String) =
        context.dataStore.edit { it[Keys.CULTURE_STYLE] = value }
}
