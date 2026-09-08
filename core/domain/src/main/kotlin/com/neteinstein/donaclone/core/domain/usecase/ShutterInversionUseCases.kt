package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.ShutterInversionRepository
import kotlinx.coroutines.flow.Flow

class ObserveInvertedShuttersUseCase(
    private val repository: ShutterInversionRepository,
) {
    operator fun invoke(): Flow<Set<Int>> = repository.observeInvertedShutterIds()
}

class SetShutterInvertedUseCase(
    private val repository: ShutterInversionRepository,
) {
    suspend operator fun invoke(
        deviceId: Int,
        inverted: Boolean,
    ) = repository.setShutterInverted(deviceId, inverted)
}
