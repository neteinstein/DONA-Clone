package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the Home tab's room sections should start expanded or collapsed the next time the app
 * is opened, and the user's custom drag-to-reorder order for room/category sections (shared by the
 * Home and Sensors tabs). Both set by the tabs' own controls. Global, not per-house. */
class RoomsDisplayPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val roomsExpandedByDefault: Flow<Boolean> = dataStore.data.map { it[ROOMS_EXPANDED_KEY] ?: true }

    suspend fun setRoomsExpandedByDefault(expanded: Boolean) {
        dataStore.edit { prefs -> prefs[ROOMS_EXPANDED_KEY] = expanded }
    }

    /** Room/category ids in the user's chosen order, most-preferred first. Empty until the user
     * drags a section for the first time — callers fall back to alphabetical order themselves. */
    val roomOrder: Flow<List<Int>> =
        dataStore.data.map { prefs ->
            prefs[ROOM_ORDER_KEY]
                ?.split(ROOM_ORDER_SEPARATOR)
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
        }

    suspend fun setRoomOrder(order: List<Int>) {
        dataStore.edit { prefs -> prefs[ROOM_ORDER_KEY] = order.joinToString(ROOM_ORDER_SEPARATOR) }
    }

    private companion object {
        val ROOMS_EXPANDED_KEY = booleanPreferencesKey("rooms_expanded_by_default")
        val ROOM_ORDER_KEY = stringPreferencesKey("room_order")
        const val ROOM_ORDER_SEPARATOR = ","
    }
}
