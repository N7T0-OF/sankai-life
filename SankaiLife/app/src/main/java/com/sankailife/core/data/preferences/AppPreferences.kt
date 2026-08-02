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

        // Heures silencieuses, stockées en minutes depuis minuit : un seul
        // entier évite les incohérences entre un champ heure et un champ minute.
        val QUIET_ENABLED    = booleanPreferencesKey("quiet_enabled")
        val QUIET_START      = intPreferencesKey("quiet_start_minutes")
        val QUIET_END        = intPreferencesKey("quiet_end_minutes")
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
    val streakReminder: Flow<Boolean> = pref(Keys.STREAK_REMINDER, true)
    val focusKeepScreen: Flow<Boolean> = pref(Keys.FOCUS_KEEP_SCREEN, true)

    val quietEnabled: Flow<Boolean> = pref(Keys.QUIET_ENABLED, false)
    val quietStartMinutes: Flow<Int> = pref(Keys.QUIET_START, 23 * 60)
    val quietEndMinutes: Flow<Int> = pref(Keys.QUIET_END, 8 * 60)

    /** Les trois réglages ci-dessus regroupés, tels que les consomme le planificateur. */
    val heuresSilencieuses: Flow<QuietHours> =
        context.dataStore.data.catch { erreur ->
            if (erreur is IOException) emit(emptyPreferences()) else throw erreur
        }.map { p ->
            QuietHours(
                enabled = p[Keys.QUIET_ENABLED] ?: false,
                startMinute = p[Keys.QUIET_START] ?: (23 * 60),
                endMinute = p[Keys.QUIET_END] ?: (8 * 60)
            )
        }

    private fun <T> pref(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.catch { erreur ->
            if (erreur is IOException) emit(emptyPreferences()) else throw erreur
        }.map { it[key] ?: default }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }

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
}
