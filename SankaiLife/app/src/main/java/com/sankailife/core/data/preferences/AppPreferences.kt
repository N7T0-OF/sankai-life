package com.sankailife.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sankai_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE       = stringPreferencesKey("theme_mode")
        val SHOW_NAV_LABELS  = booleanPreferencesKey("show_nav_labels")
        val VIBRATIONS       = booleanPreferencesKey("vibrations")
        val NOTIFICATIONS    = booleanPreferencesKey("notifications")
        val BATTERY_SAVER    = booleanPreferencesKey("battery_saver")
        val STREAK_REMINDER  = booleanPreferencesKey("streak_reminder")
        val FOCUS_KEEP_SCREEN= booleanPreferencesKey("focus_keep_screen")
        val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
    }

    val themeMode: Flow<String>       = pref(Keys.THEME_MODE, "dark")
    val showNavLabels: Flow<Boolean>  = pref(Keys.SHOW_NAV_LABELS, true)
    val vibrations: Flow<Boolean>     = pref(Keys.VIBRATIONS, true)
    val notifications: Flow<Boolean>  = pref(Keys.NOTIFICATIONS, true)
    val batterySaver: Flow<Boolean>   = pref(Keys.BATTERY_SAVER, false)
    val streakReminder: Flow<Boolean> = pref(Keys.STREAK_REMINDER, true)
    val focusKeepScreen: Flow<Boolean> = pref(Keys.FOCUS_KEEP_SCREEN, true)

    private fun <T> pref(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.catch { emit(emptyPreferences()) }.map { it[key] ?: default }

    suspend fun set(key: Preferences.Key<String>, value: String) =
        context.dataStore.edit { it[key] = value }
    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) =
        context.dataStore.edit { it[key] = value }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setShowNavLabels(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_NAV_LABELS] = v }
    suspend fun setVibrations(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATIONS] = v }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setBatterySaver(v: Boolean) = context.dataStore.edit { it[Keys.BATTERY_SAVER] = v }
    suspend fun setStreakReminder(v: Boolean) = context.dataStore.edit { it[Keys.STREAK_REMINDER] = v }
    suspend fun setFocusKeepScreen(v: Boolean) = context.dataStore.edit { it[Keys.FOCUS_KEEP_SCREEN] = v }
}
