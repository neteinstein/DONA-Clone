package com.neteinstein.donaclone.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.BinaryOutputSwitch
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.designsystem.component.PercentageSlider
import com.neteinstein.donaclone.core.designsystem.component.StatusDot
import com.neteinstein.donaclone.core.model.Device
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceDetailRoute(
    deviceId: Int,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = koinViewModel { parametersOf(deviceId) },
) {
    val uiState by viewModel.uiState.collectAsState()

    DeviceDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::refresh,
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
fun DeviceDetailScreen(
    uiState: DeviceDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    val device = uiState.device
    val category = device?.let { deviceCategoryOf(it) }
    val colors = colorsForCategory(category ?: DeviceCategory.UNKNOWN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.container,
                        titleContentColor = colors.onContainer,
                        navigationIconContentColor = colors.onContainer,
                    ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && device == null -> LoadingState(modifier = Modifier.padding(padding))
            uiState.errorMessage != null && device == null ->
                ErrorState(message = uiState.errorMessage, onRetry = onRetry, modifier = Modifier.padding(padding))
            device == null -> ErrorState(message = "Device not found", modifier = Modifier.padding(padding))
            else ->
                DeviceDetailContent(
                    device = device,
                    roomName = uiState.roomName,
                    category = category ?: DeviceCategory.UNKNOWN,
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
private fun DeviceDetailContent(
    device: Device,
    roomName: String?,
    category: DeviceCategory,
    modifier: Modifier = Modifier,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    val colors = colorsForCategory(category)

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(category),
                contentDescription = null,
                tint = colors.onContainer,
                modifier = Modifier.size(48.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            StatusDot(online = device.online)
            Text(
                text = if (device.online) "Online" else "Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (roomName != null) {
            Text(
                text = "Room: $roomName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (device.description != null) {
            Text(
                text = device.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Box(modifier = Modifier.padding(top = 32.dp).fillMaxWidth()) {
            DeviceActions(
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

@Composable
private fun DeviceActions(
    device: Device,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
) {
    when (device) {
        is Device.BinaryOutput ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (device.isOn) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onToggleBinaryOutput(device) },
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

        is Device.Shutter ->
            Column {
                Text("${device.percentage}% open", style = MaterialTheme.typography.titleMedium)
                PercentageSlider(
                    percentage = device.percentage,
                    onValueChangeFinished = { onShutterPercentage(device, it) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Button(onClick = { onOpenShutter(device) }, enabled = device.percentage < 100) {
                        Text("Open")
                    }
                    Button(onClick = { onCloseShutter(device) }, enabled = device.percentage > 0) {
                        Text("Close")
                    }
                }
            }

        is Device.Dimmer ->
            Column {
                Text("${device.percentage}%", style = MaterialTheme.typography.titleMedium)
                PercentageSlider(
                    percentage = device.percentage,
                    onValueChangeFinished = { onDimmerPercentage(device, it) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        is Device.Pulse ->
            Column {
                Button(onClick = { onFirePulse(device) }) { Text("Trigger") }
                Text(
                    text = "This device reports no persisted state — the hub only ever confirms the moment it was triggered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        is Device.BinaryInput, is Device.AnalogInput, is Device.Counter, is Device.UnknownDevice ->
            Text(
                text = stateLabelFor(device) ?: "This is a sensor; it reports state but has no actions.",
                style = MaterialTheme.typography.titleMedium,
            )
    }
}
