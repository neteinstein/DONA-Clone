@file:OptIn(ExperimentalFoundationApi::class)

package com.neteinstein.donaclone.feature.devices

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.designsystem.component.PercentageSlider
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.shutterStateLabel
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
    var infoDevice by remember { mutableStateOf<Device?>(null) }
    var pendingCloseShutter by remember { mutableStateOf<Device.Shutter?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((uiState.displayName ?: device?.name).orEmpty()) },
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
                    secondaries = uiState.secondaryDevices,
                    openAction = uiState.openAction,
                    closeAction = uiState.closeAction,
                    modifier = Modifier.padding(padding),
                    onToggleBinaryOutput = onToggleBinaryOutput,
                    onFirePulse = onFirePulse,
                    onOpenShutter = onOpenShutter,
                    onCloseShutter = { shutter ->
                        if (uiState.actionConfirmationEnabled) pendingCloseShutter = shutter else onCloseShutter(shutter)
                    },
                    onShutterPercentage = onShutterPercentage,
                    onDimmerPercentage = onDimmerPercentage,
                    onShowInfo = { infoDevice = it },
                )
        }
    }

    infoDevice?.let { info ->
        DeviceInfoDialog(device = info, onDismiss = { infoDevice = null })
    }

    pendingCloseShutter?.let { shutter ->
        CloseShutterConfirmationDialog(
            onConfirm = {
                onCloseShutter(shutter)
                pendingCloseShutter = null
            },
            onDismiss = { pendingCloseShutter = null },
        )
    }
}

@Composable
private fun CloseShutterConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close blind?") },
        text = { Text("Are you sure there is nothing preventing it from closing, such as an obstruction in its path?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Close") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeviceDetailContent(
    device: Device,
    roomName: String?,
    category: DeviceCategory,
    secondaries: List<Device>,
    openAction: Device.Pulse?,
    closeAction: Device.Pulse?,
    modifier: Modifier = Modifier,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
    onShowInfo: (Device) -> Unit,
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

        Text(
            text = if (device.online) "Online" else "Offline — controls disabled",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )

        if (roomName != null) {
            Text(
                text = "Room: $roomName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        val description = device.description
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Box(modifier = Modifier.padding(top = 32.dp).fillMaxWidth()) {
            DeviceActions(
                device = device,
                secondaries = secondaries,
                openAction = openAction,
                closeAction = closeAction,
                onToggleBinaryOutput = onToggleBinaryOutput,
                onFirePulse = onFirePulse,
                onOpenShutter = onOpenShutter,
                onCloseShutter = onCloseShutter,
                onShutterPercentage = onShutterPercentage,
                onDimmerPercentage = onDimmerPercentage,
                onShowInfo = onShowInfo,
            )
        }
    }
}

@Composable
private fun DeviceActions(
    device: Device,
    secondaries: List<Device>,
    openAction: Device.Pulse?,
    closeAction: Device.Pulse?,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
    onShowInfo: (Device) -> Unit,
) {
    val enabled = device.online

    Column {
        DeviceActionsContent(
            device = device,
            enabled = enabled,
            onToggleBinaryOutput = onToggleBinaryOutput,
            onFirePulse = onFirePulse,
            onOpenShutter = onOpenShutter,
            onCloseShutter = onCloseShutter,
            onShutterPercentage = onShutterPercentage,
            onDimmerPercentage = onDimmerPercentage,
            onShowInfo = onShowInfo,
        )

        // A native shutter already has its own open/close/percentage controls above — any
        // action-role siblings it was merged with are redundant and deliberately ignored here.
        if (device !is Device.Shutter && (openAction != null || closeAction != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                if (openAction != null) {
                    ActionButton(
                        text = "Open",
                        enabled = enabled,
                        onClick = { onFirePulse(openAction) },
                        onLongClick = { onShowInfo(openAction) },
                    )
                }
                if (closeAction != null) {
                    ActionButton(
                        text = "Close",
                        enabled = enabled,
                        onClick = { onFirePulse(closeAction) },
                        onLongClick = { onShowInfo(closeAction) },
                    )
                }
            }
        }

        secondaries.forEach { secondary ->
            Text(
                text = "Also reports: ${secondary.name} — ${stateLabelFor(secondary)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .combinedClickable(onClick = {}, onLongClick = { onShowInfo(secondary) }),
            )
        }
    }
}

@Composable
private fun DeviceActionsContent(
    device: Device,
    enabled: Boolean,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onOpenShutter: (Device.Shutter) -> Unit,
    onCloseShutter: (Device.Shutter) -> Unit,
    onShutterPercentage: (Device.Shutter, Int) -> Unit,
    onDimmerPercentage: (Device.Dimmer, Int) -> Unit,
    onShowInfo: (Device) -> Unit,
) {
    when (device) {
        is Device.BinaryOutput ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { onToggleBinaryOutput(device) },
                        onLongClick = { onShowInfo(device) },
                    ),
            ) {
                Text(if (device.isOn) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = device.isOn,
                    onCheckedChange = null,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

        is Device.Shutter ->
            Column {
                Text(
                    text = shutterStateLabel(device.percentage),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onShowInfo(device) }),
                )
                PercentageSlider(
                    percentage = device.percentage,
                    onValueChangeFinished = { onShutterPercentage(device, it) },
                    enabled = enabled,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    ActionButton(
                        text = "Open",
                        enabled = enabled && device.percentage < 100,
                        onClick = { onOpenShutter(device) },
                        onLongClick = { onShowInfo(device) },
                    )
                    ActionButton(
                        text = "Close",
                        enabled = enabled && device.percentage > 0,
                        onClick = { onCloseShutter(device) },
                        onLongClick = { onShowInfo(device) },
                    )
                }
            }

        is Device.Dimmer ->
            Column {
                Text(
                    text = "${device.percentage}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onShowInfo(device) }),
                )
                PercentageSlider(
                    percentage = device.percentage,
                    onValueChangeFinished = { onDimmerPercentage(device, it) },
                    enabled = enabled,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        is Device.Pulse ->
            Column {
                ActionButton(
                    text = "Trigger",
                    enabled = enabled,
                    onClick = { onFirePulse(device) },
                    onLongClick = { onShowInfo(device) },
                )
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
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onShowInfo(device) }),
            )
    }
}

