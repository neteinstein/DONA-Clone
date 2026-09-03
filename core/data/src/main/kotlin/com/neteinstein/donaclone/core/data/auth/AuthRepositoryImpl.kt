package com.neteinstein.donaclone.core.data.auth

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.socket.ConnectionState
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mirrors `LoginActivity.C0()`'s connection strategy (protocol notes §2.3): try the house's
 * `dns` address first (if set), and only on failure fall back to `localIp` — each with its own
 * `secure*` flag. Login itself is `read user` (resolve username -> numeric id client-side) then
 * `create session` with `MD5(password)`.
 *
 * Also owns silent session recovery: an unsolicited socket drop (not one caused by our own
 * [logout]) is retried up to [MAX_AUTO_RETRY_ATTEMPTS] times against the last active house before
 * giving up and logging out — see [handleUnsolicitedDisconnect]. A failure that means the DPU
 * simply can't be reached at all ([DonaFailure.Unreachable]) is deliberately *not* retried here
 * and does *not* log the user out — that case is the connectivity banner's job instead
 * (`ConnectivityRepository`/`MainActivityViewModel`), not this recovery loop's.
 */
class AuthRepositoryImpl(
    private val socket: DomotalkSocket,
    private val api: DomotalkApi,
    private val houseRepository: HouseRepository,
    applicationScope: CoroutineScope,
) : AuthRepository {
    private val _sessionState = MutableStateFlow(SessionStatus.DISCONNECTED)
    override val sessionState: StateFlow<SessionStatus> = _sessionState.asStateFlow()

    private val _dpuUnreachable = MutableStateFlow(false)
    override val dpuUnreachable: StateFlow<Boolean> = _dpuUnreachable.asStateFlow()

    @Volatile override var currentSession: AuthSession? = null
        private set

    @Volatile private var loggingOut = false

    @Volatile private var recovering = false

    init {
        applicationScope.launch {
            socket.connectionState.collect { state ->
                if (state == ConnectionState.DISCONNECTED && currentSession != null && !loggingOut && !recovering) {
                    handleUnsolicitedDisconnect()
                }
            }
        }
    }

    override suspend fun login(house: House): DonaResult<AuthSession> {
        _sessionState.value = SessionStatus.CONNECTING

        val attempts = connectionAttempts(house)
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
                    _dpuUnreachable.value = false
                    return result
                }
                is DonaResult.Error -> {
                    lastFailure = result.failure
                    Timber.w("Login attempt against %s failed: %s", host, result.failure)
                }
            }
        }

        _sessionState.value = SessionStatus.DISCONNECTED
        _dpuUnreachable.value = lastFailure is DonaFailure.Unreachable
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
        loggingOut = true
        currentSession = null
        _sessionState.value = SessionStatus.DISCONNECTED
        runCatching { api.logout() }
        socket.disconnect()
        loggingOut = false
    }

    /** One manual, uncounted recovery attempt for the last active house — used by the
     * connectivity banner's "Retry" action. Never counts against or resets the automatic
     * [handleUnsolicitedDisconnect] retry budget. */
    override suspend fun retryConnection(): DonaResult<Unit> {
        val house = lastActiveHouse() ?: return DonaResult.Error(DonaFailure.NotAuthenticated("No active house to reconnect to"))
        return when (val result = attemptRecovery(house)) {
            is DonaResult.Success -> DonaResult.Success(Unit)
            is DonaResult.Error -> result
        }
    }

    private suspend fun handleUnsolicitedDisconnect() {
        recovering = true
        try {
            val house = lastActiveHouse() ?: return giveUp()

            for (attempt in 1..MAX_AUTO_RETRY_ATTEMPTS) {
                delay(RETRY_BACKOFF_MILLIS.getOrElse(attempt - 1) { RETRY_BACKOFF_MILLIS.last() })
                when (val result = attemptRecovery(house)) {
                    is DonaResult.Success -> return
                    is DonaResult.Error -> {
                        if (result.failure is DonaFailure.Unreachable) {
                            // Not an auth problem — the connectivity banner owns this case; don't
                            // burn retries on it and don't log the user out over it.
                            Timber.w("Session recovery: DPU unreachable, deferring to the connectivity banner")
                            return
                        }
                        Timber.w("Session recovery attempt %d/%d failed: %s", attempt, MAX_AUTO_RETRY_ATTEMPTS, result.failure)
                    }
                }
            }
            giveUp()
        } finally {
            recovering = false
        }
    }

    /** Tries [DomotalkApi.resumeSession] first (cheap, no password re-hash), falling back to a
     * full [login] whenever resume doesn't cleanly succeed for any reason — [login] independently
     * (and correctly) re-classifies the failure as [DonaFailure.Unreachable] vs. an auth failure
     * on its own attempt, so this function doesn't need to duplicate that classification. */
    private suspend fun attemptRecovery(house: House): DonaResult<AuthSession> {
        val existingSession = currentSession
        val savedToken = existingSession?.token
        if (savedToken != null && tryResumeSession(house, savedToken)) {
            currentSession = existingSession
            _sessionState.value = SessionStatus.CONNECTED
            _dpuUnreachable.value = false
            return DonaResult.Success(existingSession)
        }
        return login(house)
    }

    private suspend fun tryResumeSession(
        house: House,
        token: String,
    ): Boolean {
        for ((host, secure) in connectionAttempts(house)) {
            try {
                socket.connect(host, secure)
                api.resumeSession(token)
                return true
            } catch (e: DomotalkException) {
                socket.disconnect()
            }
        }
        return false
    }

    private suspend fun giveUp() {
        loggingOut = true
        currentSession = null
        _sessionState.value = SessionStatus.DISCONNECTED
        socket.disconnect()
        houseRepository.setActiveHouseName(null)
        loggingOut = false
    }

    private suspend fun lastActiveHouse(): House? =
        houseRepository.activeHouseName.first()?.let { houseRepository.getHouse(it) }

    private fun connectionAttempts(house: House): List<Pair<String, Boolean>> {
        val dns = house.dns
        val localIp = house.localIp
        return buildList {
            if (!dns.isNullOrBlank()) add(dns to house.secureDns)
            if (!localIp.isNullOrBlank()) add(localIp to house.secureLocalIp)
        }
    }

    private companion object {
        const val MAX_AUTO_RETRY_ATTEMPTS = 3
        val RETRY_BACKOFF_MILLIS = listOf(2_000L, 5_000L, 10_000L)
    }
}
