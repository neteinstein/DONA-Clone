package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AutomationDraft
import com.neteinstein.donaclone.core.model.ConditionDraft
import com.neteinstein.donaclone.core.model.TriggerDraft
import com.neteinstein.donaclone.core.model.TriggerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAutomationUseCaseTest {
    private val repository = mockk<AmbienceRepository>()
    private val useCase = SaveAutomationUseCase(repository)

    private val timedTrigger = TriggerDraft(type = TriggerType.TIMED, time = "08:00")
    private val binaryAction =
        ActionDraft(type = 0, device = 42, deviceType = 60, action = 1)

    @Test
    fun `a new automation creates the ambience then creates and links each trigger`() =
        runTest {
            val draft = AutomationDraft(name = "Movie night", enabled = true, startTriggers = listOf(timedTrigger))
            coEvery { repository.createAmbience("Movie night", true) } returns
                DonaResult.Success(Ambience(id = 5, name = "Movie night", isPlaying = false, enabled = true))
            coEvery { repository.createTrigger(timedTrigger) } returns DonaResult.Success(11)
            coEvery { repository.linkStartTrigger(5, 11) } returns DonaResult.Success(Unit)

            val result = useCase(null, draft)

            assertEquals(DonaResult.Success(5), result)
            coVerify { repository.createAmbience("Movie night", true) }
            coVerify { repository.createTrigger(timedTrigger) }
            coVerify { repository.linkStartTrigger(5, 11) }
        }

    @Test
    fun `editing an existing automation updates its fields instead of creating a new ambience`() =
        runTest {
            val draft = AutomationDraft(name = "Renamed", enabled = false)
            coEvery { repository.updateAmbienceFields(5, "Renamed", false) } returns DonaResult.Success(Unit)

            val result = useCase(5, draft)

            assertEquals(DonaResult.Success(5), result)
            coVerify { repository.updateAmbienceFields(5, "Renamed", false) }
            coVerify(exactly = 0) { repository.createAmbience(any(), any()) }
        }

    @Test
    fun `stop triggers and conditions are created and linked with their own join subjects`() =
        runTest {
            val stopTrigger = TriggerDraft(type = TriggerType.TIMED, time = "23:00")
            val condition = ConditionDraft(type = 1, conditioner = 3, status = 1)
            val draft = AutomationDraft(name = "Movie night", enabled = true, stopTriggers = listOf(stopTrigger), conditions = listOf(condition))
            coEvery { repository.createAmbience(any(), any()) } returns
                DonaResult.Success(Ambience(id = 5, name = "Movie night", isPlaying = false, enabled = true))
            coEvery { repository.createTrigger(stopTrigger) } returns DonaResult.Success(21)
            coEvery { repository.linkStopTrigger(5, 21) } returns DonaResult.Success(Unit)
            coEvery { repository.createCondition(condition) } returns DonaResult.Success(31)
            coEvery { repository.linkCondition(5, 31) } returns DonaResult.Success(Unit)

            val result = useCase(null, draft)

            assertTrue(result is DonaResult.Success)
            coVerify { repository.linkStopTrigger(5, 21) }
            coVerify { repository.linkCondition(5, 31) }
        }

    @Test
    fun `the action chain links the first action via ambience firstAction and later ones via nextAction`() =
        runTest {
            val second = binaryAction.copy(device = 43)
            val draft = AutomationDraft(name = "Movie night", enabled = true, actions = listOf(binaryAction, second))
            coEvery { repository.createAmbience(any(), any()) } returns
                DonaResult.Success(Ambience(id = 5, name = "Movie night", isPlaying = false, enabled = true))
            coEvery { repository.createAction(binaryAction) } returns DonaResult.Success(100)
            coEvery { repository.createAction(second) } returns DonaResult.Success(101)
            coEvery { repository.setAmbienceFirstAction(5, 100) } returns DonaResult.Success(Unit)
            coEvery { repository.setActionNext(100, 101) } returns DonaResult.Success(Unit)

            val result = useCase(null, draft)

            assertTrue(result is DonaResult.Success)
            coVerify { repository.setAmbienceFirstAction(5, 100) }
            coVerify { repository.setActionNext(100, 101) }
        }

    @Test
    fun `a failure creating a trigger stops the sequence and is returned as-is`() =
        runTest {
            val draft = AutomationDraft(name = "Movie night", enabled = true, startTriggers = listOf(timedTrigger))
            val failure = DonaFailure.RequestRejected(code = 500, message = "hub error")
            coEvery { repository.createAmbience(any(), any()) } returns
                DonaResult.Success(Ambience(id = 5, name = "Movie night", isPlaying = false, enabled = true))
            coEvery { repository.createTrigger(timedTrigger) } returns DonaResult.Error(failure)

            val result = useCase(null, draft)

            assertEquals(DonaResult.Error(failure), result)
            coVerify(exactly = 0) { repository.linkStartTrigger(any(), any()) }
        }
}
