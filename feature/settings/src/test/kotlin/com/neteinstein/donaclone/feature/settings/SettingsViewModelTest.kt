package com.neteinstein.donaclone.feature.settings

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.CanInstallUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.DownloadUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.InstallUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDebugModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.OpenInstallPermissionSettingsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetDebugModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.AuthSession
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.core.model.UpdateAvailability
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getCurrentSession = mockk<GetCurrentSessionUseCase>()
    private val logout = mockk<LogoutUseCase>()
    private val observeThemeMode = mockk<ObserveThemeModeUseCase>()
    private val setThemeMode = mockk<SetThemeModeUseCase>()
    private val observeBiometricEnabled = mockk<ObserveBiometricEnabledUseCase>()
    private val setBiometricEnabled = mockk<SetBiometricEnabledUseCase>()
    private val checkForUpdate = mockk<CheckForUpdateUseCase>()
    private val downloadUpdate = mockk<DownloadUpdateUseCase>()
    private val canInstallUpdates = mockk<CanInstallUpdatesUseCase>()
    private val installUpdate = mockk<InstallUpdateUseCase>()
    private val openInstallPermissionSettings = mockk<OpenInstallPermissionSettingsUseCase>()
    private val observeDebugModeEnabled = mockk<ObserveDebugModeUseCase>()
    private val setDebugModeEnabled = mockk<SetDebugModeUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { observeThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        every { observeBiometricEnabled() } returns flowOf(false)
        every { observeDebugModeEnabled() } returns flowOf(false)
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
            checkForUpdate,
            downloadUpdate,
            canInstallUpdates,
            installUpdate,
            openInstallPermissionSettings,
            observeDebugModeEnabled,
            setDebugModeEnabled,
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

    @Test
    fun `entering the screen surfaces an available update`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            val update = AppUpdate(versionName = "0.2.0.5", versionCode = 5, apkDownloadUrl = "https://example.com/app.apk")
            coEvery { checkForUpdate() } returns DonaResult.Success(UpdateAvailability.Available(update))
            val viewModel = createViewModel()

            viewModel.onScreenEntered()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.UpdateAvailable(update), viewModel.uiState.value.updateStatus)
        }

    @Test
    fun `tapping update when unavailable checks then reports up to date`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { checkForUpdate() } returns DonaResult.Success(UpdateAvailability.UpToDate("0.1.0.3"))
            val viewModel = createViewModel()

            viewModel.onUpdateClicked()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.UpToDate("0.1.0.3"), viewModel.uiState.value.updateStatus)
        }

    @Test
    fun `tapping update surfaces a failure message when the check itself fails`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { checkForUpdate() } returns DonaResult.Error(DonaFailure.Unknown("boom"))
            val viewModel = createViewModel()

            viewModel.onUpdateClicked()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.Failed("boom"), viewModel.uiState.value.updateStatus)
        }

    @Test
    fun `tapping update when available downloads and installs it`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            val update = AppUpdate(versionName = "0.2.0.5", versionCode = 5, apkDownloadUrl = "https://example.com/app.apk")
            coEvery { checkForUpdate() } returns DonaResult.Success(UpdateAvailability.Available(update))
            every { canInstallUpdates() } returns true
            val apkFile = File("update.apk")
            coEvery { downloadUpdate(update) } returns DonaResult.Success(apkFile)
            every { installUpdate(apkFile) } returns Unit
            val viewModel = createViewModel()

            viewModel.onScreenEntered()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onUpdateClicked()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { downloadUpdate(update) }
            coVerify { installUpdate(apkFile) }
            assertEquals(UpdateStatus.Idle, viewModel.uiState.value.updateStatus)
        }

    @Test
    fun `tapping update when sideloading is blocked surfaces the warning without downloading`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            val update = AppUpdate(versionName = "0.2.0.5", versionCode = 5, apkDownloadUrl = "https://example.com/app.apk")
            coEvery { checkForUpdate() } returns DonaResult.Success(UpdateAvailability.Available(update))
            every { canInstallUpdates() } returns false
            val viewModel = createViewModel()

            viewModel.onScreenEntered()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onUpdateClicked()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.SideloadingBlocked, viewModel.uiState.value.updateStatus)
            coVerify(exactly = 0) { downloadUpdate(any()) }
        }

    @Test
    fun `debug mode is off by default and toggling it persists the new value`() =
        runTest(dispatcher) {
            every { getCurrentSession() } returns null
            coEvery { setDebugModeEnabled(true) } just Runs
            val viewModel = createViewModel()

            assertEquals(false, viewModel.uiState.value.debugModeEnabled)
            viewModel.onDebugModeChanged(true)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setDebugModeEnabled(true) }
        }

    @Test
    fun `enabling sideloading opens the system permission settings`() {
        every { getCurrentSession() } returns null
        every { openInstallPermissionSettings() } returns Unit
        val viewModel = createViewModel()

        viewModel.onEnableSideloadingClicked()

        verify { openInstallPermissionSettings() }
    }
}
