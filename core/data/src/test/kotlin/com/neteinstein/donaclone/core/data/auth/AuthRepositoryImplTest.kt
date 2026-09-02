package com.neteinstein.donaclone.core.data.auth

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.SessionStatus
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.UserDto
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {
    private val socket = mockk<DomotalkSocket>(relaxUnitFun = true)
    private val api = mockk<DomotalkApi>()
    private val repository = AuthRepositoryImpl(socket, api)

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
            coEvery { socket.connect(any(), any(), any()) } just Runs
            coEvery { api.readUsers() } returns listOf(UserDto(id = 7, name = "bob", role = 1))

            val result = repository.login(house)

            assertTrue(result is DonaResult.Error)
            assertEquals(SessionStatus.DISCONNECTED, repository.sessionState.value)
            coVerify(exactly = 0) { api.createSession(any(), any()) }
        }
}
