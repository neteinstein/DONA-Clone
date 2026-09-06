package com.neteinstein.donaclone.feature.devices

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Sensors tab: every read-only sensor with no tap action of its own (a door contact, a
 * humidity reading, a pulse counter, ...) that the Home tab excludes — see
 * [DevicesUiState.sensorDisplayItemsByRoom]. Uses the same [DevicesViewModel] class as the Home
 * tab, but each tab resolves its own `koinViewModel()` instance, scoped to [RoomsDisplayTab.SENSORS]
 * — same as every other tab in [com.neteinstein.donaclone.navigation.MainScreen] — so the two tabs
 * independently refresh devices/rooms, collapse/expand rooms, and persist their own
 * expanded-by-default default, rather than sharing one in-memory copy or preference.
 */
@Composable
fun SensorsRoute(
    onOpenDeviceDetail: (Int) -> Unit,
    viewModel: DevicesViewModel = koinViewModel { parametersOf(RoomsDisplayTab.SENSORS) },
) {
    val uiState by viewModel.uiState.collectAsState()

    SensorsScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onOpenDeviceDetail = onOpenDeviceDetail,
        onToggleRoomCollapsed = viewModel::toggleRoomCollapsed,
        onToggleAllRooms = { viewModel.toggleAllRooms(uiState.sensorDisplayItemsByRoom) },
        onMoveRoom = viewModel::onMoveRoom,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(
    uiState: DevicesUiState,
    onRefresh: () -> Unit,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleRoomCollapsed: (Int) -> Unit,
    onToggleAllRooms: () -> Unit,
    onMoveRoom: (roomKey: Int, targetRoomKey: Int) -> Unit,
) {
    Scaffold(
        // MainScreen's own Scaffold + bottom nav bar already reserves space for the system
        // navigation bar below this tab — without excluding it here too, this Scaffold (which has
        // no bottomBar of its own) reserves that same inset a second time, leaving a large empty
        // gap between the end of the grid and the actual bottom nav bar (mirrors the same fix on
        // DevicesScreen).
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                title = { Text("Sensors", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        val sensorItems = uiState.sensorDisplayItemsByRoom
        when {
            uiState.isLoading && uiState.devices.isEmpty() -> LoadingState(modifier = Modifier.padding(padding))
            uiState.errorMessage != null && uiState.devices.isEmpty() ->
                ErrorState(message = uiState.errorMessage, onRetry = onRefresh, modifier = Modifier.padding(padding))
            sensorItems.isEmpty() ->
                EmptyState(message = "No sensor-only devices on this hub yet.", modifier = Modifier.padding(padding))
            else ->
                DeviceRoomGrid(
                    itemsByRoom = sensorItems,
                    roomsById = uiState.rooms.associateBy { it.id },
                    collapsedRoomIds = uiState.collapsedRoomIds,
                    recentlyFiredDeviceIds = uiState.recentlyFiredDeviceIds,
                    modifier = Modifier.padding(padding),
                    onOpenDeviceDetail = onOpenDeviceDetail,
                    onToggleBinaryOutput = {},
                    onFirePulse = {},
                    onShutterTap = {},
                    onDimmerTap = {},
                    onGroupedTap = {},
                    onToggleRoomCollapsed = onToggleRoomCollapsed,
                    onToggleAllRooms = onToggleAllRooms,
                    onMoveRoom = onMoveRoom,
                )
        }
    }
}
