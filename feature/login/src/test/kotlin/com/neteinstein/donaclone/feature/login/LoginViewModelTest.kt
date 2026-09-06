package com.neteinstein.donaclone.feature.login

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.LoginUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.House
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val house = House(name = "Home", localIp = "192.168.1.50", username = "alice", password = "secret")

    /** Stands in for the stored profiles, so a test can emit an edit made on the Houses screen. */
    private val houses = MutableStateFlow(listOf(house))

    private val observeHouses = mockk<ObserveHousesUseCase>()
    private val getActiveHouse = mockk<GetActiveHouseUseCase>()
    private val login = mockk<LoginUseCase>()
    private val observeBiometricEnabled = mockk<ObserveBiometricEnabledUseCase>()
    private val setBiometricEnabled = mockk<SetBiometricEnabledUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(biometricEnabled: Boolean = false): LoginViewModel {
        coEvery { observeHouses() } returns houses
        coEvery { getActiveHouse() } returns null
        every { observeBiometricEnabled() } returns flowOf(biometricEnabled)
        return LoginViewModel(observeHouses, getActiveHouse, login, observeBiometricEnabled, setBiometricEnabled)
    }

    @Test
    fun `houses are loaded and the first one is preselected`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(house, state.selectedHouse)
                assertEquals("alice", state.username)
            }
        }

    @Test
    fun `successful login updates state and clears the error`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
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
    fun `failed login surfaces an error message`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
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

    @Test
    fun `successful login offers the biometric opt-in prompt when it isn't already on`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(biometricEnabled = false)
            dispatcher.scheduler.advanceUntilIdle()
            val session = AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
            coEvery { login.invoke(any()) } returns DonaResult.Success(session)

            viewModel.login()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showBiometricOptInPrompt)
        }

    @Test
    fun `successful login does not re-offer the prompt when biometric is already on`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(biometricEnabled = true)
            dispatcher.scheduler.advanceUntilIdle()
            val session = AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
            coEvery { login.invoke(any()) } returns DonaResult.Success(session)

            viewModel.login()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showBiometricOptInPrompt)
        }

    @Test
    fun `accepting the opt-in prompt enables biometric unlock`() =
        runTest(dispatcher) {
            coEvery { setBiometricEnabled(true) } just Runs
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onBiometricOptInResult(enable = true)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setBiometricEnabled(true) }
            assertFalse(viewModel.uiState.value.showBiometricOptInPrompt)
        }

    @Test
    fun `resuming after a failed login with credentials retries automatically`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { login.invoke(any()) } returns DonaResult.Error(DonaFailure.InvalidCredentials("Wrong password"))
            viewModel.login()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("Wrong password", viewModel.uiState.value.errorMessage)

            val session = AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
            coEvery { login.invoke(any()) } returns DonaResult.Success(session)
            viewModel.retryLoginIfNeeded()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 2) { login.invoke(any()) }
            assertTrue(viewModel.uiState.value.loginSucceeded)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `resuming without a prior failure does not retry`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.retryLoginIfNeeded()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { login.invoke(any()) }
        }

    @Test
    fun `resuming after a failure without credentials does not retry`() =
        runTest(dispatcher) {
            // A profile saved without credentials: the login fields are read-only, so blank values
            // can only come from the house itself.
            houses.value = listOf(house.copy(username = "", password = ""))
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { login.invoke(any()) } returns DonaResult.Error(DonaFailure.InvalidCredentials("Wrong password"))
            viewModel.login()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.retryLoginIfNeeded()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { login.invoke(any()) }
        }

    @Test
    fun `credentials edited on the houses screen flow back into the login fields`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            val edited = house.copy(username = "bob", password = "hunter2")

            houses.value = listOf(edited)
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(edited, state.selectedHouse)
            assertEquals("bob", state.username)
            assertEquals("hunter2", state.password)
        }

    @Test
    fun `renaming the selected house falls back to the stored profile`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            val renamed = house.copy(name = "Cabin", username = "bob")

            houses.value = listOf(renamed)
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(renamed, state.selectedHouse)
            assertEquals("bob", state.username)
        }

    @Test
    fun `refreshed credentials clear a stale error message`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { login.invoke(any()) } returns DonaResult.Error(DonaFailure.InvalidCredentials("Wrong password"))
            viewModel.login()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("Wrong password", viewModel.uiState.value.errorMessage)

            houses.value = listOf(house.copy(password = "corrected"))
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorMessage)
        }
}
