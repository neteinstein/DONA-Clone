package com.neteinstein.donaclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveConnectivityUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDpuUnreachableUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.RetryConnectionUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetAppForegroundUseCase
import com.neteinstein.donaclone.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainActivityUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val biometricEnabled: Boolean = false,
    val isLocked: Boolean = false,
    val showConnectivityBanner: Boolean = false,
)

/**
 * The composition root's single source of cross-cutting app state — theme mode, the app-level
 * biometric lock, and the "can't reach the server" connectivity banner all live here rather than
 * scattered across screens, since [com.neteinstein.donaclone.MainActivity] needs all three before
 * it can decide what to render.
 */
class MainActivityViewModel(
    observeThemeMode: ObserveThemeModeUseCase,
    observeBiometricEnabled: ObserveBiometricEnabledUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    observeDpuUnreachable: ObserveDpuUnreachableUseCase,
    private val retryConnection: RetryConnectionUseCase,
    private val setAppForeground: SetAppForegroundUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainActivityUiState())
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeThemeMode().collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
        viewModelScope.launch {
            observeBiometricEnabled().collect { enabled ->
                // Turning the setting on locks the app starting now; turning it off only stops
                // requiring a scan going forward, it doesn't unlock a screen mid-session.
                _uiState.update { it.copy(biometricEnabled = enabled, isLocked = it.isLocked || enabled) }
            }
        }
        viewModelScope.launch {
            combine(observeConnectivity(), observeDpuUnreachable()) { isOnline, dpuUnreachable ->
                !isOnline || dpuUnreachable
            }.collect { showBanner -> _uiState.update { it.copy(showConnectivityBanner = showBanner) } }
        }
    }

    /** Call from an `ON_STOP` lifecycle event so re-opening/resuming the app requires another
     * fingerprint scan, matching "gates every app open/resume." Also tells the session-recovery
     * loop the app is no longer visible, so an unsolicited disconnect while backgrounded can't
     * quietly exhaust its retry budget and log the user out before they return. */
    fun onAppBackgrounded() {
        if (_uiState.value.biometricEnabled) {
            _uiState.update { it.copy(isLocked = true) }
        }
        setAppForeground(false)
    }

    /** Call from an `ON_START` lifecycle event: lets any pending automatic session-recovery
     * attempt resume now that the app is visible again, instead of staying paused indefinitely. */
    fun onAppForegrounded() {
        setAppForeground(true)
    }

    fun onUnlocked() {
        _uiState.update { it.copy(isLocked = false) }
    }

    fun retryConnectionNow() {
        viewModelScope.launch { retryConnection() }
    }
}
