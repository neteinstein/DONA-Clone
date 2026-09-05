package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface DebugRepository {
    fun observeDebugModeEnabled(): Flow<Boolean>

    suspend fun setDebugModeEnabled(enabled: Boolean)
}
