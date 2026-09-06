package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.ActionConfirmationRepository
import kotlinx.coroutines.flow.Flow

class ObserveActionConfirmationEnabledUseCase(
    private val repository: ActionConfirmationRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeActionConfirmationEnabled()
}

class SetActionConfirmationEnabledUseCase(
    private val repository: ActionConfirmationRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setActionConfirmationEnabled(enabled)
}
