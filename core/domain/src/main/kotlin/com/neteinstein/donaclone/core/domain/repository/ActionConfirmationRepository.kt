package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ActionConfirmationRepository {
    fun observeActionConfirmationEnabled(): Flow<Boolean>

    suspend fun setActionConfirmationEnabled(enabled: Boolean)
}
