package com.neteinstein.donaclone.feature.login

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.LoginUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val house = House(name = "Home", localIp = "192.168.1.50", username = "alice", password = "secret")

    private val observeHouses = mockk<ObserveHousesUseCase>()
    private val getActiveHouse = mockk<GetActiveHouseUseCase>()
    private val login = mockk<LoginUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LoginViewModel {
        coEvery { observeHouses() } returns flowOf(listOf(house))
        coEvery { getActiveHouse() } returns null
        return LoginViewModel(observeHouses, getActiveHouse, login)
    }

    @Test
    fun `houses are loaded and the first one is preselected`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(house, state.selectedHouse)
            assertEquals("alice", state.username)
        }
    }

    @Test
    fun `successful login updates state and clears the error`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val session = AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
        coEvery { login.invoke(any()) } returns DonaResult.Success(session)

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.login()
            val loading = awaitItem()
            assertEquals(true, loading.isLoading)
            val done = awaitItem()
            assertEquals(false, done.isLoading)
            assertEquals(true, done.loginSucceeded)
            assertNull(done.errorMessage)
        }
    }

    @Test
    fun `failed login surfaces an error message`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        coEvery { login.invoke(any()) } returns DonaResult.Error(DonaFailure.InvalidCredentials("Wrong password"))

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.login()
            skipItems(1)
            val failed = awaitItem()
            assertEquals(false, failed.isLoading)
            assertEquals(false, failed.loginSucceeded)
            assertEquals("Wrong password", failed.errorMessage)
        }
    }
}
