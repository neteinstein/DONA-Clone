package com.neteinstein.donaclone.feature.houses

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.CanInstallUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.DeleteHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.DiscoverHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.DownloadUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.InstallUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.OpenInstallPermissionSettingsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.HubType
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.core.model.UpdateAvailability
import com.neteinstein.donaclone.core.model.UpdateStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class HousesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val observeHouses = mockk<ObserveHousesUseCase>()
    private val saveHouse = mockk<SaveHouseUseCase>(relaxUnitFun = true)
    private val deleteHouse = mockk<DeleteHouseUseCase>(relaxUnitFun = true)
    private val discoverHouses = mockk<DiscoverHousesUseCase>()
    private val observeThemeMode = mockk<ObserveThemeModeUseCase>()
    private val setThemeMode = mockk<SetThemeModeUseCase>(relaxUnitFun = true)
    private val checkForUpdate = mockk<CheckForUpdateUseCase>()
    private val downloadUpdate = mockk<DownloadUpdateUseCase>()
    private val canInstallUpdates = mockk<CanInstallUpdatesUseCase>()
    private val installUpdate = mockk<InstallUpdateUseCase>()
    private val openInstallPermissionSettings = mockk<OpenInstallPermissionSettingsUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { observeHouses() } returns flowOf(emptyList())
        every { observeThemeMode() } returns flowOf(ThemeMode.SYSTEM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        HousesViewModel(
            observeHouses,
            saveHouse,
            deleteHouse,
            discoverHouses,
            observeThemeMode,
            setThemeMode,
            checkForUpdate,
            downloadUpdate,
            canInstallUpdates,
            installUpdate,
            openInstallPermissionSettings,
        )

    @Test
    fun `applying a discovered house fills in the local IP`() =
        runTest(dispatcher) {
            coEvery { discoverHouses() } returns flowOf()
            val discovered =
                DiscoveredHouse(
                    mac = "AA:BB",
                    ip = "192.168.1.77",
                    gateway = null,
                    subnetMask = null,
                    dhcp = true,
                    hubType = HubType.DPU,
                    serialNumber = "SN1",
                    hardwareVersion = "1.0",
                    firmwareVersion = "1.4",
                )
            val viewModel = createViewModel()

            viewModel.startAddingHouse()
            viewModel.applyDiscoveredHouse(discovered)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                val editing = state.mode as HousesMode.Editing
                assertEquals("192.168.1.77", editing.draft.localIp)
            }
        }

    @Test
    fun `saving a draft persists it and returns to the list`() =
        runTest(dispatcher) {
            coEvery { discoverHouses() } returns flowOf()
            val viewModel = createViewModel()

            viewModel.startAddingHouse()
            viewModel.updateDraft { it.copy(name = "My Home", localIp = "10.0.0.5") }
            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(HousesMode.List, state.mode)
            }
            coVerify { saveHouse(House(name = "My Home", localIp = "10.0.0.5")) }
        }

    @Test
    fun `editHouseNamed opens the editor for the matching house`() =
        runTest(dispatcher) {
            val house = House(name = "Home", localIp = "10.0.0.1", username = "alice")
            coEvery { observeHouses() } returns flowOf(listOf(house))
            val viewModel = createViewModel()

            viewModel.editHouseNamed("Home")
            dispatcher.scheduler.advanceUntilIdle()

            val editing = viewModel.uiState.value.mode as HousesMode.Editing
            assertEquals(house, editing.original)
            assertEquals(house, editing.draft)
        }

    @Test
    fun `editHouseNamed only opens the editor once, so cancelling isn't undone`() =
        runTest(dispatcher) {
            coEvery { observeHouses() } returns flowOf(listOf(House(name = "Home")))
            val viewModel = createViewModel()

            viewModel.editHouseNamed("Home")
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.cancelEditing()
            viewModel.editHouseNamed("Home")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(HousesMode.List, viewModel.uiState.value.mode)
        }

    @Test
    fun `editHouseNamed does nothing for an unknown house`() =
        runTest(dispatcher) {
            coEvery { observeHouses() } returns flowOf(listOf(House(name = "Home")))
            val viewModel = createViewModel()

            viewModel.editHouseNamed("Cabin")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(HousesMode.List, viewModel.uiState.value.mode)
        }

    @Test
    fun `delete forwards to the use case`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val house = House(name = "Home", localIp = "10.0.0.1")

            viewModel.delete(house)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { deleteHouse("Home") }
        }

    @Test
    fun `selecting a theme mode persists it`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onThemeModeSelected(ThemeMode.DARK)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setThemeMode(ThemeMode.DARK) }
        }

    @Test
    fun `entering the screen surfaces an available update`() =
        runTest(dispatcher) {
            val update = AppUpdate(versionName = "0.2.0.5", versionCode = 5, apkDownloadUrl = "https://example.com/app.apk")
            coEvery { checkForUpdate() } returns DonaResult.Success(UpdateAvailability.Available(update))
            val viewModel = createViewModel()

            viewModel.onScreenEntered()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.UpdateAvailable(update), viewModel.uiState.value.updateStatus)
        }

    @Test
    fun `tapping update surfaces a failure message when the check itself fails`() =
        runTest(dispatcher) {
            coEvery { checkForUpdate() } returns DonaResult.Error(DonaFailure.Unknown("boom"))
            val viewModel = createViewModel()

            viewModel.onUpdateClicked()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.Failed("boom"), viewModel.uiState.value.updateStatus)
        }
}
