package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import kotlinx.coroutines.flow.Flow

interface RoomsDisplayRepository {
    fun observeRoomsExpandedByDefault(tab: RoomsDisplayTab): Flow<Boolean>

    suspend fun setRoomsExpandedByDefault(
        tab: RoomsDisplayTab,
        expanded: Boolean,
    )

    fun observeRoomOrder(): Flow<List<Int>>

    suspend fun setRoomOrder(order: List<Int>)
}
