package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetAuditLogUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetUsersUseCase
import com.neteinstein.donaclone.core.model.AuditLogEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class AuditLogUiState(
    val isLoading: Boolean = false,
    val entries: List<AuditLogEntry> = emptyList(),
    val errorMessage: String? = null,
    val objectIdFilter: String = "",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    /** userId -> display name, best-effort (falls back to the raw id when a lookup misses). */
    val userNames: Map<Int, String> = emptyMap(),
    /** objectId -> display name, resolved against known devices/rooms. The hub's `masterLog`
     * protocol never documents what `objectId` actually references (protocol notes §2.4), so
     * this is a best-effort match, not a confirmed mapping. */
    val objectNames: Map<Int, String> = emptyMap(),
)

class AuditLogViewModel(
    private val getAuditLog: GetAuditLogUseCase,
    private val getUsers: GetUsersUseCase,
    private val getDevices: GetDevicesUseCase,
    private val getRooms: GetRoomsUseCase,
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

            coroutineScope {
                val logDeferred = async { getAuditLog(objectId = objectId, from = from, to = to) }
                val userNamesDeferred = async { fetchUserNames() }
                val objectNamesDeferred = async { fetchObjectNames() }

                when (val result = logDeferred.await()) {
                    is DonaResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                entries = result.data,
                                userNames = userNamesDeferred.await(),
                                objectNames = objectNamesDeferred.await(),
                            )
                        }
                    is DonaResult.Error ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = result.failure.message ?: "Failed to load audit log")
                        }
                }
            }
        }
    }

    /** Best-effort; an error here just means names stay unresolved, not that the log fails to load. */
    private suspend fun fetchUserNames(): Map<Int, String> =
        when (val result = getUsers()) {
            is DonaResult.Success -> result.data.associate { it.id to it.name }
            is DonaResult.Error -> emptyMap()
        }

    /** Merges rooms and devices into one id -> name lookup for [AuditLogEntry.objectId]; devices
     * win on an id collision since most log entries are expected to describe device activity. */
    private suspend fun fetchObjectNames(): Map<Int, String> {
        val rooms =
            when (val result = getRooms()) {
                is DonaResult.Success -> result.data.associate { it.id to it.name }
                is DonaResult.Error -> emptyMap()
            }
        val devices =
            when (val result = getDevices()) {
                is DonaResult.Success -> result.data.associate { it.id to it.name }
                is DonaResult.Error -> emptyMap()
            }
        return rooms + devices
    }
}
