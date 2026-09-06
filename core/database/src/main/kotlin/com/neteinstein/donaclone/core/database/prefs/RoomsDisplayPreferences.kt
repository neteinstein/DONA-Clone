package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether each tab's room sections should start expanded or collapsed the next time the app is
 * opened (a separate default per [RoomsDisplayTab], set by each tab's own "expand/collapse all"
 * control), and the user's custom drag-to-reorder order for room/category sections (shared by the
 * Home and Sensors tabs). Both global, not per-house. */
class RoomsDisplayPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    fun roomsExpandedByDefault(tab: RoomsDisplayTab): Flow<Boolean> =
        dataStore.data.map { it[expandedKeyFor(tab)] ?: true }

    suspend fun setRoomsExpandedByDefault(
        tab: RoomsDisplayTab,
        expanded: Boolean,
    ) {
        dataStore.edit { prefs -> prefs[expandedKeyFor(tab)] = expanded }
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

    private fun expandedKeyFor(tab: RoomsDisplayTab) =
        when (tab) {
            RoomsDisplayTab.HOME -> HOME_ROOMS_EXPANDED_KEY
            RoomsDisplayTab.SENSORS -> SENSORS_ROOMS_EXPANDED_KEY
        }

    private companion object {
        val HOME_ROOMS_EXPANDED_KEY = booleanPreferencesKey("home_rooms_expanded_by_default")
        val SENSORS_ROOMS_EXPANDED_KEY = booleanPreferencesKey("sensors_rooms_expanded_by_default")
        val ROOM_ORDER_KEY = stringPreferencesKey("room_order")
        const val ROOM_ORDER_SEPARATOR = ","
    }
}
