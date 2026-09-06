package com.neteinstein.donaclone.feature.settings

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetAuditLogUseCase
import com.neteinstein.donaclone.core.model.AuditLogEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getAuditLog = mockk<GetAuditLogUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getAuditLog(objectId = null, from = null, to = null) } returns DonaResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the audit log without filters on init`() =
        runTest(dispatcher) {
            val entries = listOf(AuditLogEntry(id = 1, objectId = 1, date = Instant.EPOCH, type = null, description = "Test", userId = null))
            coEvery { getAuditLog(objectId = null, from = null, to = null) } returns DonaResult.Success(entries)

            val viewModel = AuditLogViewModel(getAuditLog)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(entries, viewModel.uiState.value.entries)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun `an object id filter is parsed to an int and non-digits are stripped`() =
        runTest(dispatcher) {
            val viewModel = AuditLogViewModel(getAuditLog)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onObjectIdFilterChanged("4a2x")

            assertEquals("42", viewModel.uiState.value.objectIdFilter)
        }

    @Test
    fun `refreshing applies the object id and an inclusive date range`() =
        runTest(dispatcher) {
            val from = LocalDate.of(2023, 7, 22)
            val to = LocalDate.of(2023, 7, 29)
            coEvery {
                getAuditLog(
                    objectId = 42,
                    from = from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    to = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                )
            } returns DonaResult.Success(emptyList())
            val viewModel = AuditLogViewModel(getAuditLog)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onObjectIdFilterChanged("42")
            viewModel.onDateRangeSelected(from, to)
            viewModel.refresh()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                getAuditLog(
                    objectId = 42,
                    from = from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    to = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                )
            }
        }

    @Test
    fun `a failure surfaces an error message and clears loading`() =
        runTest(dispatcher) {
            coEvery { getAuditLog(objectId = null, from = null, to = null) } returns DonaResult.Error(DonaFailure.Unknown("boom"))

            val viewModel = AuditLogViewModel(getAuditLog)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("boom", viewModel.uiState.value.errorMessage)
            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals(emptyList<AuditLogEntry>(), viewModel.uiState.value.entries)
        }
}
