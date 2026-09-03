package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.BiometricRepository
import kotlinx.coroutines.flow.Flow

class ObserveBiometricEnabledUseCase(
    private val repository: BiometricRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeBiometricEnabled()
}

class SetBiometricEnabledUseCase(
    private val repository: BiometricRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setBiometricEnabled(enabled)
}
