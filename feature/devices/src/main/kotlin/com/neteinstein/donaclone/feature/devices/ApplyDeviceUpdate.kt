package com.neteinstein.donaclone.feature.devices

import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceUpdate

/** Applies a live push [update] to [this] device, or returns it unchanged if the update doesn't
 * target this device or doesn't carry a value this subtype understands. Shared by the Home tab's
 * grid and the device detail screen so both reflect live state the same way. */
internal fun Device.withUpdate(update: DeviceUpdate): Device {
    if (id != update.deviceId) return this
    return when (this) {
        is Device.BinaryOutput -> (update as? DeviceUpdate.BinaryStatus)?.let { copy(isOn = it.isOn) } ?: this
        is Device.BinaryInput -> (update as? DeviceUpdate.BinaryStatus)?.let { copy(isActive = it.isOn) } ?: this
        is Device.Shutter -> (update as? DeviceUpdate.Percentage)?.let { copy(percentage = it.percentage) } ?: this
        is Device.Dimmer -> (update as? DeviceUpdate.Percentage)?.let { copy(percentage = it.percentage) } ?: this
        is Device.AnalogInput -> (update as? DeviceUpdate.NumericValue)?.let { copy(value = it.value) } ?: this
        is Device.Counter -> (update as? DeviceUpdate.NumericValue)?.let { copy(value = it.value) } ?: this
        is Device.Pulse, is Device.UnknownDevice -> this
    }
}
