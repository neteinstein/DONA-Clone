package com.neteinstein.donaclone.feature.devices

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
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
class DevicesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val getRooms = mockk<GetRoomsUseCase>()
    private val getDevices = mockk<GetDevicesUseCase>()
    private val sendCommand = mockk<SendDeviceCommandUseCase>()
    private val observeDeviceUpdates = mockk<ObserveDeviceUpdatesUseCase>()
    private val updates = MutableSharedFlow<DeviceUpdate>()

    private val light = Device.BinaryOutput(id = 1, name = "Kitchen light", isOn = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getRooms() } returns DonaResult.Success(emptyList())
        coEvery { getDevices() } returns DonaResult.Success(listOf(light))
        coEvery { observeDeviceUpdates() } returns updates
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DevicesViewModel(getRooms, getDevices, sendCommand, observeDeviceUpdates)

    @Test
    fun `a live update flips the cached device state`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val loaded = expectMostRecentItem()
                assertEquals(false, (loaded.devices.first() as Device.BinaryOutput).isOn)

                updates.emit(DeviceUpdate.BinaryStatus(deviceId = 1, isOn = true))

                val updated = awaitItem()
                assertEquals(true, (updated.devices.first() as Device.BinaryOutput).isOn)
            }
        }

    @Test
    fun `toggling a binary output sends the opposite of its current state`() =
        runTest(dispatcher) {
            coEvery { sendCommand(DeviceCommand.SetBinaryOutput(1, true)) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.toggleBinaryOutput(light)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { sendCommand(DeviceCommand.SetBinaryOutput(1, true)) }
        }
}
