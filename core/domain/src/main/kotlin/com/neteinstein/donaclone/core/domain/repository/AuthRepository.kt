package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionStatus>
    val currentSession: AuthSession?

    /**
     * Connects the WebSocket to [house] (DNS first, falling back to [House.localIp] on
     * failure, mirroring the original app — see protocol notes §2.3) and logs in.
     */
    suspend fun login(house: House): DonaResult<AuthSession>

    suspend fun logout()
}
