package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val houseName: String = "",
    val userName: String = "",
    val loggedOut: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val biometricEnabled: Boolean = false,
)

class SettingsViewModel(
    getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    observeBiometricEnabled: ObserveBiometricEnabledUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            getCurrentSession()?.let { SettingsUiState(houseName = it.houseName, userName = it.userName) }
                ?: SettingsUiState(),
        )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeThemeMode().collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
        viewModelScope.launch {
            observeBiometricEnabled().collect { enabled -> _uiState.update { it.copy(biometricEnabled = enabled) } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logout.invoke()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    fun onBiometricEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { setBiometricEnabled(enabled) }
    }
}
