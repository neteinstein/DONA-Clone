package com.neteinstein.donaclone.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val houseName: String = "",
    val userName: String = "",
    val loggedOut: Boolean = false,
)

class DashboardViewModel(
    getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            getCurrentSession()?.let { session ->
                DashboardUiState(houseName = session.houseName, userName = session.userName)
            } ?: DashboardUiState(),
        )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            logout.invoke()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }
}
