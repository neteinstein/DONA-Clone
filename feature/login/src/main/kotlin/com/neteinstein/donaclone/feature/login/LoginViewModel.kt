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
                    if (state.selectedHouse == null && houses.isNotEmpty()) {
                        updatedState.copy(selectedHouse = houses.first()).withPrefilledCredentials()
                    } else {
                        updatedState
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

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
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

    private fun LoginUiState.withPrefilledCredentials(): LoginUiState =
        copy(username = selectedHouse?.username.orEmpty(), password = selectedHouse?.password.orEmpty())
}
