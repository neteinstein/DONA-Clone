package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Which shutters on the active house are physically wired backwards, so their reported position
 * and their open/close commands must be mirrored (see
 * [com.neteinstein.donaclone.core.model.invertShutterPercentage]). Scoped to the active house
 * because device ids are only unique within one hub.
 */
interface ShutterInversionRepository {
    /** Inverted device ids for whatever house is currently active; empty when there is none. */
    fun observeInvertedShutterIds(): Flow<Set<Int>>

    suspend fun setShutterInverted(
        deviceId: Int,
        inverted: Boolean,
    )
}
