package com.neteinstein.donaclone.core.model

/**
 * A live, unsolicited state change pushed by the hub over the WebSocket for a single device
 * (someone flipped a physical switch, a sensor tripped, a shutter finished moving...).
 * The exact envelope this is parsed from is UNCONFIRMED against a real hub — see protocol
 * notes — but the per-type value it carries is high confidence.
 */
sealed interface DeviceUpdate {
    val deviceId: Int

    data class BinaryStatus(override val deviceId: Int, val isOn: Boolean) : DeviceUpdate
    data class Percentage(override val deviceId: Int, val percentage: Int) : DeviceUpdate
    data class NumericValue(override val deviceId: Int, val value: Double) : DeviceUpdate
}

data class MasterLogEntry(
    val objectId: Int,
    val message: String,
    val epochSeconds: Long,
)
