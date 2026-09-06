package com.neteinstein.donaclone.core.data.roomsdisplay

import com.neteinstein.donaclone.core.database.prefs.RoomsDisplayPreferences
import com.neteinstein.donaclone.core.domain.repository.RoomsDisplayRepository
import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import kotlinx.coroutines.flow.Flow

class RoomsDisplayRepositoryImpl(
    private val roomsDisplayPreferences: RoomsDisplayPreferences,
) : RoomsDisplayRepository {
    override fun observeRoomsExpandedByDefault(tab: RoomsDisplayTab): Flow<Boolean> =
        roomsDisplayPreferences.roomsExpandedByDefault(tab)

    override suspend fun setRoomsExpandedByDefault(
        tab: RoomsDisplayTab,
        expanded: Boolean,
    ) = roomsDisplayPreferences.setRoomsExpandedByDefault(tab, expanded)

    override fun observeRoomOrder(): Flow<List<Int>> = roomsDisplayPreferences.roomOrder

    override suspend fun setRoomOrder(order: List<Int>) = roomsDisplayPreferences.setRoomOrder(order)
}
