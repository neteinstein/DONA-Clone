package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow

/** Whether the app should ask for confirmation before an action that's hard to undo or could have
 * unintended physical consequences, e.g. closing a shutter. On by default. Global, not per-house. */
class ActionConfirmationPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val actionConfirmationEnabled: Flow<Boolean> = dataStore.mapDistinct { it[ACTION_CONFIRMATION_ENABLED_KEY] ?: true }

    suspend fun setActionConfirmationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ACTION_CONFIRMATION_ENABLED_KEY] = enabled }
    }

    private companion object {
        val ACTION_CONFIRMATION_ENABLED_KEY = booleanPreferencesKey("action_confirmation_enabled")
    }
}
