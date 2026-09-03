package com.neteinstein.donaclone.feature.devices

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.DeviceGridTile
import com.neteinstein.donaclone.core.designsystem.component.DeviceTileVisualState
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.Device
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
) {
    Scaffold(
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
                DeviceGrid(
                    uiState = uiState,
                    modifier = Modifier.padding(padding),
                    onOpenDeviceDetail = onOpenDeviceDetail,
                    onToggleBinaryOutput = onToggleBinaryOutput,
                    onFirePulse = onFirePulse,
                    onShutterTap = onShutterTap,
                    onDimmerTap = onDimmerTap,
                )
        }
    }
}

/** Sentinel standing in for a null [Device.roomId] ("Unassigned") in the collapsed-set below —
 * real room ids come from the hub and are never negative. */
private const val UNASSIGNED_ROOM_KEY = Int.MIN_VALUE

@Composable
private fun DeviceGrid(
    uiState: DevicesUiState,
    modifier: Modifier = Modifier,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
) {
    val roomsById = uiState.rooms.associateBy { it.id }
    var collapsedRoomIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        uiState.devicesByRoom.forEach { (roomId, devicesInRoom) ->
            val sectionKey = roomId ?: UNASSIGNED_ROOM_KEY
            val isCollapsed = sectionKey in collapsedRoomIds
            item(span = { GridItemSpan(maxLineSpan) }, key = "header-$roomId") {
                RoomSectionHeader(
                    title = roomsById[roomId]?.name ?: "Unassigned",
                    collapsed = isCollapsed,
                    onToggle = {
                        collapsedRoomIds =
                            if (isCollapsed) collapsedRoomIds - sectionKey else collapsedRoomIds + sectionKey
                    },
                )
            }
            if (!isCollapsed) {
                items(devicesInRoom, key = { it.id }) { device ->
                    DeviceCell(
                        device = device,
                        recentlyFired = device.id in uiState.recentlyFiredDeviceIds,
                        onOpenDeviceDetail = onOpenDeviceDetail,
                        onToggleBinaryOutput = onToggleBinaryOutput,
                        onFirePulse = onFirePulse,
                        onShutterTap = onShutterTap,
                        onDimmerTap = onDimmerTap,
                    )
                }
            }
        }
    }
}

/** A large, full-width tap target so collapsing a room is easy to hit — bigger type than a plain
 * section label, since this is also the room's primary click affordance, not just a heading. */
@Composable
private fun RoomSectionHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(targetValue = if (collapsed) -90f else 0f, label = "room-chevron")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (collapsed) "Expand $title" else "Collapse $title",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(chevronRotation),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun DeviceCell(
    device: Device,
    recentlyFired: Boolean,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
) {
    val visualState: DeviceTileVisualState =
        when (device) {
            is Device.BinaryOutput -> DeviceTileVisualState.Toggle(device.isOn)
            is Device.Pulse -> DeviceTileVisualState.Toggle(recentlyFired)
            is Device.Shutter -> DeviceTileVisualState.FillLevel(device.percentage)
            // Lights only ever show On/Off on the Home tab — Google Home style, no separate
            // brightness readout cluttering the tile; the exact level lives in the detail screen.
            is Device.Dimmer -> DeviceTileVisualState.FillLevel(device.percentage, showPercentageLabel = false)
            else -> DeviceTileVisualState.ReadOnly(stateLabelFor(device))
        }
    val onClick: () -> Unit =
        when (device) {
            is Device.BinaryOutput -> {
                { onToggleBinaryOutput(device) }
            }
            is Device.Pulse -> {
                { onFirePulse(device) }
            }
            is Device.Shutter -> {
                { onShutterTap(device) }
            }
            is Device.Dimmer -> {
                { onDimmerTap(device) }
            }
            else -> {
                {}
            }
        }

    DeviceGridTile(
        name = device.name,
        icon = iconFor(device),
        online = device.online,
        visualState = visualState,
        onClick = onClick,
        onLongClick = { onOpenDeviceDetail(device.id) },
    )
}
