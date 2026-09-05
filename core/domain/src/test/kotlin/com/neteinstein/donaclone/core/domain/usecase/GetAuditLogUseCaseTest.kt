package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AuditLogRepository
import com.neteinstein.donaclone.core.model.AuditLogEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAuditLogUseCaseTest {
    private val repository = mockk<AuditLogRepository>()
    private val useCase = GetAuditLogUseCase(repository)

    @Test
    fun `forwards the object id and date range filters to the repository`() =
        runTest {
            val from = Instant.ofEpochSecond(1_690_000_000)
            val to = Instant.ofEpochSecond(1_690_600_000)
            val entries = listOf(AuditLogEntry(id = 1, objectId = 42, date = from, type = null, description = null, userId = null))
            coEvery { repository.getAuditLog(objectId = 42, from = from, to = to) } returns DonaResult.Success(entries)

            val result = useCase(objectId = 42, from = from, to = to)

            assertEquals(DonaResult.Success(entries), result)
            coVerify { repository.getAuditLog(objectId = 42, from = from, to = to) }
        }

    @Test
    fun `defaults to no filters`() =
        runTest {
            coEvery { repository.getAuditLog(objectId = null, from = null, to = null) } returns DonaResult.Success(emptyList())

            useCase()

            coVerify { repository.getAuditLog(objectId = null, from = null, to = null) }
        }
}
