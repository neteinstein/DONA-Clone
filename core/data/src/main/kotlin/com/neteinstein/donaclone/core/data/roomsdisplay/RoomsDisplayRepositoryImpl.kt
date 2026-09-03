package com.neteinstein.donaclone.core.data.roomsdisplay

import com.neteinstein.donaclone.core.database.prefs.RoomsDisplayPreferences
import com.neteinstein.donaclone.core.domain.repository.RoomsDisplayRepository
import kotlinx.coroutines.flow.Flow

class RoomsDisplayRepositoryImpl(
    private val roomsDisplayPreferences: RoomsDisplayPreferences,
) : RoomsDisplayRepository {
    override fun observeRoomsExpandedByDefault(): Flow<Boolean> = roomsDisplayPreferences.roomsExpandedByDefault

    override suspend fun setRoomsExpandedByDefault(expanded: Boolean) =
        roomsDisplayPreferences.setRoomsExpandedByDefault(expanded)
}
