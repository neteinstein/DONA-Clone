package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.CanInstallUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.DownloadUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.InstallUpdateUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveActionConfirmationEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDebugModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.OpenInstallPermissionSettingsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetActionConfirmationEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetDebugModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.core.model.UpdateAvailability
import com.neteinstein.donaclone.core.model.UpdateStatus
import com.neteinstein.donaclone.core.model.toUpdateStatus
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
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val debugModeEnabled: Boolean = false,
    val actionConfirmationEnabled: Boolean = true,
)

class SettingsViewModel(
    getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    observeBiometricEnabled: ObserveBiometricEnabledUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val downloadUpdate: DownloadUpdateUseCase,
    private val canInstallUpdates: CanInstallUpdatesUseCase,
    private val installUpdate: InstallUpdateUseCase,
    private val openInstallPermissionSettings: OpenInstallPermissionSettingsUseCase,
    observeDebugModeEnabled: ObserveDebugModeUseCase,
    private val setDebugModeEnabled: SetDebugModeUseCase,
    observeActionConfirmationEnabled: ObserveActionConfirmationEnabledUseCase,
    private val setActionConfirmationEnabled: SetActionConfirmationEnabledUseCase,
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
        viewModelScope.launch {
            observeDebugModeEnabled().collect { enabled -> _uiState.update { it.copy(debugModeEnabled = enabled) } }
        }
        viewModelScope.launch {
            observeActionConfirmationEnabled().collect { enabled ->
                _uiState.update { it.copy(actionConfirmationEnabled = enabled) }
            }
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

    fun onDebugModeChanged(enabled: Boolean) {
        viewModelScope.launch { setDebugModeEnabled(enabled) }
    }

    fun onActionConfirmationEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { setActionConfirmationEnabled(enabled) }
    }

    /**
     * Runs a background update check when Settings is entered so the "Update to latest" button
     * can reflect availability immediately, without auto-downloading.
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
}
