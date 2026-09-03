package com.neteinstein.donaclone.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceDisplayItem
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.model.groupDevices
import com.neteinstein.donaclone.core.model.isDeviceOpenOrOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sentinel standing in for a null [Device.roomId] ("Unassigned") — shared by [DevicesScreen] for
 * rendering and by this file for collapse-state bookkeeping; same-package visibility means no
 * import is needed on either side. */
internal const val UNASSIGNED_ROOM_KEY = Int.MIN_VALUE

data class DevicesUiState(
    val isLoading: Boolean = true,
    val rooms: List<Division> = emptyList(),
    val devices: List<Device> = emptyList(),
    val errorMessage: String? = null,
    val houseName: String = "",
    val userName: String = "",
    val loggedOut: Boolean = false,
    /** Devices whose [Device.Pulse] was just fired — ephemeral, in-memory-only tap feedback for a
     * device kind (locks, sirens, ...) the hub never reports a persisted on/off state for. */
    val recentlyFiredDeviceIds: Set<Int> = emptySet(),
    val collapsedRoomIds: Set<Int> = emptySet(),
) {
    val displayItemsByRoom: Map<Int?, List<DeviceDisplayItem>>
        get() = groupDevices(devices).groupBy { it.primary.roomId }

    val allRoomsCollapsed: Boolean
        get() {
            val keys = displayItemsByRoom.keys.map { it ?: UNASSIGNED_ROOM_KEY }.toSet()
            return keys.isNotEmpty() && keys.all { it in collapsedRoomIds }
        }
}

