package com.neteinstein.donaclone.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BiometricLockUiState(
    val isAuthenticating: Boolean = false,
    val unlocked: Boolean = false,
    val errorMessage: String? = null,
    /** True once biometric auth fails/is cancelled, or there's nothing to unlock into — falls
     * back to the normal [LoginScreen]. */
    val useFallback: Boolean = false,
)

/** Orchestrates the silent-relogin-after-a-successful-fingerprint-scan flow — the same "fetch the
 * active house, log back in with its stored credentials" the app already does on a bare cold
 * start (see `LoginViewModel.init`), just gated behind [onBiometricSucceeded] instead of running
 * unconditionally. Stays Android-free; the [androidx.biometric.BiometricPrompt] call itself lives
 * in [BiometricLockScreen], which has the `FragmentActivity` this needs. */
class BiometricLockViewModel(
    private val getActiveHouse: GetActiveHouseUseCase,
    private val login: LoginUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiometricLockUiState())
    val uiState: StateFlow<BiometricLockUiState> = _uiState.asStateFlow()

    fun onBiometricSucceeded() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, errorMessage = null) }
            val house = getActiveHouse()
            if (house == null) {
                _uiState.update { it.copy(isAuthenticating = false, useFallback = true) }
                return@launch
            }
            when (val result = login(house)) {
                is DonaResult.Success -> _uiState.update { it.copy(isAuthenticating = false, unlocked = true) }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(isAuthenticating = false, errorMessage = result.failure.message, useFallback = true)
                    }
            }
        }
    }

    fun useFallbackLogin() {
        _uiState.update { it.copy(useFallback = true) }
    }
}
