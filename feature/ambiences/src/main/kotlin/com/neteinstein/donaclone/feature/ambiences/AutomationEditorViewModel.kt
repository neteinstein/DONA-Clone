package com.neteinstein.donaclone.feature.ambiences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutomationEditorUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val name: String = "",
    val enabled: Boolean = false,
    val rooms: List<Division> = emptyList(),
    val devices: List<Device> = emptyList(),
    val entriesBySection: Map<AutomationSection, List<AutomationEntryDraft>> =
        AutomationSection.entries.associateWith { emptyList() },
    /** The section whose "Configure ..." sub-screen is currently shown, or null for the main
     * editor. Only one editing surface is ever open at once. */
    val editingSection: AutomationSection? = null,
    /** Set after a save attempt — see [AutomationEditorViewModel.save]. Surfaced as a dialog/snackbar
     * rather than silently pretending the scenario was written to the hub. */
    val saveMessage: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && entriesBySection[AutomationSection.TRIGGERS]?.isNotEmpty() == true
}

/**
 * Backs the "create a new automation" screen ([AutomationEditorScreen]). Builds the draft entirely
 * client-side from real rooms/devices (see [refresh]) — there is currently no confirmed hub wire
 * format for writing a scenario's trigger/condition/action objects (see `docs/PROTOCOL.md` §"Ambience/
 * Scene": those sub-objects were never fully decompiled), so [save] cannot yet persist the result to
 * the hub. It surfaces that limitation via [AutomationEditorUiState.saveMessage] instead of silently
 * discarding the user's work or guessing at a request shape that could corrupt hub state.
 */
class AutomationEditorViewModel(
    private val getRooms: GetRoomsUseCase,
    private val getDevices: GetDevicesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AutomationEditorUiState())
    val uiState: StateFlow<AutomationEditorUiState> = _uiState.asStateFlow()

    private var nextEntryId = 0L

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val roomsResult = getRooms()
            val devicesResult = getDevices()
            _uiState.update { state ->
                val failure = (roomsResult as? DonaResult.Error)?.failure ?: (devicesResult as? DonaResult.Error)?.failure
                state.copy(
                    isLoading = false,
                    rooms = (roomsResult as? DonaResult.Success)?.data ?: state.rooms,
                    devices = (devicesResult as? DonaResult.Success)?.data ?: state.devices,
                    errorMessage = failure?.message,
                )
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onEnabledChange(enabled: Boolean) = _uiState.update { it.copy(enabled = enabled) }

    fun startAdding(section: AutomationSection) = _uiState.update { it.copy(editingSection = section) }

    fun cancelAdding() = _uiState.update { it.copy(editingSection = null) }

    fun addEntry(
        section: AutomationSection,
        entry: AutomationEntryDraft,
    ) {
        _uiState.update { state ->
            val updated = state.entriesBySection[section].orEmpty() + entry.copy(id = nextEntryId++)
            state.copy(entriesBySection = state.entriesBySection + (section to updated), editingSection = null)
        }
    }

    fun removeEntry(
        section: AutomationSection,
        entryId: Long,
    ) {
        _uiState.update { state ->
            val updated = state.entriesBySection[section].orEmpty().filterNot { it.id == entryId }
            state.copy(entriesBySection = state.entriesBySection + (section to updated))
        }
    }

    /** There's no confirmed hub API to create a new scenario's trigger/condition objects yet (see
     * class doc), so this can't actually write [uiState] to the hub — it only tells the user why,
     * rather than either pretending success or throwing the draft away. */
    fun save() {
        _uiState.update {
            it.copy(
                saveMessage =
                    "Saving new automations to the hub isn't supported by this app yet — the hub's " +
                        "trigger/condition format hasn't been confirmed. Your draft is unchanged.",
            )
        }
    }

    fun consumeSaveMessage() = _uiState.update { it.copy(saveMessage = null) }
}
