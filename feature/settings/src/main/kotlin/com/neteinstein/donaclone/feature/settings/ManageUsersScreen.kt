package com.neteinstein.donaclone.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.User
import org.koin.androidx.compose.koinViewModel

@Composable
fun ManageUsersRoute(
    onBack: () -> Unit,
    viewModel: ManageUsersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ManageUsersScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onAddUser = viewModel::startAddingUser,
        onEditUser = viewModel::startEditingUser,
        onDeleteUser = viewModel::delete,
        onEnabledChanged = viewModel::setEnabled,
        onCancelEditing = viewModel::cancelEditing,
        onDraftChange = viewModel::updateDraft,
        onSave = viewModel::saveDraft,
    )
}

@Composable
fun ManageUsersScreen(
    uiState: ManageUsersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddUser: () -> Unit,
    onEditUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onEnabledChanged: (User, Boolean) -> Unit,
    onCancelEditing: () -> Unit,
    onDraftChange: ((UserDraft) -> UserDraft) -> Unit,
    onSave: () -> Unit,
) {
    when (val mode = uiState.mode) {
        ManageUsersMode.List ->
            ManageUsersListScreen(
                users = uiState.users,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onBack = onBack,
                onRetry = onRetry,
                onAddUser = onAddUser,
                onEditUser = onEditUser,
                onDeleteUser = onDeleteUser,
                onEnabledChanged = onEnabledChanged,
            )

        is ManageUsersMode.Editing ->
            EditUserScreen(
                draft = mode.draft,
                isNew = mode.original == null,
                onBack = onCancelEditing,
                onDraftChange = onDraftChange,
                onSave = onSave,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageUsersListScreen(
    users: List<User>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddUser: () -> Unit,
    onEditUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onEnabledChanged: (User, Boolean) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<User?>(null) }
    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteUser(user)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            title = { Text("Delete user?") },
            text = { Text("\"${user.name}\" will lose access to this hub immediately.") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddUser) {
                Icon(Icons.Filled.Add, contentDescription = "Add user")
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            when {
                isLoading && users.isEmpty() -> LoadingState()
                errorMessage != null && users.isEmpty() -> ErrorState(message = errorMessage, onRetry = onRetry)
                users.isEmpty() -> EmptyState(message = "No users configured on this hub yet.", icon = Icons.Filled.People)
                else ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        users.forEach { user ->
                            ListItem(
                                headlineContent = { Text(user.name) },
                                supportingContent = { Text(if (user.enabled) "Enabled" else "Disabled") },
                                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = user.enabled, onCheckedChange = { onEnabledChanged(user, it) })
                                        IconButton(onClick = { onEditUser(user) }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { pendingDelete = user }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(88.dp))
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditUserScreen(
    draft: UserDraft,
    isNew: Boolean,
    onBack: () -> Unit,
    onDraftChange: ((UserDraft) -> UserDraft) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add user" else "Edit user") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> onDraftChange { it.copy(name = name) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.password,
                onValueChange = { value -> onDraftChange { it.copy(password = value) } },
                label = { Text(if (isNew) "Password" else "New password (leave blank to keep current)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.role.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { role -> onDraftChange { it.copy(role = role) } } },
                label = { Text("Role") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = draft.enabled, onCheckedChange = { value -> onDraftChange { it.copy(enabled = value) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Remote access", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.remoteAccessible,
                    onCheckedChange = { value -> onDraftChange { it.copy(remoteAccessible = value) } },
                )
            }
            Spacer(Modifier.height(24.dp))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
