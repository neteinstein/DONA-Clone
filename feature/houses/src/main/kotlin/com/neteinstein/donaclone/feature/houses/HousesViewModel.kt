package com.neteinstein.donaclone.feature.houses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.CanInstallUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.DeleteHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.DiscoverHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.DownloadUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.InstallUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.OpenInstallPermissionSettingsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.core.model.UpdateAvailability
import com.neteinstein.donaclone.core.model.UpdateStatus
import com.neteinstein.donaclone.core.model.toUpdateStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HousesMode {
    data object List : HousesMode

    data class Editing(
        val original: House?,
        val draft: House,
    ) : HousesMode
}

data class HousesUiState(
    val houses: kotlin.collections.List<House> = emptyList(),
    val mode: HousesMode = HousesMode.List,
    val discovered: kotlin.collections.List<DiscoveredHouse> = emptyList(),
    val isDiscovering: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
)

class HousesViewModel(
    private val observeHouses: ObserveHousesUseCase,
    private val saveHouse: SaveHouseUseCase,
    private val deleteHouse: DeleteHouseUseCase,
    private val discoverHouses: DiscoverHousesUseCase,
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val downloadUpdate: DownloadUpdateUseCase,
    private val canInstallUpdates: CanInstallUpdatesUseCase,
    private val installUpdate: InstallUpdateUseCase,
    private val openInstallPermissionSettings: OpenInstallPermissionSettingsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HousesUiState())
    val uiState: StateFlow<HousesUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch {
            observeHouses().collect { houses -> _uiState.update { it.copy(houses = houses) } }
        }
        viewModelScope.launch {
            observeThemeMode().collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    /**
     * Runs a background update check when this screen is entered so the "Update to latest" button
     * can reflect availability immediately, without auto-downloading. Mirrors
     * `SettingsViewModel.onScreenEntered`, since both screens surface the same "Updates" section.
     */
    fun onScreenEntered() {
        viewModelScope.launch {
            val result = checkForUpdate()
            if (result is DonaResult.Success) {
                _uiState.update { it.copy(updateStatus = result.data.toUpdateStatus()) }
            }
        }
    }

    /**
     * Checks GitHub Releases and, if a newer build exists, downloads it and launches the system
     * installer - unless the OS will block that install outright, in which case this stops at
     * [UpdateStatus.SideloadingBlocked] without downloading anything.
     */
    fun onUpdateClicked() {
        val availableUpdate = (_uiState.value.updateStatus as? UpdateStatus.UpdateAvailable)?.update
        if (availableUpdate != null) {
            viewModelScope.launch { downloadAndInstall(availableUpdate) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Checking) }
            when (val result = checkForUpdate()) {
                is DonaResult.Success -> {
                    val availability = result.data
                    if (availability is UpdateAvailability.Available) {
                        downloadAndInstall(availability.update)
                    } else {
                        _uiState.update { it.copy(updateStatus = availability.toUpdateStatus()) }
                    }
                }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(updateStatus = UpdateStatus.Failed(result.failure.message ?: "Update check failed"))
                    }
            }
        }
    }

    /** Deep-links to the system "install unknown apps" settings page for this app. */
    fun onEnableSideloadingClicked() {
        openInstallPermissionSettings()
    }

    private suspend fun downloadAndInstall(update: AppUpdate) {
        if (!canInstallUpdates()) {
            _uiState.update { it.copy(updateStatus = UpdateStatus.SideloadingBlocked) }
            return
        }

        _uiState.update { it.copy(updateStatus = UpdateStatus.Downloading) }
        when (val result = downloadUpdate(update)) {
            is DonaResult.Success -> {
                installUpdate(result.data)
                _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
            }
            is DonaResult.Error ->
                _uiState.update { it.copy(updateStatus = UpdateStatus.Failed(result.failure.message ?: "Download failed")) }
        }
    }

    fun startAddingHouse() {
        _uiState.update { it.copy(mode = HousesMode.Editing(original = null, draft = House(name = ""))) }
        startDiscovery()
    }

    fun startEditingHouse(house: House) {
        _uiState.update { it.copy(mode = HousesMode.Editing(original = house, draft = house)) }
    }

    fun cancelEditing() {
        stopDiscovery()
        _uiState.update { it.copy(mode = HousesMode.List) }
    }

    fun updateDraft(transform: (House) -> House) {
        _uiState.update { state ->
            val editing = state.mode as? HousesMode.Editing ?: return@update state
            state.copy(mode = editing.copy(draft = transform(editing.draft)))
        }
    }

    fun applyDiscoveredHouse(discoveredHouse: DiscoveredHouse) {
        updateDraft { draft ->
            draft.copy(
                localIp = discoveredHouse.ip,
                name = draft.name.ifBlank { "DPU ${discoveredHouse.serialNumber.orEmpty()}".trim() },
            )
        }
    }

    fun saveDraft() {
        val editing = _uiState.value.mode as? HousesMode.Editing ?: return
        if (editing.draft.name.isBlank()) return
        viewModelScope.launch {
            saveHouse(editing.draft)
            stopDiscovery()
            _uiState.update { it.copy(mode = HousesMode.List) }
        }
    }

    fun delete(house: House) {
        viewModelScope.launch { deleteHouse(house.name) }
    }

    private fun startDiscovery() {
        stopDiscovery()
        _uiState.update { it.copy(isDiscovering = true, discovered = emptyList()) }
        discoveryJob =
            viewModelScope.launch {
                discoverHouses().collect { found ->
                    _uiState.update { it.copy(discovered = it.discovered + found) }
                }
                _uiState.update { it.copy(isDiscovering = false) }
            }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _uiState.update { it.copy(isDiscovering = false) }
    }

    override fun onCleared() {
        stopDiscovery()
    }
}
