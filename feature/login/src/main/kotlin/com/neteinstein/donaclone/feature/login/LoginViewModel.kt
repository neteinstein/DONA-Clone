package com.neteinstein.donaclone.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.LoginUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.model.House
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val houses: List<House> = emptyList(),
    val selectedHouse: House? = null,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSucceeded: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Shown once, right after a successful password login, when biometric unlock isn't on yet. */
    val showBiometricOptInPrompt: Boolean = false,
)

class LoginViewModel(
    private val observeHouses: ObserveHousesUseCase,
    private val getActiveHouse: GetActiveHouseUseCase,
    private val login: LoginUseCase,
    observeBiometricEnabled: ObserveBiometricEnabledUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeHouses().collect { houses ->
                _uiState.update { state ->
                    val updatedState = state.copy(houses = houses)
                    val selected = state.selectedHouse
                    when {
                        selected == null && houses.isNotEmpty() ->
                            updatedState.copy(selectedHouse = houses.first()).withPrefilledCredentials()
                        // The selected house may have just been edited on the Houses screen — its
                        // address (dns/localIp/...) *and* its credentials, which are only editable
                        // there now that this screen's fields are read-only. Re-sync both so the
                        // saved values show up here and login() uses them instead of the stale
                        // copy captured when the house was first selected. Matched by name (the
                        // House primary key); a rename or a delete falls back to the first
                        // remaining profile rather than keeping a house that no longer exists.
                        selected != null -> {
                            val refreshed = houses.find { it.name == selected.name } ?: houses.firstOrNull()
                            if (refreshed != selected) {
                                updatedState.copy(selectedHouse = refreshed).withPrefilledCredentials()
                            } else {
                                updatedState
                            }
                        }
                        else -> updatedState
                    }
                }
            }
        }
        viewModelScope.launch {
            getActiveHouse()?.let { selectHouse(it) }
        }
        viewModelScope.launch {
            observeBiometricEnabled().collect { enabled -> _uiState.update { it.copy(biometricEnabled = enabled) } }
        }
    }

    fun selectHouse(house: House) {
        _uiState.update { it.copy(selectedHouse = house).withPrefilledCredentials() }
    }

    fun login() {
        val house = _uiState.value.selectedHouse ?: return
        val credentials = house.copy(username = _uiState.value.username, password = _uiState.value.password)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = login.invoke(credentials)) {
                is DonaResult.Success ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSucceeded = true,
                            showBiometricOptInPrompt = !it.biometricEnabled,
                        )
                    }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.failure.message ?: "Could not log in")
                    }
            }
        }
    }

    fun consumeLoginSucceeded() {
        _uiState.update { it.copy(loginSucceeded = false) }
    }

    /** Called when the app resumes to the foreground while this screen is showing. Automatically
     * retries a previously failed login once credentials are available, so the user isn't stuck
     * having to tap "Log in" again after e.g. a transient network drop while backgrounded. */
    fun retryLoginIfNeeded() {
        val state = _uiState.value
        val hasCredentials = state.username.isNotBlank() && state.password.isNotBlank()
        if (state.errorMessage != null && !state.isLoading && hasCredentials) {
            login()
        }
    }

    fun onBiometricOptInResult(enable: Boolean) {
        if (enable) viewModelScope.launch { setBiometricEnabled(true) }
        _uiState.update { it.copy(showBiometricOptInPrompt = false) }
    }

    /** Credentials are never typed on this screen — they always mirror the stored profile, so any
     * error raised by the previous pair of values is stale as soon as they are re-read. */
    private fun LoginUiState.withPrefilledCredentials(): LoginUiState =
        copy(
            username = selectedHouse?.username.orEmpty(),
            password = selectedHouse?.password.orEmpty(),
            errorMessage = null,
        )
}
