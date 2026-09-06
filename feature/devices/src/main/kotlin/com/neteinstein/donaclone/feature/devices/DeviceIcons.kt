package com.neteinstein.donaclone.feature.devices

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.PulseKind
import com.neteinstein.donaclone.core.model.shutterStateLabel

/**
 * A best-effort classification of a device, driving both its icon (this file) and its detail
 * screen theming ([colorsForCategory]). Interaction behaviour (what a tap/long-press does) is
 * driven purely by the [Device] sealed subtype, never by this category — the category only ever
 * picks icon/label/color, matching the hub's own freeTypeLabel being an independent, best-effort
 * hint rather than a wire-protocol type.
 */
enum class DeviceCategory {
    LIGHT,
    DIMMER,
    SHUTTER,
    GARAGE_DOOR,
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
 * where available.
 *
 * In practice the hub almost never sends [Device.freeTypeLabel] as a string (its `type` key is
 * usually the numeric [com.neteinstein.donaclone.core.model.DpuDeviceCode] instead — see
 * [com.neteinstein.donaclone.core.network.mapper.DeviceJsonMapper]), which otherwise left every
 * ambiguous deviceOut (an on/off relay could be a light, a garage door opener, anything) and every
 * deviceIn sensor showing the same catch-all icon. So when there's no free-text label at all, this
 * falls back to matching the same keywords against the device's own (user-assigned) [Device.name]
 * before finally falling back to the concrete [Device] subtype — a name is a much weaker signal
 * than a hub-provided type, so it's only consulted when the hub gave nothing better.
 */
fun deviceCategoryOf(device: Device): DeviceCategory {
    val freeLabel = device.freeTypeLabel?.lowercase().orEmpty()
    categoryForLabel(freeLabel)?.let { return it }
    if (freeLabel.isNotBlank()) return DeviceCategory.GENERIC_SENSOR

    categoryForLabel(device.name.lowercase())?.let { return it }

    return when (device) {
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

/**
 * Keyword table shared by both the hub's free-text type label and (as a weaker fallback) the
 * device's own name — English and Portuguese, matching the hub's observed bilingual naming (see
 * the prefix lists in `DeviceGrouping.kt`). Order matters: more specific keywords (e.g. "garagem")
 * must be checked before broader ones they can co-occur with ("portão da garagem" contains both
 * "garagem" and "portão", and should read as a garage door, not a generic gate).
 */
private fun categoryForLabel(label: String): DeviceCategory? =
    when {
        "light" in label || "luz" in label || "lâmpada" in label || "lampada" in label -> DeviceCategory.LIGHT
        "dimmer" in label -> DeviceCategory.DIMMER
        "garage" in label || "garagem" in label -> DeviceCategory.GARAGE_DOOR
        "shutter" in label || "blind" in label || "persiana" in label || "estore" in label -> DeviceCategory.SHUTTER
        "valve" in label || "torneira" in label -> DeviceCategory.VALVE
        "lock" in label || "fechadura" in label || "cadeado" in label -> DeviceCategory.LOCK
        "flood" in label || "water" in label || "água" in label || "agua" in label -> DeviceCategory.FLOOD_SENSOR
        "crepuscular" in label || "dusk" in label || "twilight" in label -> DeviceCategory.CREPUSCULAR_SENSOR
        "gate" in label || "portão" in label || "portao" in label -> DeviceCategory.GATE_SENSOR
        "door" in label || "porta" in label -> DeviceCategory.DOOR_SENSOR
        "siren" in label || "sirene" in label -> DeviceCategory.SIREN
        "chime" in label || "campainha" in label -> DeviceCategory.CHIME
        "outlet" in label || "tomada" in label -> DeviceCategory.OUTLET
        "temperature" in label || "temperatura" in label -> DeviceCategory.TEMPERATURE
        "meter" in label || "medidor" in label -> DeviceCategory.METER
        else -> null
    }

fun iconFor(device: Device): ImageVector = iconFor(deviceCategoryOf(device))

fun iconFor(category: DeviceCategory): ImageVector =
    when (category) {
        DeviceCategory.LIGHT -> Icons.Filled.Lightbulb
        DeviceCategory.DIMMER -> Icons.Filled.WbSunny
        DeviceCategory.SHUTTER -> Icons.Filled.Blinds
        DeviceCategory.GARAGE_DOOR -> Icons.Filled.Garage
        DeviceCategory.LOCK -> Icons.Filled.DoorFront
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
        is Device.Shutter -> shutterStateLabel(device.percentage)
        is Device.Dimmer -> "${device.percentage}%"
        is Device.BinaryInput -> if (device.isActive) "Active" else "Idle"
        is Device.AnalogInput -> device.value.toString()
        is Device.Counter -> device.value.toString()
        is Device.UnknownDevice -> "Unsupported device"
    }
