package com.neteinstein.donaclone.core.database.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Maps this DataStore's preferences to [T], filtering out duplicate emissions caused by
 * unrelated keys being written in the same (possibly shared) DataStore file — otherwise every
 * consumer re-fires on every write to any key, not just the one it cares about. */
internal fun <T> DataStore<Preferences>.mapDistinct(transform: (Preferences) -> T): Flow<T> =
    data.map(transform).distinctUntilChanged()
