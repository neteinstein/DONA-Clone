package com.neteinstein.donaclone.feature.ambiences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.DeleteAutomationUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetAmbiencesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveAutomationUseCase
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
    /** True when this screen was opened (via a long press on an existing scene) to view/edit it,
     * rather than to create a new one. */
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val name: String = "",
    val enabled: Boolean = false,
    val rooms: List<Division> = emptyList(),
    val devices: List<Device> = emptyList(),
    val entriesBySection: Map<AutomationSection, List<AutomationEntryDraft>> =
        AutomationSection.entries.associateWith { emptyList() },
    /** The section whose "Configure ..." sub-screen is currently shown, or null for the main
     * editor. Only one editing surface is ever open at once. */
    val editingSection: AutomationSection? = null,
    /** Set after a save/delete attempt. Surfaced as a dialog rather than silently pretending the
     * scenario was written to (or removed from) the hub. */
    val saveMessage: String? = null,
    /** True once [saveMessage] describes a *successful* save or delete — the screen should close
     * after the user dismisses that message rather than leaving them on stale state. */
    val closeAfterMessage: Boolean = false,
) {
    /** A new scenario needs at least one trigger before it makes sense on the hub. An existing one
     * being edited already has a trigger there (even though this app has no confirmed way to read
     * it back, see [AutomationEditorViewModel]'s class doc), so editing only requires a name. */
    val canSave: Boolean
        get() =
            !isSaving && !isDeleting &&
                name.isNotBlank() && (isEditing || entriesBySection[AutomationSection.TRIGGERS]?.isNotEmpty() == true)

    val canDelete: Boolean
        get() = isEditing && !isSaving && !isDeleting
}

/**
 * Backs both the "create a new automation" and "view/edit an existing automation" screens
 * ([AutomationEditorScreen]) — the latter is reached by long-pressing a scene on [AmbiencesScreen].
 * When [ambienceId] is non-null, [refresh] looks the scene up (there is no single-scene hub
 * endpoint) and seeds the name/enabled fields from it; the hub only exposes those two fields per
 * [com.neteinstein.donaclone.core.model.Ambience], so that's all there is to seed. The rest of the
 * draft is built entirely client-side from real rooms/devices and mapped onto the hub's confirmed
 * trigger/condition/action wire schema by [save] (`docs/PROTOCOL.md` §11.6).
 *
 * **Editing limitation, by protocol design, not by choice:** saving an edit can rename the
 * scenario, toggle it, and *add* new triggers/conditions/actions — it cannot show, change, or
 * remove the automation's pre-existing ones. §11.4 marks the `ambienceStartTrigger`/
 * `ambienceStopTrigger`/`ambienceCondition` join subjects "create only" (no confirmed `read`), and
 * [com.neteinstein.donaclone.core.model.Ambience] doesn't carry `firstAction` either, so this app
 * has no confirmed way to discover which trigger/condition/action ids already belong to an
 * ambience being edited.
 */
class AutomationEditorViewModel(
    private val ambienceId: Int?,
    private val getRooms: GetRoomsUseCase,
    private val getDevices: GetDevicesUseCase,
    private val getAmbiences: GetAmbiencesUseCase,
    private val saveAutomation: SaveAutomationUseCase,
    private val deleteAutomation: DeleteAutomationUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AutomationEditorUiState(isEditing = ambienceId != null))
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
            val ambiencesResult = if (ambienceId != null) getAmbiences() else null
            val ambience = (ambiencesResult as? DonaResult.Success)?.data?.firstOrNull { it.id == ambienceId }
            _uiState.update { state ->
                val failure =
                    (roomsResult as? DonaResult.Error)?.failure
                        ?: (devicesResult as? DonaResult.Error)?.failure
                        ?: (ambiencesResult as? DonaResult.Error)?.failure
                state.copy(
                    isLoading = false,
                    rooms = (roomsResult as? DonaResult.Success)?.data ?: state.rooms,
                    devices = (devicesResult as? DonaResult.Success)?.data ?: state.devices,
                    name = ambience?.name ?: state.name,
                    enabled = ambience?.enabled ?: state.enabled,
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

    /**
     * Removing a non-last [AutomationSection.ACTIONS] entry truncates the chain from that point
     * on — there's no observed "delete the middle link, reconnect the chain" support on the real
     * hub (§11.6), so this client-side draft mirrors that instead of pretending a gap-free chain
     * is possible. [AutomationEditorScreen] confirms this with the user before calling it.
     */
    fun removeEntry(
        section: AutomationSection,
        entryId: Long,
    ) {
        _uiState.update { state ->
            val current = state.entriesBySection[section].orEmpty()
            val updated =
                if (section == AutomationSection.ACTIONS) {
                    val index = current.indexOfFirst { it.id == entryId }
                    if (index == -1) current else current.take(index)
                } else {
                    current.filterNot { it.id == entryId }
                }
            state.copy(entriesBySection = state.entriesBySection + (section to updated))
        }
    }

    /** True when removing this entry would also drop later entries from the chain — used by
     * [AutomationEditorScreen] to decide whether a removal needs confirmation. */
    fun removingTruncatesChain(
        section: AutomationSection,
        entryId: Long,
    ): Boolean {
        if (section != AutomationSection.ACTIONS) return false
        val entries = _uiState.value.entriesBySection[section].orEmpty()
        val index = entries.indexOfFirst { it.id == entryId }
        return index != -1 && index != entries.lastIndex
    }

    fun save() {
        val state = _uiState.value
        val draft = state.toAutomationDraft()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = saveAutomation(ambienceId, draft)) {
                is DonaResult.Success ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveMessage = if (state.isEditing) "Automation updated." else "Automation saved.",
                            closeAfterMessage = true,
                        )
                    }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveMessage = "Couldn't save this automation: ${result.failure.message ?: "unknown error"}",
                        )
                    }
            }
        }
    }

    fun delete() {
        val id = ambienceId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val result = deleteAutomation(id)) {
                is DonaResult.Success ->
                    _uiState.update {
                        it.copy(isDeleting = false, saveMessage = "Automation deleted.", closeAfterMessage = true)
                    }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            saveMessage = "Couldn't delete this automation: ${result.failure.message ?: "unknown error"}",
                        )
                    }
            }
        }
    }

    fun consumeSaveMessage() = _uiState.update { it.copy(saveMessage = null, closeAfterMessage = false) }
}
