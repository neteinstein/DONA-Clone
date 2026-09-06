package com.neteinstein.donaclone.core.model

/** A named user role, as returned by `read role` (protocol notes §11.4) — e.g. "Administrator",
 * "Installer", "User" on a stock hub, though names are hub-configurable so they're always read
 * live rather than hardcoded. */
data class Role(
    val id: Int,
    val name: String,
)
