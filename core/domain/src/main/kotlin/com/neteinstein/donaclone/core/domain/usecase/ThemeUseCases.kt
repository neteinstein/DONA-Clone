package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.ThemeRepository
import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class ObserveThemeModeUseCase(
    private val repository: ThemeRepository,
) {
    operator fun invoke(): Flow<ThemeMode> = repository.observeThemeMode()
}

class SetThemeModeUseCase(
    private val repository: ThemeRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setThemeMode(mode)
}
