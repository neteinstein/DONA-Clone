package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.ConnectivityRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveConnectivityUseCase(
    private val repository: ConnectivityRepository,
) {
    operator fun invoke(): StateFlow<Boolean> = repository.isOnline
}
