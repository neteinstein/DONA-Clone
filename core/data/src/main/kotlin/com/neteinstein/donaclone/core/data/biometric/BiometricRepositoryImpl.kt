package com.neteinstein.donaclone.core.data.biometric

import com.neteinstein.donaclone.core.database.prefs.BiometricPreferences
import com.neteinstein.donaclone.core.domain.repository.BiometricRepository
import kotlinx.coroutines.flow.Flow

class BiometricRepositoryImpl(
    private val biometricPreferences: BiometricPreferences,
) : BiometricRepository {
    override fun observeBiometricEnabled(): Flow<Boolean> = biometricPreferences.biometricEnabled

    override suspend fun setBiometricEnabled(enabled: Boolean) = biometricPreferences.setBiometricEnabled(enabled)
}
