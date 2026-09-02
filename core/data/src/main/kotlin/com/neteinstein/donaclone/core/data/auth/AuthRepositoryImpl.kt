package com.neteinstein.donaclone.core.data.auth

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Mirrors `LoginActivity.C0()`'s connection strategy (protocol notes §2.3): try the house's
 * `dns` address first (if set), and only on failure fall back to `localIp` — each with its own
 * `secure*` flag. Login itself is `read user` (resolve username -> numeric id client-side) then
 * `create session` with `MD5(password)`.
 */
class AuthRepositoryImpl(
    private val socket: DomotalkSocket,
    private val api: DomotalkApi,
) : AuthRepository {
    private val _sessionState = MutableStateFlow(SessionStatus.DISCONNECTED)
    override val sessionState: StateFlow<SessionStatus> = _sessionState.asStateFlow()

    @Volatile override var currentSession: AuthSession? = null
        private set

    override suspend fun login(house: House): DonaResult<AuthSession> {
        _sessionState.value = SessionStatus.CONNECTING

        val dns = house.dns
        val localIp = house.localIp
        val attempts =
            buildList {
                if (!dns.isNullOrBlank()) add(dns to house.secureDns)
                if (!localIp.isNullOrBlank()) add(localIp to house.secureLocalIp)
            }

        if (attempts.isEmpty()) {
            _sessionState.value = SessionStatus.DISCONNECTED
            return DonaResult.Error(DonaFailure.Unreachable("No DNS or local IP configured for this house"))
        }

        var lastFailure: DonaFailure = DonaFailure.Unreachable("Unknown error")
        for ((host, secure) in attempts) {
            when (val result = attemptLogin(host, secure, house)) {
                is DonaResult.Success -> {
                    currentSession = result.data
                    _sessionState.value = SessionStatus.CONNECTED
                    return result
                }
                is DonaResult.Error -> {
                    lastFailure = result.failure
                    Timber.w("Login attempt against %s failed: %s", host, result.failure)
                }
            }
        }

        _sessionState.value = SessionStatus.DISCONNECTED
        return DonaResult.Error(lastFailure)
    }

    private suspend fun attemptLogin(
        host: String,
        secure: Boolean,
        house: House,
    ): DonaResult<AuthSession> {
        try {
            socket.connect(host, secure)
        } catch (e: DomotalkException) {
            return DonaResult.Error(DonaFailure.Unreachable(e.message, e))
        }

        return try {
            val users = api.readUsers()
            val user = users.firstOrNull { it.name == house.username }

            if (user == null || user.role == 0) {
                socket.disconnect()
                return DonaResult.Error(DonaFailure.InvalidCredentials("Could not find a user named '${house.username}'"))
            }

            val token = api.createSession(user.id, PasswordHasher.md5Hex(house.password))
            DonaResult.Success(
                AuthSession(token = token, userId = user.id, userName = user.name, houseName = house.name),
            )
        } catch (e: DomotalkException) {
            socket.disconnect()
            DonaResult.Error(DonaFailure.InvalidCredentials(e.message, e))
        }
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        socket.disconnect()
        currentSession = null
        _sessionState.value = SessionStatus.DISCONNECTED
    }
}
