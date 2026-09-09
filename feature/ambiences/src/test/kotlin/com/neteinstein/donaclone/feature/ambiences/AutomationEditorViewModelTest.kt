package com.neteinstein.donaclone.feature.ambiences

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.DeleteAutomationUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetAmbiencesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetAutomationDetailUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveAutomationUseCase
import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AmbienceConditionType
import com.neteinstein.donaclone.core.model.AutomationDetail
import com.neteinstein.donaclone.core.model.ConditionDraft
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.model.TriggerDraft
import com.neteinstein.donaclone.core.model.TriggerType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getRooms = mockk<GetRoomsUseCase>()
    private val getDevices = mockk<GetDevicesUseCase>()
    private val getAmbiences = mockk<GetAmbiencesUseCase>()
    private val getAutomationDetail = mockk<GetAutomationDetailUseCase>()
    private val saveAutomation = mockk<SaveAutomationUseCase>()
    private val deleteAutomation = mockk<DeleteAutomationUseCase>()

    private val kitchen = Division(id = 1, name = "Kitchen", floor = 0)
    private val light = Device.BinaryOutput(id = 1, name = "Kitchen light", roomId = 1, isOn = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getRooms() } returns DonaResult.Success(listOf(kitchen))
        coEvery { getDevices() } returns DonaResult.Success(listOf(light))
        coEvery { getAutomationDetail(any()) } returns DonaResult.Success(AutomationDetail())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(ambienceId: Int? = null) =
        AutomationEditorViewModel(ambienceId, getRooms, getDevices, getAmbiences, getAutomationDetail, saveAutomation, deleteAutomation)

    @Test
    fun `a new draft cannot be saved without a name or a trigger`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canSave)

            viewModel.onNameChange("Movie night")
            assertFalse(viewModel.uiState.value.canSave)

            viewModel.addEntry(AutomationSection.TRIGGERS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `adding an entry closes the editing section and assigns it a stable id`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startAdding(AutomationSection.ACTIONS)
            assertEquals(AutomationSection.ACTIONS, viewModel.uiState.value.editingSection)

            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))

            val state = viewModel.uiState.value
            assertNull(state.editingSection)
            assertEquals(1, state.entriesBySection[AutomationSection.ACTIONS]?.size)
            assertEquals("Kitchen light", state.entriesBySection[AutomationSection.ACTIONS]?.single()?.summary)
        }

    @Test
    fun `removing an entry only affects its own section`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.addEntry(AutomationSection.TRIGGERS, AutomationEntryDraft(id = 0, type = AutomationEntryType.TIMED, hour = 8, minute = 30))
            viewModel.addEntry(AutomationSection.CONDITIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            val triggerId = viewModel.uiState.value.entriesBySection[AutomationSection.TRIGGERS]!!.single().id

            viewModel.removeEntry(AutomationSection.TRIGGERS, triggerId)

            val state = viewModel.uiState.value
            assertTrue(state.entriesBySection[AutomationSection.TRIGGERS].orEmpty().isEmpty())
            assertEquals(1, state.entriesBySection[AutomationSection.CONDITIONS]?.size)
        }

    @Test
    fun `saving a new automation calls the use case and surfaces a success message that closes the screen`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { saveAutomation(null, any()) } returns DonaResult.Success(9)

            viewModel.onNameChange("Movie night")
            viewModel.addEntry(AutomationSection.TRIGGERS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            assertNull(viewModel.uiState.value.saveMessage)

            viewModel.save()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { saveAutomation(null, any()) }
            val state = viewModel.uiState.value
            assertFalse(state.isSaving)
            assertTrue(state.closeAfterMessage)
            assertEquals("Automation saved.", state.saveMessage)

            viewModel.consumeSaveMessage()
            assertNull(viewModel.uiState.value.saveMessage)
            assertFalse(viewModel.uiState.value.closeAfterMessage)
        }

    @Test
    fun `a failed save surfaces the failure message and keeps the draft open`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { saveAutomation(null, any()) } returns
                DonaResult.Error(DonaFailure.RequestRejected(code = 500, message = "hub error"))

            viewModel.onNameChange("Movie night")
            viewModel.addEntry(AutomationSection.TRIGGERS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            viewModel.save()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.closeAfterMessage)
            assertTrue(state.saveMessage.orEmpty().contains("hub error"))
        }

    @Test
    fun `opening with an ambienceId seeds the name and enabled state from the matching scene`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isEditing)
            assertEquals("Movie night", state.name)
            assertTrue(state.enabled)
        }

    @Test
    fun `saving an existing automation calls the use case with its id and a distinct success message`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
            coEvery { saveAutomation(7, any()) } returns DonaResult.Success(7)

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.save()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { saveAutomation(7, any()) }
            assertEquals("Automation updated.", viewModel.uiState.value.saveMessage)
        }

    @Test
    fun `deleting an existing automation calls the use case and surfaces a success message that closes the screen`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
            coEvery { deleteAutomation(7) } returns DonaResult.Success(Unit)

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.delete()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { deleteAutomation(7) }
            val state = viewModel.uiState.value
            assertTrue(state.closeAfterMessage)
            assertEquals("Automation deleted.", state.saveMessage)
        }

    @Test
    fun `removing a non-last action entry is flagged as truncating the chain, other sections never are`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            val actions = viewModel.uiState.value.entriesBySection[AutomationSection.ACTIONS]!!
            val (first, second) = actions

            assertTrue(viewModel.removingTruncatesChain(AutomationSection.ACTIONS, first.id))
            assertFalse(viewModel.removingTruncatesChain(AutomationSection.ACTIONS, second.id))

            viewModel.addEntry(AutomationSection.TRIGGERS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            val triggerId = viewModel.uiState.value.entriesBySection[AutomationSection.TRIGGERS]!!.single().id
            assertFalse(viewModel.removingTruncatesChain(AutomationSection.TRIGGERS, triggerId))
        }

    @Test
    fun `removing a non-last action entry also removes every action after it`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            viewModel.addEntry(AutomationSection.ACTIONS, AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = light))
            val firstId = viewModel.uiState.value.entriesBySection[AutomationSection.ACTIONS]!!.first().id

            viewModel.removeEntry(AutomationSection.ACTIONS, firstId)

            assertTrue(viewModel.uiState.value.entriesBySection[AutomationSection.ACTIONS].orEmpty().isEmpty())
        }

    @Test
    fun `opening an existing automation shows the triggers, actions, conditions and finalizers it already has`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
            coEvery { getAutomationDetail(7) } returns
                DonaResult.Success(
                    AutomationDetail(
                        startTriggers = listOf(TriggerDraft(existingId = 100, type = TriggerType.TIMED, time = "07:20")),
                        stopTriggers = listOf(TriggerDraft(existingId = 101, type = TriggerType.TIMED, time = "23:45")),
                        conditions =
                            listOf(
                                ConditionDraft(existingId = 102, type = AmbienceConditionType.DEVICE, conditioner = 1, status = 1),
                            ),
                        actions = listOf(ActionDraft(existingId = 103, type = 0, device = 1, deviceType = 60, action = 1)),
                    ),
                )

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            val entries = viewModel.uiState.value.entriesBySection
            assertEquals("07:20", entries[AutomationSection.TRIGGERS]?.single()?.summary)
            assertEquals("23:45", entries[AutomationSection.FINALIZERS]?.single()?.summary)
            assertEquals("Kitchen light", entries[AutomationSection.CONDITIONS]?.single()?.summary)
            assertEquals("Kitchen light", entries[AutomationSection.ACTIONS]?.single()?.summary)
            assertEquals(100, entries[AutomationSection.TRIGGERS]?.single()?.hubId)
        }

    @Test
    fun `re-saving an untouched existing automation keeps its entries instead of duplicating them`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
            coEvery { getAutomationDetail(7) } returns
                DonaResult.Success(
                    AutomationDetail(startTriggers = listOf(TriggerDraft(existingId = 100, type = TriggerType.TIMED, time = "07:20"))),
                )
            coEvery { saveAutomation(7, any()) } returns DonaResult.Success(7)

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.save()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                saveAutomation(
                    7,
                    match { it.startTriggers.single().existingId == 100 && it.removedTriggerIds.isEmpty() },
                )
            }
        }

    @Test
    fun `removing an entry that came from the hub schedules it for deletion on the next save`() =
        runTest(dispatcher) {
            val movieNight = Ambience(id = 7, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
            coEvery { getAutomationDetail(7) } returns
                DonaResult.Success(
                    AutomationDetail(
                        conditions =
                            listOf(
                                ConditionDraft(existingId = 102, type = AmbienceConditionType.DEVICE, conditioner = 1, status = 1),
                            ),
                    ),
                )
            coEvery { saveAutomation(7, any()) } returns DonaResult.Success(7)

            val viewModel = createViewModel(ambienceId = 7)
            dispatcher.scheduler.advanceUntilIdle()

            val conditionId = viewModel.uiState.value.entriesBySection[AutomationSection.CONDITIONS]!!.single().id
            viewModel.removeEntry(AutomationSection.CONDITIONS, conditionId)
            viewModel.save()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { saveAutomation(7, match { it.conditions.isEmpty() && it.removedConditionIds == listOf(102) }) }
        }
}
