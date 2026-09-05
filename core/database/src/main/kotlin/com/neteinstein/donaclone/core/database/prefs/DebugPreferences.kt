package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether verbose logging ("Debug Mode" in Settings) is turned on. Off by default; when on, it
 * enables logging even on a release build, which normally logs nothing. */
class DebugPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val debugModeEnabled: Flow<Boolean> = dataStore.data.map { it[DEBUG_MODE_KEY] ?: false }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DEBUG_MODE_KEY] = enabled }
    }

    private companion object {
        val DEBUG_MODE_KEY = booleanPreferencesKey("debug_mode")
    }
}
