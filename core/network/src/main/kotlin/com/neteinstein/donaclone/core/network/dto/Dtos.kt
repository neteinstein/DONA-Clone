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
