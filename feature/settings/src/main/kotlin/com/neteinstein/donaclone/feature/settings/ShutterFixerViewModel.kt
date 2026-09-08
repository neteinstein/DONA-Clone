package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveInvertedShuttersUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetShutterInvertedUseCase
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShutterFixerUiState(
    val shutters: List<Device.Shutter> = emptyList(),
    val rooms: List<Division> = emptyList(),
    /** Ids the user has marked as wired backwards, for the active house. */
    val invertedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Backs the "Shutter Fixer" screen: lists every shutter the hub reports and lets the user flag the
 * ones whose open/close direction and position come back mirrored. The flag is applied down in
 * `DeviceRepositoryImpl`, so the positions listed here are already corrected — which is exactly
 * what makes the checkbox verifiable at a glance.
 */
class ShutterFixerViewModel(
    private val getDevices: GetDevicesUseCase,
    private val getRooms: GetRoomsUseCase,
    observeInvertedShutters: ObserveInvertedShuttersUseCase,
    private val setShutterInverted: SetShutterInvertedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShutterFixerUiState())
    val uiState: StateFlow<ShutterFixerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeInvertedShutters().collect { ids -> _uiState.update { it.copy(invertedIds = ids) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            coroutineScope {
                val devicesDeferred = async { getDevices() }
                val roomsDeferred = async { getRooms() }

                when (val result = devicesDeferred.await()) {
                    is DonaResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                shutters = result.data.filterIsInstance<Device.Shutter>().sortedBy { s -> s.name },
                                rooms = roomsDeferred.await().roomsOrEmpty(),
                            )
                        }
                    is DonaResult.Error ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = result.failure.message ?: "Failed to load shutters")
                        }
                }
            }
        }
    }

    /** Best-effort — rows just fall back to no room label if the room read fails. */
    private fun DonaResult<List<Division>>.roomsOrEmpty(): List<Division> =
        when (this) {
            is DonaResult.Success -> data
            is DonaResult.Error -> emptyList()
        }

    /** Re-reads the devices afterwards so the position shown next to the checkbox flips
     * immediately, letting the user confirm the fix without leaving the screen. */
    fun setInverted(
        deviceId: Int,
        inverted: Boolean,
    ) {
        viewModelScope.launch {
            setShutterInverted(deviceId, inverted)
            refresh()
        }
    }
}
