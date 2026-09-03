package com.neteinstein.donaclone.feature.devices

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val getDevices = mockk<GetDevicesUseCase>()
    private val getRooms = mockk<GetRoomsUseCase>()
    private val sendCommand = mockk<SendDeviceCommandUseCase>()
    private val observeDeviceUpdates = mockk<ObserveDeviceUpdatesUseCase>()
    private val updates = MutableSharedFlow<DeviceUpdate>()

    private val shutter = Device.Shutter(id = 2, name = "Living room blinds", roomId = 10, percentage = 40)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getDevices() } returns DonaResult.Success(listOf(shutter))
        coEvery { getRooms() } returns DonaResult.Success(listOf(Division(id = 10, name = "Living Room", floor = 0)))
        coEvery { observeDeviceUpdates() } returns updates
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DeviceDetailViewModel(2, getDevices, getRooms, observeDeviceUpdates, sendCommand)

    @Test
    fun `loads the device by id and its room name`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(shutter, state.device)
            assertEquals("Living Room", state.roomName)
        }

    @Test
    fun `a live update for this device id is reflected`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            updates.emit(DeviceUpdate.Percentage(deviceId = 2, percentage = 80))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(80, (viewModel.uiState.value.device as Device.Shutter).percentage)
        }

    @Test
    fun `open and close forward the exact device command`() =
        runTest(dispatcher) {
            coEvery { sendCommand(DeviceCommand.SetShutterOpen(2)) } returns DonaResult.Success(Unit)
            coEvery { sendCommand(DeviceCommand.SetShutterClosed(2)) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.openShutter(shutter)
            viewModel.closeShutter(shutter)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { sendCommand(DeviceCommand.SetShutterOpen(2)) }
            coVerify { sendCommand(DeviceCommand.SetShutterClosed(2)) }
        }
}
