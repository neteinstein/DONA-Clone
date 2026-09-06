package com.neteinstein.donaclone.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveActionConfirmationEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceDisplayItem
import com.neteinstein.donaclone.core.model.groupDevices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceDetailUiState(
    val isLoading: Boolean = true,
    val device: Device? = null,
    val displayName: String? = null,
    val secondaryDevices: List<Device> = emptyList(),
    val openAction: Device.Pulse? = null,
    val closeAction: Device.Pulse? = null,
    val roomName: String? = null,
    val errorMessage: String? = null,
    val actionConfirmationEnabled: Boolean = true,
)

class DeviceDetailViewModel(
    private val deviceId: Int,
    private val getDevices: GetDevicesUseCase,
    private val getRooms: GetRoomsUseCase,
    observeDeviceUpdates: ObserveDeviceUpdatesUseCase,
    private val sendCommand: SendDeviceCommandUseCase,
    observeActionConfirmationEnabled: ObserveActionConfirmationEnabledUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            observeDeviceUpdates().collect { update ->
                if (update.deviceId == deviceId) {
                    _uiState.update { state -> state.copy(device = state.device?.withUpdate(update)) }
                }
            }
        }
        viewModelScope.launch {
            observeActionConfirmationEnabled().collect { enabled ->
                _uiState.update { it.copy(actionConfirmationEnabled = enabled) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val roomsResult = getRooms()
            val rooms = (roomsResult as? DonaResult.Success)?.data.orEmpty()

            when (val result = getDevices()) {
                is DonaResult.Success -> {
                    val displayItem = groupDevices(result.data).firstOrNull { it.primary.id == deviceId }
                    val device = displayItem?.primary
                    val grouped = displayItem as? DeviceDisplayItem.Grouped
                    val roomName = rooms.firstOrNull { it.id == device?.roomId }?.name
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            device = device,
                            displayName = grouped?.displayName,
                            secondaryDevices = grouped?.secondaries.orEmpty(),
                            openAction = grouped?.openAction,
                            closeAction = grouped?.closeAction,
                            roomName = roomName,
                            errorMessage = if (device == null) "Device not found" else null,
                        )
                    }
                }
                is DonaResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.failure.message) }
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
    ) = execute(DeviceCommand.SetShutterPercentage(device.id, percentage))

    fun setDimmerPercentage(
        device: Device.Dimmer,
        percentage: Int,
    ) = execute(DeviceCommand.SetDimmerPercentage(device.id, percentage))

    private fun execute(command: DeviceCommand) {
        viewModelScope.launch {
            when (val result = sendCommand(command)) {
                is DonaResult.Error -> _uiState.update { it.copy(errorMessage = result.failure.message) }
                is DonaResult.Success -> Unit
            }
        }
    }
}
