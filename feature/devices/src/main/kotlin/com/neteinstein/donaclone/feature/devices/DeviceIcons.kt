package com.neteinstein.donaclone.feature.devices

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.PulseKind

/**
 * A best-effort classification of a device, driving both its icon (this file) and its detail
 * screen theming ([DeviceCategoryTheme]). Interaction behaviour (what a tap/long-press does) is
 * driven purely by the [Device] sealed subtype, never by this category — the category only ever
 * picks icon/label/color, matching the hub's own freeTypeLabel being an independent, best-effort
 * hint rather than a wire-protocol type.
 */
enum class DeviceCategory {
    LIGHT,
    DIMMER,
    SHUTTER,
    LOCK,
    VALVE,
    FLOOD_SENSOR,
    DOOR_SENSOR,
    GATE_SENSOR,
    CREPUSCULAR_SENSOR,
    SIREN,
    CHIME,
    OUTLET,
    TEMPERATURE,
    METER,
    GENERIC_SENSOR,
    UNKNOWN,
}

/**
 * Best-effort category per device. The hub's own icon-mapping switch (`p4.h.b()`) wasn't fully
 * recovered from the decompiled app, so this keys off the device's free-text category label
 * where available, falling back to the concrete [Device] subtype.
 */
fun deviceCategoryOf(device: Device): DeviceCategory {
    val label = device.freeTypeLabel?.lowercase().orEmpty()

    return when {
        "light" in label -> DeviceCategory.LIGHT
        "dimmer" in label -> DeviceCategory.DIMMER
        "shutter" in label -> DeviceCategory.SHUTTER
        "valve" in label -> DeviceCategory.VALVE
        "lock" in label -> DeviceCategory.LOCK
        "flood" in label -> DeviceCategory.FLOOD_SENSOR
        "crepuscular" in label || "dusk" in label || "twilight" in label -> DeviceCategory.CREPUSCULAR_SENSOR
        "gate" in label -> DeviceCategory.GATE_SENSOR
        "door" in label -> DeviceCategory.DOOR_SENSOR
        "siren" in label -> DeviceCategory.SIREN
        "chime" in label -> DeviceCategory.CHIME
        "outlet" in label -> DeviceCategory.OUTLET
        "temperature" in label -> DeviceCategory.TEMPERATURE
        "meter" in label -> DeviceCategory.METER
        label.isNotBlank() -> DeviceCategory.GENERIC_SENSOR
        else ->
            when (device) {
                is Device.BinaryOutput -> DeviceCategory.OUTLET
                is Device.Dimmer -> DeviceCategory.DIMMER
                is Device.Shutter -> DeviceCategory.SHUTTER
                is Device.Pulse ->
                    when (device.kind) {
                        PulseKind.SIREN -> DeviceCategory.SIREN
                        PulseKind.CHIME -> DeviceCategory.CHIME
                        PulseKind.LOCK -> DeviceCategory.LOCK
                        else -> DeviceCategory.OUTLET
                    }
                is Device.BinaryInput -> DeviceCategory.GENERIC_SENSOR
                is Device.AnalogInput, is Device.Counter -> DeviceCategory.GENERIC_SENSOR
                is Device.UnknownDevice -> DeviceCategory.UNKNOWN
            }
    }
}

fun iconFor(device: Device): ImageVector = iconFor(deviceCategoryOf(device))

fun iconFor(category: DeviceCategory): ImageVector =
    when (category) {
        DeviceCategory.LIGHT -> Icons.Filled.WbIncandescent
        DeviceCategory.DIMMER -> Icons.Filled.WbSunny
        DeviceCategory.SHUTTER -> Icons.Filled.VerticalAlignCenter
        DeviceCategory.LOCK -> Icons.Filled.Lock
        DeviceCategory.VALVE -> Icons.Filled.Plumbing
        DeviceCategory.FLOOD_SENSOR -> Icons.Filled.WaterDrop
        DeviceCategory.DOOR_SENSOR -> Icons.Filled.DoorFront
        DeviceCategory.GATE_SENSOR -> Icons.Filled.Fence
        DeviceCategory.CREPUSCULAR_SENSOR -> Icons.Filled.WbTwilight
        DeviceCategory.SIREN -> Icons.Filled.NotificationsActive
        DeviceCategory.CHIME -> Icons.Filled.Notifications
        DeviceCategory.OUTLET -> Icons.Filled.Power
        DeviceCategory.TEMPERATURE -> Icons.Filled.Thermostat
        DeviceCategory.METER -> Icons.Filled.Bolt
        DeviceCategory.GENERIC_SENSOR -> Icons.Filled.Sensors
        DeviceCategory.UNKNOWN -> Icons.Filled.DeviceUnknown
    }

/** A short, human string for whatever value the device is currently reporting. */
fun stateLabelFor(device: Device): String? =
    when (device) {
        is Device.BinaryOutput -> if (device.isOn) "On" else "Off"
        is Device.Pulse -> null
        is Device.Shutter -> "${device.percentage}% open"
        is Device.Dimmer -> "${device.percentage}%"
        is Device.BinaryInput -> if (device.isActive) "Active" else "Idle"
        is Device.AnalogInput -> device.value.toString()
        is Device.Counter -> device.value.toString()
        is Device.UnknownDevice -> "Unsupported device"
    }
