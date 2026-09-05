package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.AuditLogEntry
import java.time.Instant

interface AuditLogRepository {
    suspend fun getAuditLog(
        objectId: Int? = null,
        from: Instant? = null,
        to: Instant? = null,
    ): DonaResult<List<AuditLogEntry>>
}
