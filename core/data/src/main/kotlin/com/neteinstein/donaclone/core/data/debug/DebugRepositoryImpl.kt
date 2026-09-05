package com.neteinstein.donaclone.core.data.debug

import com.neteinstein.donaclone.core.database.prefs.DebugPreferences
import com.neteinstein.donaclone.core.domain.repository.DebugRepository
import kotlinx.coroutines.flow.Flow

class DebugRepositoryImpl(
    private val debugPreferences: DebugPreferences,
) : DebugRepository {
    override fun observeDebugModeEnabled(): Flow<Boolean> = debugPreferences.debugModeEnabled

    override suspend fun setDebugModeEnabled(enabled: Boolean) = debugPreferences.setDebugModeEnabled(enabled)
}
