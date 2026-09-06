package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** The user's manual light/dark/system theme preference, persisted the same way as
 * [SessionPreferences] (same DataStore file, a different key). */
class ThemePreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> =
        dataStore.mapDistinct { prefs ->
            prefs[THEME_MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
