package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the app-level fingerprint lock (gating every cold start / resume-from-background) is
 * turned on. Global, not per-house — it always gates whichever house is currently active. */
class BiometricPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[BIOMETRIC_ENABLED_KEY] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BIOMETRIC_ENABLED_KEY] = enabled }
    }

    private companion object {
        val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
    }
}
