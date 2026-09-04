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
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.OpenInstallPermissionSettingsUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.core.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Update to latest" button and its status text on [SettingsScreen] - checks GitHub
 * Releases and, if allowed, downloads and installs a newer build. Mirrors the in-app update flow
 * from https://github.com/neteinstein/CompareApp.
 */
sealed class UpdateStatus {
    /** Nothing in flight - the button's normal resting state. */
    data object Idle : UpdateStatus()

    data object Checking : UpdateStatus()

    /** The installed build is already the latest one published on GitHub Releases. */
    data class UpToDate(val currentVersionName: String) : UpdateStatus()

    /** A newer build exists and is ready to be downloaded/installed on button tap. */
    data class UpdateAvailable(val update: AppUpdate) : UpdateStatus()

    data object Downloading : UpdateStatus()

    /**
     * A newer release exists, but the OS won't let this app install it yet. [SettingsScreen] shows
     * a warning banner whose action opens the system "install unknown apps" page for this app
     * ([SettingsViewModel.onEnableSideloadingClicked]); the user is expected to tap
     * "Update to latest" again afterwards, which re-checks and proceeds automatically now that the
     * OS allows it.
     */
    data object SideloadingBlocked : UpdateStatus()

    data class Failed(val message: String) : UpdateStatus()
}

data class SettingsUiState(
    val houseName: String = "",
    val userName: String = "",
    val loggedOut: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val biometricEnabled: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
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

    private fun UpdateAvailability.toUpdateStatus(): UpdateStatus =
        when (this) {
            is UpdateAvailability.UpToDate -> UpdateStatus.UpToDate(currentVersionName)
            is UpdateAvailability.Available -> UpdateStatus.UpdateAvailable(update)
        }
}
