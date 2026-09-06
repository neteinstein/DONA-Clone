package com.neteinstein.donaclone.feature.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceDisplayItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun DevicesRoute(
    onOpenDeviceDetail: (Int) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    DevicesScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onLogout = viewModel::logout,
        onOpenDeviceDetail = onOpenDeviceDetail,
        onToggleBinaryOutput = viewModel::toggleBinaryOutput,
        onFirePulse = viewModel::firePulse,
        onShutterTap = viewModel::onShutterTap,
        onDimmerTap = viewModel::onDimmerTap,
        onGroupedTap = viewModel::onGroupedTap,
        onToggleRoomCollapsed = viewModel::toggleRoomCollapsed,
        onToggleAllRooms = { viewModel.toggleAllRooms(uiState.homeDisplayItemsByRoom) },
        onMoveRoom = viewModel::onMoveRoom,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    uiState: DevicesUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
    onGroupedTap: (DeviceDisplayItem.Grouped) -> Unit,
    onToggleRoomCollapsed: (Int) -> Unit,
    onToggleAllRooms: () -> Unit,
    onMoveRoom: (roomKey: Int, targetRoomKey: Int) -> Unit,
) {
    Scaffold(
        // MainScreen's own Scaffold + bottom nav bar already reserves space for the system
        // navigation bar below this tab — without excluding it here too, this Scaffold (which has
        // no bottomBar of its own) reserves that same inset a second time, leaving a large empty
        // gap between the end of the device grid and the actual bottom nav bar.
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.houseName.ifBlank { "Home" }, style = MaterialTheme.typography.titleLarge)
                        if (uiState.userName.isNotBlank()) {
                            Text(
                                "Signed in as ${uiState.userName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.devices.isEmpty() -> LoadingState(modifier = Modifier.padding(padding))
            uiState.errorMessage != null && uiState.devices.isEmpty() ->
                ErrorState(message = uiState.errorMessage, onRetry = onRefresh, modifier = Modifier.padding(padding))
            uiState.devices.isEmpty() ->
                EmptyState(message = "No devices found on this hub yet.", modifier = Modifier.padding(padding))
            else ->
                DeviceRoomGrid(
                    itemsByRoom = uiState.homeDisplayItemsByRoom,
                    roomsById = uiState.rooms.associateBy { it.id },
                    collapsedRoomIds = uiState.collapsedRoomIds,
                    recentlyFiredDeviceIds = uiState.recentlyFiredDeviceIds,
                    modifier = Modifier.padding(padding),
                    onOpenDeviceDetail = onOpenDeviceDetail,
                    onToggleBinaryOutput = onToggleBinaryOutput,
                    onFirePulse = onFirePulse,
                    onShutterTap = onShutterTap,
                    onDimmerTap = onDimmerTap,
                    onGroupedTap = onGroupedTap,
                    onToggleRoomCollapsed = onToggleRoomCollapsed,
                    onToggleAllRooms = onToggleAllRooms,
                    onMoveRoom = onMoveRoom,
                )
        }
    }
}
