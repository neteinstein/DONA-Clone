package com.neteinstein.donaclone.feature.devices

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.PulseKind

/**
 * Best-effort icon per device. The hub's own icon-mapping switch (`p4.h.b()`) wasn't fully
 * recovered from the decompiled app, so this keys off the device's free-text category label
 * where available, falling back to the concrete [Device] subtype.
 */
fun iconFor(device: Device): ImageVector {
    val label = device.freeTypeLabel?.lowercase().orEmpty()

    return when {
        "light" in label -> Icons.Filled.WbIncandescent
        "dimmer" in label -> Icons.Filled.WbSunny
        "shutter" in label -> Icons.Filled.VerticalAlignCenter
        "lock" in label -> Icons.Filled.Lock
        "siren" in label -> Icons.Filled.NotificationsActive
        "chime" in label -> Icons.Filled.Notifications
        "outlet" in label -> Icons.Filled.Power
        "temperature" in label -> Icons.Filled.Thermostat
        "meter" in label -> Icons.Filled.Bolt
        label.isNotBlank() -> Icons.Filled.Sensors
        else -> when (device) {
            is Device.BinaryOutput -> Icons.Filled.Power
            is Device.Dimmer -> Icons.Filled.WbSunny
            is Device.Shutter -> Icons.Filled.VerticalAlignCenter
            is Device.Pulse -> when (device.kind) {
                PulseKind.SIREN -> Icons.Filled.NotificationsActive
                PulseKind.CHIME -> Icons.Filled.Notifications
                PulseKind.LOCK -> Icons.Filled.Lock
                else -> Icons.Filled.Bolt
            }
            is Device.BinaryInput -> Icons.Filled.Sensors
            is Device.AnalogInput, is Device.Counter -> Icons.Filled.TrendingUp
            is Device.UnknownDevice -> Icons.Filled.DeviceUnknown
        }
    }
}
