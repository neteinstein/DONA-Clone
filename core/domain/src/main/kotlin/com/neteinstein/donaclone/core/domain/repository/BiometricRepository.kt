package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface BiometricRepository {
    fun observeBiometricEnabled(): Flow<Boolean>

    suspend fun setBiometricEnabled(enabled: Boolean)
}
