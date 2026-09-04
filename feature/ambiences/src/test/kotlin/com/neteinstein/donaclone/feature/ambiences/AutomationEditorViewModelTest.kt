package com.neteinstein.donaclone.feature.ambiences

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import io.mockk.coEvery
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

    private val kitchen = Division(id = 1, name = "Kitchen", floor = 0)
    private val light = Device.BinaryOutput(id = 1, name = "Kitchen light", roomId = 1, isOn = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getRooms() } returns DonaResult.Success(listOf(kitchen))
        coEvery { getDevices() } returns DonaResult.Success(listOf(light))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AutomationEditorViewModel(getRooms, getDevices)

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
    fun `saving surfaces a message instead of pretending to persist`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.saveMessage)
            viewModel.save()
            assertTrue(viewModel.uiState.value.saveMessage != null)

            viewModel.consumeSaveMessage()
            assertNull(viewModel.uiState.value.saveMessage)
        }
}
