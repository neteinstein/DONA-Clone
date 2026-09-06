package com.neteinstein.donaclone.core.model

/** A hub user account, as returned by `read user`. */
data class User(
    val id: Int,
    val name: String,
)
