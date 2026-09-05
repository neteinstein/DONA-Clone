package com.neteinstein.donaclone.core.data.auditlog

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.AuditLogRepository
import com.neteinstein.donaclone.core.model.AuditLogEntry
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.MasterLogEntryDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

class AuditLogRepositoryImpl(
    private val api: DomotalkApi,
) : AuditLogRepository {
    override suspend fun getAuditLog(
        objectId: Int?,
        from: Instant?,
        to: Instant?,
    ): DonaResult<List<AuditLogEntry>> =
        donaResultCatching {
            api.readMasterLog(filters = buildFilters(objectId, from, to)).map { it.toDomain() }
        }

    private fun buildFilters(
        objectId: Int?,
        from: Instant?,
        to: Instant?,
    ): JsonArray? {
        val filters =
            buildJsonArray {
                objectId?.let { add(filter(field = "objectId", operation = "equal", value = it)) }
                from?.let { add(filter(field = "date", operation = "greater", value = it.epochSecond)) }
                to?.let { add(filter(field = "date", operation = "lesser", value = it.epochSecond)) }
            }
        return filters.takeIf { it.isNotEmpty() }
    }

    private fun filter(
        field: String,
        operation: String,
        value: Number,
    ) = buildJsonObject {
        put("field", JsonPrimitive(field))
        put("operation", JsonPrimitive(operation))
        put("value", JsonPrimitive(value))
    }

    private fun MasterLogEntryDto.toDomain() =
        AuditLogEntry(
            id = id,
            objectId = objectId,
            date = Instant.ofEpochSecond(date),
            type = type,
            description = description,
            userId = userId,
        )
}
