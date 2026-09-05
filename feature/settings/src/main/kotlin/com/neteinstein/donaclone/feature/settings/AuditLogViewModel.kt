package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetAuditLogUseCase
import com.neteinstein.donaclone.core.model.AuditLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditLogUiState(
    val isLoading: Boolean = false,
    val entries: List<AuditLogEntry> = emptyList(),
    val errorMessage: String? = null,
    val objectIdFilter: String = "",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
)

class AuditLogViewModel(
    private val getAuditLog: GetAuditLogUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onObjectIdFilterChanged(value: String) {
        _uiState.update { it.copy(objectIdFilter = value.filter(Char::isDigit)) }
    }

    fun onDateRangeSelected(
        from: LocalDate?,
        to: LocalDate?,
    ) {
        _uiState.update { it.copy(fromDate = from, toDate = to) }
    }

    fun refresh() {
        val state = _uiState.value
        val objectId = state.objectIdFilter.toIntOrNull()
        val from = state.fromDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
        // The "to" bound is exclusive on the wire (`operation: "lesser"`), so push it to the
        // start of the following day to make the picked end date inclusive.
        val to: Instant? = state.toDate?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getAuditLog(objectId = objectId, from = from, to = to)) {
                is DonaResult.Success -> _uiState.update { it.copy(isLoading = false, entries = result.data) }
                is DonaResult.Error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.failure.message ?: "Failed to load audit log")
                    }
            }
        }
    }
}
