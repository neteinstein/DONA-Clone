package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow

/**
 * Ids of the shutters whose open/close direction and position are wired backwards on this house's
 * hub, set from the "Shutter Fixer" settings screen. Stored as a comma-joined id list, the same
 * shape [RoomsDisplayPreferences.roomOrder] uses.
 *
 * Unlike every other preference in this module these are keyed **per house**: device ids are only
 * unique within one hub, so a single global set would silently invert an unrelated device on a
 * second house.
 */
class ShutterInversionPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    fun invertedShutterIds(houseName: String): Flow<Set<Int>> =
        dataStore.mapDistinct { prefs ->
            prefs[keyFor(houseName)]
                ?.split(SEPARATOR)
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                .orEmpty()
        }

    suspend fun setInverted(
        houseName: String,
        deviceId: Int,
        inverted: Boolean,
    ) {
        dataStore.edit { prefs ->
            val key = keyFor(houseName)
            val current =
                prefs[key]
                    ?.split(SEPARATOR)
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toSet()
                    .orEmpty()
            val updated = if (inverted) current + deviceId else current - deviceId
            if (updated.isEmpty()) prefs.remove(key) else prefs[key] = updated.sorted().joinToString(SEPARATOR)
        }
    }

    private fun keyFor(houseName: String) = stringPreferencesKey("$INVERTED_SHUTTERS_KEY_PREFIX$houseName")

    private companion object {
        const val INVERTED_SHUTTERS_KEY_PREFIX = "inverted_shutters_"
        const val SEPARATOR = ","
    }
}
