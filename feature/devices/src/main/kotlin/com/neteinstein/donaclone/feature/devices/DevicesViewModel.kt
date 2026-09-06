package com.neteinstein.donaclone.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.neteinstein.donaclone.core.model.DeviceDisplayItem
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import com.neteinstein.donaclone.core.model.groupDevices
import com.neteinstein.donaclone.core.model.isActionlessSensor
import com.neteinstein.donaclone.core.model.isDeviceOpenOrOn
import com.neteinstein.donaclone.core.model.isOrphanedActionSensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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
    /** The user's drag-to-reorder room/category order, as persisted — empty until they've ever
     * dragged one. Room ids the user never placed (new to the hub, or dragged before they existed)
     * fall back to alphabetical order, appended after whatever the user did place — see
     * [roomOrder]. Shared by the Home and Sensors tabs so a room stays in the same relative
     * position on both. */
    val customRoomOrder: List<Int> = emptyList(),
) {
    /** Every room/category currently in use (room ids, plus [UNASSIGNED_ROOM_KEY]), in display
     * order: the user's own [customRoomOrder] first, then anything they haven't placed yet in
     * alphabetical-by-name order. */
    val roomOrder: List<Int>
        get() {
            val presentKeys = groupDevices(devices).map { it.primary.roomId ?: UNASSIGNED_ROOM_KEY }.toSet()
            val roomNames = rooms.associate { it.id to it.name } + (UNASSIGNED_ROOM_KEY to "Unassigned")
            val alphabetical = presentKeys.sortedBy { roomNames[it]?.lowercase() ?: "" }
            val customPresent = customRoomOrder.filter { it in presentKeys }
            return customPresent + alphabetical.filterNot { it in customPresent }
        }

    /** Home tab: every display item with a tap action of its own, grouped by room and ordered per
     * [roomOrder]. Actionless sensors live on the Sensors tab instead — see [sensorDisplayItemsByRoom]. */
    val homeDisplayItemsByRoom: Map<Int, List<DeviceDisplayItem>>
        get() = itemsByRoomInOrder { !it.isActionlessSensor }

    /** Sensors tab: read-only sensors with no action of their own (a door contact, a humidity
     * reading, ...), grouped by room and ordered per [roomOrder]. */
    val sensorDisplayItemsByRoom: Map<Int, List<DeviceDisplayItem>>
        get() = itemsByRoomInOrder { it.isActionlessSensor }

    private fun itemsByRoomInOrder(predicate: (DeviceDisplayItem) -> Boolean): Map<Int, List<DeviceDisplayItem>> {
        val grouped =
            groupDevices(devices)
                .filter(predicate)
                .groupBy { it.primary.roomId ?: UNASSIGNED_ROOM_KEY }
        return roomOrder.mapNotNull { key -> grouped[key]?.let { key to it } }.toMap()
    }

    fun allRoomsCollapsed(itemsByRoom: Map<Int, List<DeviceDisplayItem>>): Boolean {
        val keys = itemsByRoom.keys
        return keys.isNotEmpty() && keys.all { it in collapsedRoomIds }
    }
}

class DevicesViewModel(
    /** Which tab this instance backs — Home and Sensors each get their own instance (see
     * [DevicesModule]), and each persists its own "expanded by default" preference under this key
     * so collapsing every room on one tab doesn't change what the other tab shows next time it's
     * opened fresh. */
    private val tab: RoomsDisplayTab,
    private val getRooms: GetRoomsUseCase,
    private val getDevices: GetDevicesUseCase,
    private val sendCommand: SendDeviceCommandUseCase,
    observeDeviceUpdates: ObserveDeviceUpdatesUseCase,
    getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
    private val observeRoomsExpandedByDefault: ObserveRoomsExpandedByDefaultUseCase,
    private val setRoomsExpandedByDefault: SetRoomsExpandedByDefaultUseCase,
    private val observeRoomOrder: ObserveRoomOrderUseCase,
    private val setRoomOrder: SetRoomOrderUseCase,
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
        viewModelScope.launch {
            val persistedOrder = observeRoomOrder().first()
            _uiState.update { it.copy(customRoomOrder = persistedOrder) }
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

            // Logged from the Sensors tab instance only — the Home tab's own DevicesViewModel
            // would otherwise log the exact same devices a second time every refresh.
            if (tab == RoomsDisplayTab.SENSORS) {
                (devicesResult as? DonaResult.Success)?.data?.let { devices ->
                    groupDevices(devices).filter(::isOrphanedActionSensor).forEach { orphan ->
                        Timber.e(
                            "Sensor '%s' (id=%d, room=%s) is named like an action but has no matching " +
                                "device to attach to on the hub — this is expected to always pair up",
                            orphan.primary.name,
                            orphan.primary.id,
                            orphan.primary.roomId,
                        )
                    }
                }
            }

            if (!hasSeededCollapseState) {
                hasSeededCollapseState = true
                val expandedByDefault = observeRoomsExpandedByDefault(tab).first()
                _uiState.update { state ->
                    val keys = state.roomOrder.toSet()
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
        // Local vals, not the item's properties directly — Kotlin can't smart-cast a nullable
        // property to non-null across module boundaries (DeviceDisplayItem lives in core:model).
        val openAction = item.openAction
        val closeAction = item.closeAction
        when {
            primary is Device.Shutter -> onShutterTap(primary)
            openAction != null && closeAction != null ->
                if (isDeviceOpenOrOn(primary)) firePulse(closeAction) else firePulse(openAction)
            openAction != null -> firePulse(openAction)
            closeAction != null -> firePulse(closeAction)
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

    /** Collapses every room shown on this tab if any is currently expanded, otherwise expands
     * every room — a simple binary "expand all / collapse all" toggle for [itemsByRoom] (the
     * calling screen's own [DevicesUiState.homeDisplayItemsByRoom] or
     * [DevicesUiState.sensorDisplayItemsByRoom]). The resulting all-open/all-closed state is
     * persisted as [tab]'s own default for the next time that tab is opened fresh — the other tab
     * keeps its own separately. */
    fun toggleAllRooms(itemsByRoom: Map<Int, List<DeviceDisplayItem>>) {
        val target =
            if (_uiState.value.allRoomsCollapsed(itemsByRoom)) {
                emptySet()
            } else {
                itemsByRoom.keys
            }
        _uiState.update { it.copy(collapsedRoomIds = target) }
        viewModelScope.launch { setRoomsExpandedByDefault(tab, target.isEmpty()) }
    }

    /** Drag-to-reorder for a room/category section header: moves [roomKey] to sit immediately
     * next to [targetRoomKey] in the shared room order, then persists it. Both keys must already
     * be present in [DevicesUiState.roomOrder] (room ids, or [UNASSIGNED_ROOM_KEY]); a stale or
     * unknown key is a no-op. */
    fun onMoveRoom(
        roomKey: Int,
        targetRoomKey: Int,
    ) {
        if (roomKey == targetRoomKey) return
        val currentOrder = _uiState.value.roomOrder
        val fromIndex = currentOrder.indexOf(roomKey)
        val targetIndex = currentOrder.indexOf(targetRoomKey)
        if (fromIndex == -1 || targetIndex == -1) return

        val reordered = currentOrder.toMutableList()
        reordered.removeAt(fromIndex)
        val insertAt = reordered.indexOf(targetRoomKey) + if (fromIndex < targetIndex) 1 else 0
        reordered.add(insertAt, roomKey)

        _uiState.update { it.copy(customRoomOrder = reordered) }
        viewModelScope.launch { setRoomOrder(reordered) }
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
