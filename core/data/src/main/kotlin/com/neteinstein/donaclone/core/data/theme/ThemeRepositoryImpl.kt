package com.neteinstein.donaclone.core.data.theme

import com.neteinstein.donaclone.core.database.prefs.ThemePreferences
import com.neteinstein.donaclone.core.domain.repository.ThemeRepository
import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class ThemeRepositoryImpl(
    private val themePreferences: ThemePreferences,
) : ThemeRepository {
    override fun observeThemeMode(): Flow<ThemeMode> = themePreferences.themeMode

    override suspend fun setThemeMode(mode: ThemeMode) = themePreferences.setThemeMode(mode)
}
