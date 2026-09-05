package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuditLogRepository
import com.neteinstein.donaclone.core.model.AuditLogEntry
import java.time.Instant

class GetAuditLogUseCase(
    private val repository: AuditLogRepository,
) {
    suspend operator fun invoke(
        objectId: Int? = null,
        from: Instant? = null,
        to: Instant? = null,
    ): DonaResult<List<AuditLogEntry>> = repository.getAuditLog(objectId, from, to)
}
