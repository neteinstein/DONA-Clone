package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionStatus>
    val currentSession: AuthSession?

    /** True from the moment a login/reconnect attempt fails because the DPU simply can't be
     * reached (as opposed to a rejected/invalid session) until the next attempt succeeds — drives
     * the app's "can't reach the server" connectivity banner. */
    val dpuUnreachable: StateFlow<Boolean>

    /**
     * Connects the WebSocket to [house] (DNS first, falling back to [House.localIp] on
     * failure, mirroring the original app — see protocol notes §2.3) and logs in.
     */
    suspend fun login(house: House): DonaResult<AuthSession>

    suspend fun logout()

    /** One manual, uncounted reconnect attempt for whatever house is currently active — used by
     * the connectivity banner's "Retry" action. Does not affect the automatic session-recovery
     * retry budget (see the `AuthRepositoryImpl` implementation). */
    suspend fun retryConnection(): DonaResult<Unit>

    /** Tells the automatic session-recovery loop whether the app is currently in the foreground.
     * An unsolicited disconnect while backgrounded (the OS tearing down the socket, not a real
     * auth failure) must not spend its retry budget — and must not give up and log the user out —
     * while nobody is there to see it; see `AuthRepositoryImpl.handleUnsolicitedDisconnect`. */
    fun setAppForeground(foreground: Boolean)
}
