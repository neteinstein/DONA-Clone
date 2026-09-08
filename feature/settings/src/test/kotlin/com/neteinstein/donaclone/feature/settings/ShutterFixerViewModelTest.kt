package com.neteinstein.donaclone.feature.settings

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveInvertedShuttersUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetShutterInvertedUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShutterFixerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val shutter = Device.Shutter(id = 7, name = "Kitchen blind", roomId = 1, percentage = 30)
    private val light = Device.BinaryOutput(id = 8, name = "Kitchen light", roomId = 1, isOn = true)

    private val getDevices = mockk<GetDevicesUseCase>()
    private val getRooms = mockk<GetRoomsUseCase>()
    private val observeInvertedShutters = mockk<ObserveInvertedShuttersUseCase>()
    private val setShutterInverted = mockk<SetShutterInvertedUseCase>(relaxed = true)
    private val invertedIds = MutableStateFlow(emptySet<Int>())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ShutterFixerViewModel {
        coEvery { getDevices() } returns DonaResult.Success(listOf(shutter, light))
        coEvery { getRooms() } returns DonaResult.Success(listOf(Division(id = 1, name = "Kitchen", floor = null)))
        every { observeInvertedShutters() } returns invertedIds
        return ShutterFixerViewModel(getDevices, getRooms, observeInvertedShutters, setShutterInverted)
    }

    @Test
    fun `only shutters are listed`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                assertEquals(listOf(shutter), expectMostRecentItem().shutters)
            }
        }

    @Test
    fun `the stored inverted set is reflected in the ui state`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            invertedIds.value = setOf(7)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                assertEquals(setOf(7), expectMostRecentItem().invertedIds)
            }
        }

    @Test
    fun `ticking a shutter persists the flag and re-reads the devices`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setInverted(deviceId = 7, inverted = true)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { setShutterInverted(7, true) }
            coVerify(atLeast = 2) { getDevices() }
        }
}
