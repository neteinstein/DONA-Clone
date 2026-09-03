package com.neteinstein.donaclone.feature.devices

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveRoomOrderUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetRoomOrderUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.model.PulseKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val getRooms = mockk<GetRoomsUseCase>()
    private val getDevices = mockk<GetDevicesUseCase>()
    private val sendCommand = mockk<SendDeviceCommandUseCase>()
    private val observeDeviceUpdates = mockk<ObserveDeviceUpdatesUseCase>()
    private val getCurrentSession = mockk<GetCurrentSessionUseCase>()
    private val logout = mockk<LogoutUseCase>()
    private val observeRoomsExpandedByDefault = mockk<ObserveRoomsExpandedByDefaultUseCase>()
    private val setRoomsExpandedByDefault = mockk<SetRoomsExpandedByDefaultUseCase>()
    private val observeRoomOrder = mockk<ObserveRoomOrderUseCase>()
    private val setRoomOrder = mockk<SetRoomOrderUseCase>()
    private val updates = MutableSharedFlow<DeviceUpdate>()

    private val light = Device.BinaryOutput(id = 1, name = "Kitchen light", isOn = false)
    private val shutter = Device.Shutter(id = 2, name = "Living room blinds", percentage = 40)
    private val lock = Device.Pulse(id = 3, name = "Front door lock", kind = PulseKind.LOCK)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getRooms() } returns DonaResult.Success(emptyList())
        coEvery { getDevices() } returns DonaResult.Success(listOf(light, shutter, lock))
        coEvery { observeDeviceUpdates() } returns updates
        every { getCurrentSession() } returns null
        every { observeRoomsExpandedByDefault() } returns flowOf(true)
        coEvery { setRoomsExpandedByDefault(any()) } returns Unit
        every { observeRoomOrder() } returns flowOf(emptyList())
        coEvery { setRoomOrder(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        DevicesViewModel(
            getRooms,
            getDevices,
            sendCommand,
            observeDeviceUpdates,
            getCurrentSession,
            logout,
            observeRoomsExpandedByDefault,
            setRoomsExpandedByDefault,
            observeRoomOrder,
            setRoomOrder,
        )

    @Test
    fun `a live update flips the cached device state`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val loaded = expectMostRecentItem()
                assertEquals(false, (loaded.devices.first { it.id == 1 } as Device.BinaryOutput).isOn)

                updates.emit(DeviceUpdate.BinaryStatus(deviceId = 1, isOn = true))

                val updated = awaitItem()
                assertEquals(true, (updated.devices.first { it.id == 1 } as Device.BinaryOutput).isOn)
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

    @Test
    fun `tapping a shutter below 50 percent open opens it fully`() =
        runTest(dispatcher) {
            coEvery { sendCommand(DeviceCommand.SetShutterOpen(2)) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onShutterTap(shutter)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { sendCommand(DeviceCommand.SetShutterOpen(2)) }
        }

    @Test
    fun `tapping a shutter at or above 50 percent open closes it fully`() =
        runTest(dispatcher) {
            val halfOpenOrMore = shutter.copy(percentage = 75)
            coEvery { sendCommand(DeviceCommand.SetShutterClosed(2)) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onShutterTap(halfOpenOrMore)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { sendCommand(DeviceCommand.SetShutterClosed(2)) }
        }

    @Test
    fun `firing a pulse marks it recently-fired then clears the mark`() =
        runTest(dispatcher) {
            coEvery { sendCommand(DeviceCommand.FirePulse(3)) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.firePulse(lock)
            dispatcher.scheduler.runCurrent()
            assertTrue(3 in viewModel.uiState.value.recentlyFiredDeviceIds)

            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(3 !in viewModel.uiState.value.recentlyFiredDeviceIds)
        }

    @Test
    fun `an actionless sensor shows on the Sensors tab, not the Home tab`() =
        runTest(dispatcher) {
            val sensor = Device.BinaryInput(id = 4, name = "Sensor Fumo", isActive = false)
            coEvery { getDevices() } returns DonaResult.Success(listOf(light, sensor))
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.homeDisplayItemsByRoom.values.flatten().none { it.primary.id == 4 })
            assertTrue(state.sensorDisplayItemsByRoom.values.flatten().any { it.primary.id == 4 })
            assertTrue(state.homeDisplayItemsByRoom.values.flatten().any { it.primary.id == 1 })
        }

    @Test
    fun `rooms default to alphabetical order until the user reorders them`() =
        runTest(dispatcher) {
            val kitchen = Device.BinaryOutput(id = 5, name = "Kitchen plug", roomId = 20, isOn = false)
            val attic = Device.BinaryOutput(id = 6, name = "Attic plug", roomId = 10, isOn = false)
            coEvery { getRooms() } returns
                DonaResult.Success(listOf(Division(id = 20, name = "Kitchen", floor = null), Division(id = 10, name = "Attic", floor = null)))
            coEvery { getDevices() } returns DonaResult.Success(listOf(kitchen, attic))
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(10, 20), viewModel.uiState.value.roomOrder)
        }

    @Test
    fun `dragging a room persists the custom order`() =
        runTest(dispatcher) {
            val kitchen = Device.BinaryOutput(id = 5, name = "Kitchen plug", roomId = 20, isOn = false)
            val attic = Device.BinaryOutput(id = 6, name = "Attic plug", roomId = 10, isOn = false)
            coEvery { getRooms() } returns
                DonaResult.Success(listOf(Division(id = 20, name = "Kitchen", floor = null), Division(id = 10, name = "Attic", floor = null)))
            coEvery { getDevices() } returns DonaResult.Success(listOf(kitchen, attic))
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onMoveRoom(roomKey = 20, targetRoomKey = 10)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(20, 10), viewModel.uiState.value.roomOrder)
            coVerify { setRoomOrder(listOf(20, 10)) }
        }
}
