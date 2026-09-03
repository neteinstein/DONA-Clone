package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import kotlinx.coroutines.flow.StateFlow

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val houseRepository: HouseRepository,
) {
    suspend operator fun invoke(house: House): DonaResult<AuthSession> {
        val result = authRepository.login(house)
        if (result is DonaResult.Success) {
            houseRepository.saveHouse(house)
            houseRepository.setActiveHouseName(house.name)
        }
        return result
    }
}

class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val houseRepository: HouseRepository,
) {
    suspend operator fun invoke() {
        authRepository.logout()
        houseRepository.setActiveHouseName(null)
    }
}

class ObserveSessionStateUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke(): StateFlow<SessionStatus> = repository.sessionState
}

class GetCurrentSessionUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke(): AuthSession? = repository.currentSession
}

class RetryConnectionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): DonaResult<Unit> = repository.retryConnection()
}

class ObserveDpuUnreachableUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke(): StateFlow<Boolean> = repository.dpuUnreachable
}
