package com.neteinstein.donaclone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.common.map
import com.neteinstein.donaclone.core.domain.usecase.CreateUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.DeleteUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRolesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetUsersUseCase
import com.neteinstein.donaclone.core.domain.usecase.UpdateUserUseCase
import com.neteinstein.donaclone.core.model.Role
import com.neteinstein.donaclone.core.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        /** Why the last Save attempt didn't go through — either a validation rule the form itself
         * enforces, or the hub's own rejection. Shown on the form, since
         * [ManageUsersUiState.errorMessage] only ever surfaces on the *list* screen and a save
         * failure would otherwise be completely invisible. */
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : ManageUsersMode
}

/** The edit form's working copy — kept separate from [User] because a draft also carries a
 * plaintext [password] field the domain model never does (write-only, §11.4). */
data class UserDraft(
    val name: String = "",
    /** Null until the hub's role catalogue ([ManageUsersUiState.roles]) has loaded and seeded a
     * default (mirrors the hub's own web UI, which defaults to `roles[0]`). */
    val roleId: Int? = null,
    val enabled: Boolean = true,
    /** Matches the hub's own "add user" default (`settingsAddUserController.js`). */
    val remoteAccessible: Boolean = false,
    /** New user: required. Existing user: blank means "keep the current password". */
    val password: String = "",
    /** Existing user only — required alongside [password] to authorize the change; must be blank
     * when [password] is blank. */
    val oldPassword: String = "",
)

/** At least 6 characters with a lowercase letter, an uppercase letter, and a digit. */
private val PASSWORD_PATTERN = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$")

data class ManageUsersUiState(
    val users: kotlin.collections.List<User> = emptyList(),
    /** The hub's named role catalogue (`read role`, §11.4) — never hardcoded, since role names
     * are configurable per house. */
    val roles: kotlin.collections.List<Role> = emptyList(),
    val mode: ManageUsersMode = ManageUsersMode.List,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class ManageUsersViewModel(
    private val getUsers: GetUsersUseCase,
    private val getRoles: GetRolesUseCase,
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

            coroutineScope {
                val usersDeferred = async { getUsers() }
                val rolesDeferred = async { getRoles() }

                when (val result = usersDeferred.await()) {
                    is DonaResult.Success ->
                        _uiState.update {
                            it.copy(isLoading = false, users = result.data, roles = rolesDeferred.await().rolesOrEmpty())
                        }
                    is DonaResult.Error ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = result.failure.message ?: "Failed to load users")
                        }
                }
            }
        }
    }

    /** Best-effort — the role picker just falls back to showing raw ids if this fails. */
    private fun DonaResult<List<Role>>.rolesOrEmpty(): List<Role> =
        when (this) {
            is DonaResult.Success -> data
            is DonaResult.Error -> emptyList()
        }

    fun startAddingUser() {
        val defaultRoleId = _uiState.value.roles.firstOrNull()?.id
        _uiState.update { it.copy(mode = ManageUsersMode.Editing(original = null, draft = UserDraft(roleId = defaultRoleId))) }
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
                                roleId = user.role,
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
            state.copy(mode = editing.copy(draft = transform(editing.draft), error = null))
        }
    }

    private fun failEditing(message: String) =
        _uiState.update { state ->
            val editing = state.mode as? ManageUsersMode.Editing ?: return@update state
            state.copy(mode = editing.copy(error = message, isSaving = false))
        }

    fun saveDraft() {
        val editing = _uiState.value.mode as? ManageUsersMode.Editing ?: return
        if (editing.isSaving) return
        val draft = editing.draft
        val original = editing.original
        if (draft.name.isBlank()) return failEditing("Give this user a name.")
        val roleId = draft.roleId ?: return failEditing("Pick a role for this user.")
        if (original == null && draft.password.isBlank()) return failEditing("A new user needs a password.")
        if (draft.password.isNotBlank() && !PASSWORD_PATTERN.matches(draft.password)) {
            return failEditing("Password must be at least 6 characters and include an uppercase letter, a lowercase letter, and a number.")
        }
        if (original != null && draft.password.isNotBlank() && draft.oldPassword.isBlank()) {
            return failEditing("Enter the current password to set a new one.")
        }
        if (original != null && draft.password.isBlank() && draft.oldPassword.isNotBlank()) {
            return failEditing("Enter a new password, or clear the current password field.")
        }

        viewModelScope.launch {
            _uiState.update { state ->
                val current = state.mode as? ManageUsersMode.Editing ?: return@update state
                state.copy(mode = current.copy(error = null, isSaving = true), errorMessage = null)
            }
            val result =
                if (original == null) {
                    createUser(draft.name, draft.password, roleId, draft.enabled, draft.remoteAccessible).map { }
                } else {
                    updateUser(
                        id = original.id,
                        name = draft.name,
                        role = roleId,
                        enabled = draft.enabled,
                        remoteAccessible = draft.remoteAccessible,
                        newPassword = draft.password.ifBlank { null },
                        oldPassword = draft.oldPassword.ifBlank { null },
                    )
                }
            when (result) {
                is DonaResult.Success -> {
                    _uiState.update { it.copy(mode = ManageUsersMode.List) }
                    refresh()
                }
                is DonaResult.Error -> failEditing(result.failure.message ?: "Failed to save user")
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