/** A button-styled control that additionally reports long-presses, for the info dialog. */
@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        shape = ButtonDefaults.shape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

/** Human label for a [Device] subtype, used by [DeviceInfoDialog]. */
private fun deviceTypeLabel(device: Device): String =
    when (device) {
        is Device.BinaryOutput -> "Binary Output"
        is Device.Pulse -> "Pulse Output"
        is Device.Shutter -> "Shutter"
        is Device.Dimmer -> "Dimmer"
        is Device.BinaryInput -> "Binary Input Sensor"
        is Device.AnalogInput -> "Analog Sensor"
        is Device.Counter -> "Counter Sensor"
        is Device.UnknownDevice -> "Unknown Device"
    }

/** Every field the app has for [device], flattened into label/value rows for [DeviceInfoDialog]. */
private fun deviceInfoRows(device: Device): List<Pair<String, String>> =
    buildList {
        add("Type" to deviceTypeLabel(device))
        add("ID" to device.id.toString())
        add("Name" to device.name)
        device.description?.let { add("Description" to it) }
        device.freeTypeLabel?.let { add("Category label" to it) }
        device.roomId?.let { add("Room ID" to it.toString()) }
        add("Enabled" to device.enabled.toString())
        add("Online" to device.online.toString())
        when (device) {
            is Device.BinaryOutput -> add("State" to if (device.isOn) "On" else "Off")
            is Device.Pulse -> {
                add("Pulse kind" to device.kind.name)
                device.durationSeconds?.let { add("Duration (s)" to it.toString()) }
            }
            is Device.Shutter -> add("Position" to "${device.percentage}%")
            is Device.Dimmer -> add("Brightness" to "${device.percentage}%")
            is Device.BinaryInput -> add("State" to if (device.isActive) "Active" else "Idle")
            is Device.AnalogInput -> add("Value" to device.value.toString())
            is Device.Counter -> add("Value" to device.value.toString())
            is Device.UnknownDevice -> device.rawTypeCode?.let { add("Raw type code" to it.toString()) }
        }
    }

@Composable
private fun DeviceInfoDialog(
    device: Device,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(device.name) },
        text = {
            Column {
                deviceInfoRows(device).forEach { (label, value) ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}
