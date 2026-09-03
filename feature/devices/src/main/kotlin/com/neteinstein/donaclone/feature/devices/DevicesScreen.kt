package com.neteinstein.donaclone.feature.devices

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        onToggleAllRooms = viewModel::toggleAllRooms,
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
                    onGroupedTap = onGroupedTap,
                    onToggleRoomCollapsed = onToggleRoomCollapsed,
                    onToggleAllRooms = onToggleAllRooms,
                )
        }
    }
}

@Composable
private fun DeviceGrid(
    uiState: DevicesUiState,
    modifier: Modifier = Modifier,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
    onGroupedTap: (DeviceDisplayItem.Grouped) -> Unit,
    onToggleRoomCollapsed: (Int) -> Unit,
    onToggleAllRooms: () -> Unit,
) {
    val roomsById = uiState.rooms.associateBy { it.id }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onToggleAllRooms) {
                Icon(
                    imageVector = if (uiState.allRoomsCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (uiState.allRoomsCollapsed) "Expand all" else "Collapse all")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            uiState.displayItemsByRoom.forEach { (roomId, itemsInRoom) ->
                val sectionKey = roomId ?: UNASSIGNED_ROOM_KEY
                val isCollapsed = sectionKey in uiState.collapsedRoomIds
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$roomId") {
                    RoomSectionHeader(
                        title = roomsById[roomId]?.name ?: "Unassigned",
                        collapsed = isCollapsed,
                        onToggle = { onToggleRoomCollapsed(sectionKey) },
                    )
                }
                if (!isCollapsed) {
                    items(itemsInRoom, key = { it.primary.id }) { displayItem ->
                        DeviceCell(
                            item = displayItem,
                            recentlyFired = displayItem.primary.id in uiState.recentlyFiredDeviceIds,
                            onOpenDeviceDetail = onOpenDeviceDetail,
                            onToggleBinaryOutput = onToggleBinaryOutput,
                            onFirePulse = onFirePulse,
                            onShutterTap = onShutterTap,
                            onDimmerTap = onDimmerTap,
                            onGroupedTap = onGroupedTap,
                        )
                    }
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
    item: DeviceDisplayItem,
    recentlyFired: Boolean,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
    onGroupedTap: (DeviceDisplayItem.Grouped) -> Unit,
) {
    val device = item.primary
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

    val hasGroupedActions = item is DeviceDisplayItem.Grouped && (item.openAction != null || item.closeAction != null)
    val onClick: () -> Unit =
        if (item is DeviceDisplayItem.Grouped && device !is Device.Shutter && hasGroupedActions) {
            { onGroupedTap(item) }
        } else {
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
        }

    DeviceGridTile(
        name = item.displayName,
        icon = iconFor(device),
        online = device.online,
        visualState = visualState,
        onClick = onClick,
        onLongClick = { onOpenDeviceDetail(device.id) },
    )
}
