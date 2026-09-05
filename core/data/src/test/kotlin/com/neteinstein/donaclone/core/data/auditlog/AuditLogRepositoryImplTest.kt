package com.neteinstein.donaclone.core.data.auditlog

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.MasterLogEntryDto
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AuditLogRepositoryImplTest {
    private val api = mockk<DomotalkApi>()
    private val repository = AuditLogRepositoryImpl(api)

    @Test
    fun `no filters are sent when no object id or date range is given`() =
        runTest {
            coEvery { api.readMasterLog(filters = null) } returns emptyList()

            val result = repository.getAuditLog()

            assertEquals(DonaResult.Success(emptyList<Nothing>()), result)
            coVerify { api.readMasterLog(filters = null) }
        }

    @Test
    fun `builds an equal filter for the object id and greater-lesser filters for the date range`() =
        runTest {
            val from = Instant.ofEpochSecond(1_690_000_000)
            val to = Instant.ofEpochSecond(1_690_600_000)
            val filtersSlot = slot<JsonArray>()
            coEvery { api.readMasterLog(filters = capture(filtersSlot)) } returns emptyList()

            repository.getAuditLog(objectId = 42, from = from, to = to)

            val filters = filtersSlot.captured
            assertEquals(3, filters.size)
            assertEquals("objectId", filters[0].jsonObject["field"]?.jsonPrimitive?.content)
            assertEquals("equal", filters[0].jsonObject["operation"]?.jsonPrimitive?.content)
            assertEquals("42", filters[0].jsonObject["value"]?.jsonPrimitive?.content)
            assertEquals("date", filters[1].jsonObject["field"]?.jsonPrimitive?.content)
            assertEquals("greater", filters[1].jsonObject["operation"]?.jsonPrimitive?.content)
            assertEquals("date", filters[2].jsonObject["field"]?.jsonPrimitive?.content)
            assertEquals("lesser", filters[2].jsonObject["operation"]?.jsonPrimitive?.content)
        }

    @Test
    fun `maps the dto's epoch-second date and passes through the other fields`() =
        runTest {
            val dto = MasterLogEntryDto(id = 1, objectId = 42, date = 1_690_000_000, type = 3, description = "Armed", userId = 7)
            coEvery { api.readMasterLog(filters = null) } returns listOf(dto)

            val result = repository.getAuditLog()

            assertTrue(result is DonaResult.Success)
            val entry = (result as DonaResult.Success).data.single()
            assertEquals(1, entry.id)
            assertEquals(42, entry.objectId)
            assertEquals(Instant.ofEpochSecond(1_690_000_000), entry.date)
            assertEquals(3, entry.type)
            assertEquals("Armed", entry.description)
            assertEquals(7, entry.userId)
        }
}
