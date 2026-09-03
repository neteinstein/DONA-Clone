package com.neteinstein.donaclone.feature.settings

import app.cash.turbine.test
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.ThemeMode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getCurrentSession = mockk<GetCurrentSessionUseCase>()
    private val logout = mockk<LogoutUseCase>()
    private val observeThemeMode = mockk<ObserveThemeModeUseCase>()
    private val setThemeMode = mockk<SetThemeModeUseCase>()
    private val observeBiometricEnabled = mockk<ObserveBiometricEnabledUseCase>()
    private val setBiometricEnabled = mockk<SetBiometricEnabledUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { observeThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        every { observeBiometricEnabled() } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(
            getCurrentSession,
            logout,
            observeThemeMode,
            setThemeMode,
            observeBiometricEnabled,
            setBiometricEnabled,
        )

    @Test
    fun `initial state reflects the current session`() {
        every { getCurrentSession() } returns AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
        val viewModel = createViewModel()

        assertEquals("Home", viewModel.uiState.value.houseName)
        assertEquals("alice", viewModel.uiState.value.userName)
    }

    @Test
    fun `logout marks the state as logged out`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { logout.invoke() } just Runs
            val viewModel = createViewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.logout()
                val loggedOut = awaitItem()
                assertEquals(true, loggedOut.loggedOut)
            }
        }

    @Test
    fun `selecting a theme mode persists it`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { setThemeMode(ThemeMode.DARK) } just Runs
            val viewModel = createViewModel()

            viewModel.onThemeModeSelected(ThemeMode.DARK)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setThemeMode(ThemeMode.DARK) }
        }

    @Test
    fun `toggling biometric persists the new value`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { setBiometricEnabled(true) } just Runs
            val viewModel = createViewModel()

            viewModel.onBiometricEnabledChanged(true)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setBiometricEnabled(true) }
        }
}
