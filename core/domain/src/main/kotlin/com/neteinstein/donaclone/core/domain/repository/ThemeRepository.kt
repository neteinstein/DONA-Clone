package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
