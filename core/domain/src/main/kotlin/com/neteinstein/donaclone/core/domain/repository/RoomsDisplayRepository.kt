package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface RoomsDisplayRepository {
    fun observeRoomsExpandedByDefault(): Flow<Boolean>

    suspend fun setRoomsExpandedByDefault(expanded: Boolean)
}
