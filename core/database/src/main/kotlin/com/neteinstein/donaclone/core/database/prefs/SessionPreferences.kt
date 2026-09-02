package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers which [com.neteinstein.donaclone.core.database.house.HouseEntity] was last used so
 * the app can offer to reconnect to it on launch. The session token itself is never persisted
 * here (the original app doesn't persist it either, see protocol notes §2.3) — only the house
 * name, credentials for which live in Room and get re-sent through a fresh login each launch.
 */
class SessionPreferences(private val dataStore: DataStore<Preferences>) {

    val activeHouseName: Flow<String?> = dataStore.data.map { it[ACTIVE_HOUSE_NAME_KEY] }

    suspend fun setActiveHouseName(name: String?) {
        dataStore.edit { prefs ->
            if (name == null) prefs.remove(ACTIVE_HOUSE_NAME_KEY) else prefs[ACTIVE_HOUSE_NAME_KEY] = name
        }
    }

    private companion object {
        val ACTIVE_HOUSE_NAME_KEY = stringPreferencesKey("active_house_name")
    }
}
