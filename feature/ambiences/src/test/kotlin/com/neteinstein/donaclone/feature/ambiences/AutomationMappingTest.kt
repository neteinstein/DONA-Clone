package com.neteinstein.donaclone.feature.ambiences

import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationMappingTest {
    private val doorSensor = Device.BinaryInput(id = 1, name = "Front door", roomId = 1, isActive = false)
    private val light = Device.BinaryOutput(id = 2, name = "Kitchen light", roomId = 1, isOn = false)
    private val siren = Device.Pulse(id = 3, name = "Siren", roomId = 1, kind = com.neteinstein.donaclone.core.model.PulseKind.SIREN)

    private fun uiState(
        section: AutomationSection,
        entry: AutomationEntryDraft,
    ) = AutomationEditorUiState(
        name = "Test",
        enabled = true,
        entriesBySection = AutomationSection.entries.associateWith { emptyList<AutomationEntryDraft>() } + (section to listOf(entry)),
    )

    @Test
    fun `an interrupt trigger's chosen event is sent as-is instead of a fixed default`() {
        val entry = AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = doorSensor, event = 1)
        val state = uiState(AutomationSection.TRIGGERS, entry)

        val draft = state.toAutomationDraft().startTriggers.single()

        assertEquals(TriggerType.INTERRUPT, draft.type)
        assertEquals(1, draft.event)
    }

    @Test
    fun `an action entry's explicit action, percentage, withLast and delay are sent as-is`() {
        val entry =
            AutomationEntryDraft(
                id = 0,
                type = AutomationEntryType.BY_DEVICE,
                device = light,
                actionCode = 0,
                withLast = true,
                delayFromLastSeconds = 5,
            )
        val state = uiState(AutomationSection.ACTIONS, entry)

        val draft = state.toAutomationDraft().actions.single()

        assertEquals(0, draft.action)
        assertNull(draft.percentage)
        assertEquals(true, draft.withLast)
        assertEquals(5_000L, draft.delayFromLast)
    }

    @Test
    fun `a pulse action with no chosen action code falls back to the device's only valid code`() {
        val entry = AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = siren)
        val state = uiState(AutomationSection.ACTIONS, entry)

        val draft = state.toAutomationDraft().actions.single()

        assertEquals(0, draft.action)
        assertEquals(siren.kind.wireValue, draft.deviceSubtype)
    }
}