class DevicesViewModel(
    private val getRooms: GetRoomsUseCase,
    private val getDevices: GetDevicesUseCase,
    private val sendCommand: SendDeviceCommandUseCase,
    observeDeviceUpdates: ObserveDeviceUpdatesUseCase,
    getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
    private val observeRoomsExpandedByDefault: ObserveRoomsExpandedByDefaultUseCase,
    private val setRoomsExpandedByDefault: SetRoomsExpandedByDefaultUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            getCurrentSession()?.let { session ->
                DevicesUiState(houseName = session.houseName, userName = session.userName)
            } ?: DevicesUiState(),
        )
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val lastNonZeroDimmerPercentage = mutableMapOf<Int, Int>()
    private var hasSeededCollapseState = false

    init {
        refresh()
        viewModelScope.launch {
            observeDeviceUpdates().collect { update ->
                _uiState.update { it.copy(devices = it.devices.map { device -> device.withUpdate(update) }) }
                rememberNonZeroDimmerPercentage(update)
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
            (devicesResult as? DonaResult.Success)?.data.orEmpty().forEach { device ->
                if (device is Device.Dimmer && device.percentage > 0) {
                    lastNonZeroDimmerPercentage[device.id] = device.percentage
                }
            }

            if (!hasSeededCollapseState) {
                hasSeededCollapseState = true
                val expandedByDefault = observeRoomsExpandedByDefault().first()
                _uiState.update { state ->
                    val keys = state.displayItemsByRoom.keys.map { it ?: UNASSIGNED_ROOM_KEY }.toSet()
                    state.copy(collapsedRoomIds = if (expandedByDefault) emptySet() else keys)
                }
            }
        }
    }

    fun toggleBinaryOutput(device: Device.BinaryOutput) = execute(DeviceCommand.SetBinaryOutput(device.id, !device.isOn))

    /** Fires the pulse and briefly marks the tile as "just triggered" — see [DevicesUiState.recentlyFiredDeviceIds]. */
    fun firePulse(device: Device.Pulse) {
        execute(DeviceCommand.FirePulse(device.id))
        _uiState.update { it.copy(recentlyFiredDeviceIds = it.recentlyFiredDeviceIds + device.id) }
        viewModelScope.launch {
            delay(PULSE_FEEDBACK_MILLIS)
            _uiState.update { it.copy(recentlyFiredDeviceIds = it.recentlyFiredDeviceIds - device.id) }
        }
    }

    fun openShutter(device: Device.Shutter) = execute(DeviceCommand.SetShutterOpen(device.id))

    fun closeShutter(device: Device.Shutter) = execute(DeviceCommand.SetShutterClosed(device.id))

    /** Tap rule for the Home tab's shutter tile: below 50% open it opens fully, at/above 50% open
     * it closes fully (the detail screen's explicit Open/Close buttons cover every other case). */
    fun onShutterTap(device: Device.Shutter) {
        if (device.percentage < 50) openShutter(device) else closeShutter(device)
    }

    fun setShutterPercentage(
        device: Device.Shutter,
        percentage: Int,
    ) = execute(DeviceCommand.SetShutterPercentage(device.id, percentage))

    fun setDimmerPercentage(
        device: Device.Dimmer,
        percentage: Int,
    ) = execute(DeviceCommand.SetDimmerPercentage(device.id, percentage))

    /** Tap rule for the Home tab's dimmer tile: toggles between off and its last-known-on level. */
    fun onDimmerTap(device: Device.Dimmer) {
        val target = if (device.percentage > 0) 0 else lastNonZeroDimmerPercentage[device.id] ?: 100
        setDimmerPercentage(device, target)
    }

    /** Tap rule for a merged Home tab tile that has its own open/close action devices instead of
     * (or in addition to) a native shutter — a native [Device.Shutter] primary is always
     * authoritative and keeps using [onShutterTap] instead. */
    fun onGroupedTap(item: DeviceDisplayItem.Grouped) {
        val primary = item.primary
        when {
            primary is Device.Shutter -> onShutterTap(primary)
            item.openAction != null && item.closeAction != null ->
                if (isDeviceOpenOrOn(primary)) firePulse(item.closeAction) else firePulse(item.openAction)
            item.openAction != null -> firePulse(item.openAction)
            item.closeAction != null -> firePulse(item.closeAction)
            // No actions at all (e.g. on/off + hidden numeric reading) — caller falls back to the
            // primary device's own default tap behavior.
        }
    }

    fun toggleRoomCollapsed(roomKey: Int) {
        _uiState.update { state ->
            state.copy(
                collapsedRoomIds =
                    if (roomKey in state.collapsedRoomIds) {
                        state.collapsedRoomIds - roomKey
                    } else {
                        state.collapsedRoomIds + roomKey
                    },
            )
        }
    }

    /** Collapses every room if any is currently expanded, otherwise expands every room — a simple
     * binary "expand all / collapse all" toggle. The resulting all-open/all-closed state is
     * persisted as the default for the next time the Home tab is opened fresh. */
    fun toggleAllRooms() {
        val target =
            if (_uiState.value.allRoomsCollapsed) {
                emptySet()
            } else {
                _uiState.value.displayItemsByRoom.keys.map { it ?: UNASSIGNED_ROOM_KEY }.toSet()
            }
        _uiState.update { it.copy(collapsedRoomIds = target) }
        viewModelScope.launch { setRoomsExpandedByDefault(target.isEmpty()) }
    }

    fun logout() {
        viewModelScope.launch {
            logout.invoke()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    private fun execute(command: DeviceCommand) {
        viewModelScope.launch {
            when (val result = sendCommand(command)) {
                is DonaResult.Error -> _uiState.update { it.copy(errorMessage = result.failure.message) }
                is DonaResult.Success -> Unit
            }
        }
    }

    private fun rememberNonZeroDimmerPercentage(update: DeviceUpdate) {
        if (update is DeviceUpdate.Percentage && update.percentage > 0) {
            val device = _uiState.value.devices.firstOrNull { it.id == update.deviceId }
            if (device is Device.Dimmer) {
                lastNonZeroDimmerPercentage[update.deviceId] = update.percentage
            }
        }
    }

    private companion object {
        const val PULSE_FEEDBACK_MILLIS = 1200L
    }
}
