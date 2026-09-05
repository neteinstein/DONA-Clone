package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.DebugRepository
import kotlinx.coroutines.flow.Flow

class ObserveDebugModeUseCase(
    private val repository: DebugRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeDebugModeEnabled()
}

class SetDebugModeUseCase(
    private val repository: DebugRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDebugModeEnabled(enabled)
}
