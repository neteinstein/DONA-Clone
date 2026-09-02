package com.neteinstein.donaclone.core.model

/** A room/division, called "Divisions" in the original DPU firmware and "Rooms" in the app UI. */
data class Division(
    val id: Int,
    val name: String,
    val floor: Int?,
)
