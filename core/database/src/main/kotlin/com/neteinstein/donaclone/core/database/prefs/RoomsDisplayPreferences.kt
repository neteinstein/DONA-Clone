package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the Home tab's room sections should start expanded or collapsed the next time the app
 * is opened — set by the Home tab's "Expand all"/"Collapse all" control. Global, not per-house. */
class RoomsDisplayPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val roomsExpandedByDefault: Flow<Boolean> = dataStore.data.map { it[ROOMS_EXPANDED_KEY] ?: true }

    suspend fun setRoomsExpandedByDefault(expanded: Boolean) {
        dataStore.edit { prefs -> prefs[ROOMS_EXPANDED_KEY] = expanded }
    }

    private companion object {
        val ROOMS_EXPANDED_KEY = booleanPreferencesKey("rooms_expanded_by_default")
    }
}
