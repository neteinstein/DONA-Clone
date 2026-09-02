package com.neteinstein.donaclone.feature.ambiences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetAmbiencesUseCase
import com.neteinstein.donaclone.core.domain.usecase.TriggerAmbienceUseCase
import com.neteinstein.donaclone.core.model.Ambience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AmbiencesUiState(
    val isLoading: Boolean = true,
    val ambiences: List<Ambience> = emptyList(),
    val errorMessage: String? = null,
)

class AmbiencesViewModel(
    private val getAmbiences: GetAmbiencesUseCase,
    private val triggerAmbience: TriggerAmbienceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AmbiencesUiState())
    val uiState: StateFlow<AmbiencesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getAmbiences()) {
                is DonaResult.Success -> _uiState.update { it.copy(isLoading = false, ambiences = result.data) }
                is DonaResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.failure.message) }
            }
        }
    }

    fun toggle(ambience: Ambience) {
        viewModelScope.launch {
            // Optimistic update: mirrors the original app's UI toggling isPlaying immediately.
            _uiState.update { state ->
                state.copy(ambiences = state.ambiences.map { if (it.id == ambience.id) it.copy(isPlaying = !it.isPlaying) else it })
            }
            val result = triggerAmbience(ambience)
            if (result is DonaResult.Error) {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = result.failure.message,
                        ambiences = state.ambiences.map { if (it.id == ambience.id) ambience else it },
                    )
                }
            }
        }
    }
}
