package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUseCaseTest {

    private val authRepository = mockk<AuthRepository>()
    private val houseRepository = mockk<HouseRepository>(relaxUnitFun = true)
    private val useCase = LoginUseCase(authRepository, houseRepository)

    private val house = House(name = "Home", localIp = "192.168.1.50", username = "alice", password = "secret")

    @Test
    fun `successful login persists the house and marks it active`() = runTest {
        val session = AuthSession(token = "abc", userId = 1, userName = "alice", houseName = "Home")
        coEvery { authRepository.login(house) } returns DonaResult.Success(session)

        val result = useCase(house)

        assertTrue(result is DonaResult.Success)
        coVerify { houseRepository.saveHouse(house) }
        coVerify { houseRepository.setActiveHouseName("Home") }
    }

    @Test
    fun `failed login does not persist the house`() = runTest {
        coEvery { authRepository.login(house) } returns DonaResult.Error(DonaFailure.InvalidCredentials())

        val result = useCase(house)

        assertTrue(result is DonaResult.Error)
        coVerify(exactly = 0) { houseRepository.saveHouse(any()) }
    }
}
