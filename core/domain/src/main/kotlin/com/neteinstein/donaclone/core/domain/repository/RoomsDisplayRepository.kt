package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface RoomsDisplayRepository {
    fun observeRoomsExpandedByDefault(): Flow<Boolean>

    suspend fun setRoomsExpandedByDefault(expanded: Boolean)

    fun observeRoomOrder(): Flow<List<Int>>

    suspend fun setRoomOrder(order: List<Int>)
}
