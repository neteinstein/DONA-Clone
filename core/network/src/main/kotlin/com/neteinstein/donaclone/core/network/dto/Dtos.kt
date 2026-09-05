package com.neteinstein.donaclone.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val role: Int = 0,
    val hidden: Boolean = false,
    val photoUri: String? = null,
    val name: String = "",
    val remoteAccessible: Boolean = true,
    val house: Int? = null,
    val enabled: Boolean = true,
)

@Serializable
data class SessionDto(
    val token: String,
)

@Serializable
data class DivisionDto(
    val id: Int,
    val name: String = "",
    val floor: Int? = null,
)

@Serializable
data class AmbienceDto(
    val id: Int,
    val name: String = "",
    val isPlaying: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * One `masterLog` entry (`v4/f.java:76-99`). The hub's exact field list beyond the
 * `objectId`/`date` pair used for filtering (§2.4) was not recoverable from decompiled code,
 * so everything past those two is read defensively as nullable.
 */
@Serializable
data class MasterLogEntryDto(
    val id: Int = 0,
    val objectId: Int? = null,
    val date: Long = 0,
    val type: Int? = null,
    val description: String? = null,
    val userId: Int? = null,
)
