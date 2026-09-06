package com.neteinstein.donaclone.core.model

/** A hub user account, as returned by `read user` (protocol notes §3.2/§11.4). */
data class User(
    val id: Int,
    val name: String,
    val role: Int = 0,
    val enabled: Boolean = true,
    val remoteAccessible: Boolean = true,
    val hidden: Boolean = false,
)
