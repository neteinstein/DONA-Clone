package com.neteinstein.donaclone.core.model

/**
 * A configured DPU/hub connection profile. Mirrors the original app's `Home` Room entity
 * (one row per site the phone knows about) so multi-house setups keep working the same way.
 */
data class House(
    val name: String,
    val dns: String? = null,
    val secureDns: Boolean = true,
    val localIp: String? = null,
    val secureLocalIp: Boolean = false,
    val username: String = "",
    val password: String = "",
    val stayConnected: Boolean = true,
    val notificationId: String? = null,
    val codeOnDisarmAlarm: Boolean = false,
)

/** A hub announcement received in reply to a LAN `domobroadcast` discovery probe. */
data class DiscoveredHouse(
    val mac: String,
    val ip: String,
    val gateway: String?,
    val subnetMask: String?,
    val dhcp: Boolean,
    val hubType: HubType,
    val serialNumber: String?,
    val hardwareVersion: String?,
    val firmwareVersion: String?,
)

enum class HubType {
    DPU,
    D815,
    D808,
    WIFI_SHUTTER,
    WIFI_LIGHT,
    WIFI_OUTLET,
    UNKNOWN,
}
