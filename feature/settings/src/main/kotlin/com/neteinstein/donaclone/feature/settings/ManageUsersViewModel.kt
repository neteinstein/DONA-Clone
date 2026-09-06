package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.common.map
import com.neteinstein.donaclone.core.domain.usecase.CreateUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.DeleteUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetUsersUseCase
import com.neteinstein.donaclone.core.domain.usecase.UpdateUserUseCase
import com.neteinstein.donaclone.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ManageUsersMode {
    data object List : ManageUsersMode

    data class Editing(
        val original: User?,
        val draft: UserDraft,
    ) : ManageUsersMode
}

/** The edit form's working copy — kept separate from [User] because a draft also carries a
 * plaintext [password] field the domain model never does (write-only, §11.4). */
data class UserDraft(
    val name: String = "",
    /** Role is a raw hub integer with no confirmed name mapping beyond `0` = disabled/no-such-user
     * at login time (protocol notes §2.3/§10) — edited as a plain number rather than a picker. */
    val role: Int = 1,
    val enabled: Boolean = true,
    val remoteAccessible: Boolean = true,
    /** New user: required. Existing user: blank means "keep the current password". */
    val password: String = "",
)

data class ManageUsersUiState(
    val users: kotlin.collections.List<User> = emptyList(),
    val mode: ManageUsersMode = ManageUsersMode.List,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class ManageUsersViewModel(
    private val getUsers: GetUsersUseCase,
    private val createUser: CreateUserUseCase,
    private val updateUser: UpdateUserUseCase,
    private val deleteUser: DeleteUserUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManageUsersUiState())
    val uiState: StateFlow<ManageUsersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getUsers()) {
                is DonaResult.Success -> _uiState.update { it.copy(isLoading = false, users = result.data) }
                is DonaResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.failure.message ?: "Failed to load users") }
            }
        }
    }

    fun startAddingUser() {
        _uiState.update { it.copy(mode = ManageUsersMode.Editing(original = null, draft = UserDraft())) }
    }

    fun startEditingUser(user: User) {
        _uiState.update {
            it.copy(
                mode =
                    ManageUsersMode.Editing(
                        original = user,
                        draft =
                            UserDraft(
                                name = user.name,
                                role = user.role,
                                enabled = user.enabled,
                                remoteAccessible = user.remoteAccessible,
                            ),
                    ),
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(mode = ManageUsersMode.List) }
    }

    fun updateDraft(transform: (UserDraft) -> UserDraft) {
        _uiState.update { state ->
            val editing = state.mode as? ManageUsersMode.Editing ?: return@update state
            state.copy(mode = editing.copy(draft = transform(editing.draft)))
        }
    }

    fun saveDraft() {
        val editing = _uiState.value.mode as? ManageUsersMode.Editing ?: return
        val draft = editing.draft
        val original = editing.original
        if (draft.name.isBlank()) return
        if (original == null && draft.password.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result =
                if (original == null) {
                    createUser(draft.name, draft.password, draft.role, draft.enabled, draft.remoteAccessible).map { }
                } else {
                    updateUser(
                        id = original.id,
                        name = draft.name,
                        role = draft.role,
                        enabled = draft.enabled,
                        remoteAccessible = draft.remoteAccessible,
                        newPassword = draft.password.ifBlank { null },
                    )
                }
            when (result) {
                is DonaResult.Success -> {
                    _uiState.update { it.copy(mode = ManageUsersMode.List) }
                    refresh()
                }
                is DonaResult.Error ->
                    _uiState.update { it.copy(errorMessage = result.failure.message ?: "Failed to save user") }
            }
        }
    }

    fun setEnabled(
        user: User,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            when (
                val result =
                    updateUser(
                        id = user.id,
                        name = user.name,
                        role = user.role,
                        enabled = enabled,
                        remoteAccessible = user.remoteAccessible,
                    )
            ) {
                is DonaResult.Success -> refresh()
                is DonaResult.Error ->
                    _uiState.update { it.copy(errorMessage = result.failure.message ?: "Failed to update user") }
            }
        }
    }

    fun delete(user: User) {
        viewModelScope.launch {
            when (val result = deleteUser(user.id)) {
                is DonaResult.Success -> refresh()
                is DonaResult.Error ->
                    _uiState.update { it.copy(errorMessage = result.failure.message ?: "Failed to delete user") }
            }
        }
    }
}
