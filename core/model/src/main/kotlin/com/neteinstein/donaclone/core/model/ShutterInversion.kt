package com.neteinstein.donaclone.core.model

/**
 * Mirrors a shutter position for a physically inverted module: one wired so the hub's
 * `0 = closed / 100 = open` convention (protocol notes §4) ends up backwards on the actual blind.
 * Applied in the data layer so every reader and every command sees the corrected value — see
 * `ShutterInversionRepository` and the "Shutter Fixer" settings screen.
 */
fun invertShutterPercentage(percentage: Int): Int = (100 - percentage).coerceIn(0, 100)
