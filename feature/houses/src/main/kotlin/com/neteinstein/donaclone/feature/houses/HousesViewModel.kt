package com.neteinstein.donaclone.feature.houses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.domain.usecase.DeleteHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.DiscoverHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveHouseUseCase
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
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
)

class HousesViewModel(
    private val observeHouses: ObserveHousesUseCase,
    private val saveHouse: SaveHouseUseCase,
    private val deleteHouse: DeleteHouseUseCase,
    private val discoverHouses: DiscoverHousesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HousesUiState())
    val uiState: StateFlow<HousesUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch {
            observeHouses().collect { houses -> _uiState.update { it.copy(houses = houses) } }
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
