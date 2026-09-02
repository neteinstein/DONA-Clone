package com.neteinstein.donaclone.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val isLoading: Boolean = true,
    val rooms: List<Division> = emptyList(),
    val devices: List<Device> = emptyList(),
    val errorMessage: String? = null,
) {
    val devicesByRoom: Map<Int?, List<Device>> get() = devices.groupBy { it.roomId }
}

class DevicesViewModel(
    private val getRooms: GetRoomsUseCase,
    private val getDevices: GetDevicesUseCase,
    private val sendCommand: SendDeviceCommandUseCase,
    observeDeviceUpdates: ObserveDeviceUpdatesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            observeDeviceUpdates().collect { update ->
                _uiState.update { it.copy(devices = it.devices.map { device -> device.withUpdate(update) }) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val roomsResult = getRooms()
            val devicesResult = getDevices()

            _uiState.update { state ->
                val failure = (roomsResult as? DonaResult.Error)?.failure ?: (devicesResult as? DonaResult.Error)?.failure
                state.copy(
                    isLoading = false,
                    rooms = (roomsResult as? DonaResult.Success)?.data ?: state.rooms,
                    devices = (devicesResult as? DonaResult.Success)?.data ?: state.devices,
                    errorMessage = failure?.message,
                )
            }
        }
    }

    fun toggleBinaryOutput(device: Device.BinaryOutput) = execute(DeviceCommand.SetBinaryOutput(device.id, !device.isOn))

    fun firePulse(device: Device.Pulse) = execute(DeviceCommand.FirePulse(device.id))

    fun openShutter(device: Device.Shutter) = execute(DeviceCommand.SetShutterOpen(device.id))

    fun closeShutter(device: Device.Shutter) = execute(DeviceCommand.SetShutterClosed(device.id))

    fun setShutterPercentage(
        device: Device.Shutter,
        percentage: Int,
    ) =
        execute(DeviceCommand.SetShutterPercentage(device.id, percentage))

    fun setDimmerPercentage(
        device: Device.Dimmer,
        percentage: Int,
    ) =
        execute(DeviceCommand.SetDimmerPercentage(device.id, percentage))

    private fun execute(command: DeviceCommand) {
        viewModelScope.launch {
            when (val result = sendCommand(command)) {
                is DonaResult.Error -> _uiState.update { it.copy(errorMessage = result.failure.message) }
                is DonaResult.Success -> Unit
            }
        }
    }
}

private fun Device.withUpdate(update: DeviceUpdate): Device {
    if (id != update.deviceId) return this
    return when (this) {
        is Device.BinaryOutput -> (update as? DeviceUpdate.BinaryStatus)?.let { copy(isOn = it.isOn) } ?: this
        is Device.BinaryInput -> (update as? DeviceUpdate.BinaryStatus)?.let { copy(isActive = it.isOn) } ?: this
        is Device.Shutter -> (update as? DeviceUpdate.Percentage)?.let { copy(percentage = it.percentage) } ?: this
        is Device.Dimmer -> (update as? DeviceUpdate.Percentage)?.let { copy(percentage = it.percentage) } ?: this
        is Device.AnalogInput -> (update as? DeviceUpdate.NumericValue)?.let { copy(value = it.value) } ?: this
        is Device.Counter -> (update as? DeviceUpdate.NumericValue)?.let { copy(value = it.value) } ?: this
        is Device.Pulse, is Device.UnknownDevice -> this
    }
}
