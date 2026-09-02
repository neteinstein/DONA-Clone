package com.neteinstein.donaclone.feature.houses

import app.cash.turbine.test
import com.neteinstein.donaclone.core.domain.usecase.DeleteHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.DiscoverHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveHouseUseCase
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.HubType
import io.mockk.coEvery
import io.mockk.coVerify
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { observeHouses() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HousesViewModel(observeHouses, saveHouse, deleteHouse, discoverHouses)

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
    fun `delete forwards to the use case`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val house = House(name = "Home", localIp = "10.0.0.1")

            viewModel.delete(house)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { deleteHouse("Home") }
        }
}
