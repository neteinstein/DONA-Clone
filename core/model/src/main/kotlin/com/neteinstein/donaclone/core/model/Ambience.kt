package com.neteinstein.donaclone.core.model

/** A scene ("Scenario" in the app's UI copy). Trigger/condition sub-objects are opaque —
 * the hub's format for them was not fully recoverable, see protocol notes. */
data class Ambience(
    val id: Int,
    val name: String,
    val isPlaying: Boolean,
    val enabled: Boolean,
)
