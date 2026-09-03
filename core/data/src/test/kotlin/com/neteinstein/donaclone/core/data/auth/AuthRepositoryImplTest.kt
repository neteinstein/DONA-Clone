package com.neteinstein.donaclone.core.data.auth

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.UserDto
import com.neteinstein.donaclone.core.network.socket.ConnectionState
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {
    private val socket = mockk<DomotalkSocket>(relaxUnitFun = true)
    private val api = mockk<DomotalkApi>()
    private val houseRepository = mockk<HouseRepository>(relaxed = true)
    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /** Shares the calling [TestScope]'s virtual-time scheduler so the retry loop's `delay()`
     * calls advance with [kotlinx.coroutines.test.TestScope.advanceUntilIdle] instead of a
     * disconnected scheduler that never ticks. */
    private fun TestScope.repository(): AuthRepositoryImpl {
        every { socket.connectionState } returns connectionState
        return AuthRepositoryImpl(
            socket = socket,
            api = api,
            houseRepository = houseRepository,
            applicationScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private val house =
        House(
            name = "Home",
            dns = "home.example.com",
            secureDns = true,
            localIp = "192.168.1.50",
            secureLocalIp = false,
            username = "alice",
            password = "secret",
        )

    @Test
    fun `successful login on the first attempt returns a session and does not try the local IP`() =
        runTest {
            val repository = repository()
            coEvery { socket.connect("home.example.com", true, true) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "alice", role = 1))
            coEvery { api.createSession(7, PasswordHasher.md5Hex("secret")) } returns "token-123"

            val result = repository.login(house)

            assertTrue(result is DonaResult.Success)
            result as DonaResult.Success
            assertEquals("token-123", result.data.token)
            assertEquals(7, result.data.userId)
            assertEquals(SessionStatus.CONNECTED, repository.sessionState.value)
            coVerify(exactly = 0) { socket.connect("192.168.1.50", false, false) }
        }

    @Test
    fun `falls back to the local IP when the DNS address is unreachable`() =
        runTest {
            val repository = repository()
            coEvery {
                socket.connect("home.example.com", true, true)
            } throws DomotalkException.ConnectFailed(RuntimeException("dns down"))
            coEvery { socket.connect("192.168.1.50", false, false) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "alice", role = 1))
            coEvery { api.createSession(7, PasswordHasher.md5Hex("secret")) } returns "token-456"

            val result = repository.login(house)

            assertTrue(result is DonaResult.Success)
            coVerify { socket.connect("192.168.1.50", false, false) }
        }

    @Test
    fun `unknown username fails without creating a session`() =
        runTest {
            val repository = repository()
            coEvery { socket.connect(any(), any(), any()) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "bob", role = 1))

            val result = repository.login(house)

            assertTrue(result is DonaResult.Error)
            assertEquals(SessionStatus.DISCONNECTED, repository.sessionState.value)
            coVerify(exactly = 0) { api.createSession(any(), any()) }
        }

    @Test
    fun `an unsolicited disconnect retries login against the active house and recovers`() =
        runTest {
            val repository = repository()
            coEvery { socket.connect("home.example.com", true, true) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "alice", role = 1))
            coEvery { api.createSession(7, PasswordHasher.md5Hex("secret")) } returns "token-1"
            val loginResult = repository.login(house)
            assertTrue(loginResult is DonaResult.Success)

            coEvery { houseRepository.activeHouseName } returns MutableStateFlow("Home")
            coEvery { houseRepository.getHouse("Home") } returns house
            coEvery { api.resumeSession(any()) } throws DomotalkException.RequestTimeout("action", "session")
            coEvery { api.createSession(7, PasswordHasher.md5Hex("secret")) } returns "token-2"

            connectionState.value = ConnectionState.CONNECTED
            connectionState.value = ConnectionState.DISCONNECTED
            advanceUntilIdle()

            assertEquals(SessionStatus.CONNECTED, repository.sessionState.value)
        }

    @Test
    fun `an unsolicited disconnect that stays unreachable does not log the user out`() =
        runTest {
            val repository = repository()
            coEvery { socket.connect("home.example.com", true, true) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "alice", role = 1))
            coEvery { api.createSession(7, PasswordHasher.md5Hex("secret")) } returns "token-1"
            repository.login(house)

            coEvery { houseRepository.activeHouseName } returns MutableStateFlow("Home")
            coEvery { houseRepository.getHouse("Home") } returns house
            coEvery { api.resumeSession(any()) } throws DomotalkException.ConnectionLost()
            coEvery {
                socket.connect("home.example.com", true, true)
            } throws DomotalkException.ConnectFailed(RuntimeException("down"))
            coEvery {
                socket.connect("192.168.1.50", false, false)
            } throws DomotalkException.ConnectFailed(RuntimeException("down"))

            connectionState.value = ConnectionState.CONNECTED
            connectionState.value = ConnectionState.DISCONNECTED
            advanceUntilIdle()

            assertTrue(repository.currentSession != null)
        }
}
