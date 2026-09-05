package com.neteinstein.donaclone.core.model

import java.time.Instant

/** One entry from the hub's event/audit log (`masterLog`), filterable by [objectId] and a
 * [date] range. The entry's fields beyond that pair are UNCONFIRMED (see protocol notes),
 * so [type]/[description]/[userId] are read defensively as nullable. */
data class AuditLogEntry(
    val id: Int,
    val objectId: Int?,
    val date: Instant,
    val type: Int?,
    val description: String?,
    val userId: Int?,
)
