package com.neteinstein.donaclone.feature.devices

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.BinaryOutputSwitch
import com.neteinstein.donaclone.core.designsystem.component.DeviceTile
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.designsystem.component.PercentageSlider
import com.neteinstein.donaclone.core.model.Device
import org.koin.androidx.compose.koinViewModel

@Composable
fun DevicesRoute(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DevicesScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggleBinaryOutput = viewModel::toggleBinaryOutput,
        onFirePulse = viewModel::firePulse,
        onOpenShutter = viewModel::openShutter,
        onCloseShutter = viewModel::closeShutter,
        onShutterPercentage = viewModel::setShutterPercentage,
        onDimmerPercentage = viewModel::setDimmerPercentage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    uiState: DevicesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rooms & Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
            else -> DeviceList(
                uiState = uiState,
                modifier = Modifier.padding(padding),
                onToggleBinaryOutput = onToggleBinaryOutput,
                onFirePulse = onFirePulse,
                onOpenShutter = onOpenShutter,
                onCloseShutter = onCloseShutter,
                onShutterPercentage = onShutterPercentage,
                onDimmerPercentage = onDimmerPercentage,
            )
        }
    }
}

@Composable
private fun DeviceList(
    uiState: DevicesUiState,
    modifier: Modifier = Modifier,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    val roomsById = uiState.rooms.associateBy { it.id }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        uiState.devicesByRoom.forEach { (roomId, devicesInRoom) ->
            item(key = "header-$roomId") {
                Text(
                    text = roomsById[roomId]?.name ?: "Unassigned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(devicesInRoom, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    onToggleBinaryOutput = onToggleBinaryOutput,
                    onFirePulse = onFirePulse,
                    onOpenShutter = onOpenShutter,
                    onCloseShutter = onCloseShutter,
                    onShutterPercentage = onShutterPercentage,
                    onDimmerPercentage = onDimmerPercentage,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    when (device) {
        is Device.BinaryOutput -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
        ) {
            BinaryOutputSwitch(isOn = device.isOn, onToggle = { onToggleBinaryOutput(device) })
        }

        is Device.Pulse -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            onClick = { onFirePulse(device) },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Trigger")
        }

        is Device.Shutter -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            subtitle = "${device.percentage}% open",
        ) {
            PercentageSlider(
                percentage = device.percentage,
                onValueChangeFinished = { onShutterPercentage(device, it) },
            )
        }

        is Device.Dimmer -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
        ) {
            PercentageSlider(
                percentage = device.percentage,
                onValueChangeFinished = { onDimmerPercentage(device, it) },
            )
        }

        is Device.BinaryInput -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            subtitle = if (device.isActive) "Active" else "Idle",
        ) {}

        is Device.AnalogInput -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            subtitle = device.value.toString(),
        ) {}

        is Device.Counter -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            subtitle = device.value.toString(),
        ) {}

        is Device.UnknownDevice -> DeviceTile(
            name = device.name,
            icon = iconFor(device),
            online = device.online,
            subtitle = "Unsupported device",
        ) {}
    }
}
