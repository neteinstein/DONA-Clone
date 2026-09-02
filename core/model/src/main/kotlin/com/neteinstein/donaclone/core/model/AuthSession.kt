package com.neteinstein.donaclone.core.model

data class AuthSession(
    val token: String,
    val userId: Int,
    val userName: String,
    val houseName: String,
)

enum class SessionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

data class DonaUser(
    val id: Int,
    val name: String,
    val role: Int,
    val hidden: Boolean,
    val enabled: Boolean,
    val remoteAccessible: Boolean,
    val photoUri: String?,
)
