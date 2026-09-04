package com.neteinstein.donaclone.feature.houses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import org.koin.androidx.compose.koinViewModel

@Composable
fun HousesRoute(
    onDone: () -> Unit,
    viewModel: HousesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    HousesScreen(
        uiState = uiState,
        onBack = onDone,
        onAddHouse = viewModel::startAddingHouse,
        onEditHouse = viewModel::startEditingHouse,
        onDeleteHouse = viewModel::delete,
        onCancelEditing = viewModel::cancelEditing,
        onDraftChange = viewModel::updateDraft,
        onApplyDiscovered = viewModel::applyDiscoveredHouse,
        onSave = viewModel::saveDraft,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousesScreen(
    uiState: HousesUiState,
    onBack: () -> Unit,
    onAddHouse: () -> Unit,
    onEditHouse: (House) -> Unit,
    onDeleteHouse: (House) -> Unit,
    onCancelEditing: () -> Unit,
    onDraftChange: ((House) -> House) -> Unit,
    onApplyDiscovered: (DiscoveredHouse) -> Unit,
    onSave: () -> Unit,
) {
    when (val mode = uiState.mode) {
        HousesMode.List ->
            HousesListScreen(
                houses = uiState.houses,
                onBack = onBack,
                onAddHouse = onAddHouse,
                onEditHouse = onEditHouse,
                onDeleteHouse = onDeleteHouse,
            )

        is HousesMode.Editing ->
            EditHouseScreen(
                draft = mode.draft,
                isNew = mode.original == null,
                discovered = uiState.discovered,
                isDiscovering = uiState.isDiscovering,
                onBack = onCancelEditing,
                onDraftChange = onDraftChange,
                onApplyDiscovered = onApplyDiscovered,
                onSave = onSave,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HousesListScreen(
    houses: List<House>,
    onBack: () -> Unit,
    onAddHouse: () -> Unit,
    onEditHouse: (House) -> Unit,
    onDeleteHouse: (House) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Houses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHouse) {
                Icon(Icons.Filled.Add, contentDescription = "Add house")
            }
        },
    ) { padding ->
        if (houses.isEmpty()) {
            EmptyState(
                message = "No houses yet. Add your DPU's local IP or DNS address to connect.",
                icon = Icons.Filled.Home,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.padding(padding)) {
            items(houses, key = { it.name }) { house ->
                ListItem(
                    headlineContent = { Text(house.name) },
                    supportingContent = { Text(house.localIp ?: house.dns ?: "No address configured") },
                    leadingContent = { Icon(Icons.Filled.Home, contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEditHouse(house) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDeleteHouse(house) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHouseScreen(
    draft: House,
    isNew: Boolean,
    discovered: List<DiscoveredHouse>,
    isDiscovering: Boolean,
    onBack: () -> Unit,
    onDraftChange: ((House) -> House) -> Unit,
    onApplyDiscovered: (DiscoveredHouse) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add house" else "Edit house") },
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

            if (isDiscovering || discovered.isNotEmpty()) {
                Text("Devices found on your network", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                discovered.forEach { found ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(found.ip) },
                            supportingContent = { Text("${found.hubType} · ${found.serialNumber ?: "unknown serial"}") },
                            leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                            modifier = Modifier.clickable { onApplyDiscovered(found) },
                        )
                    }
                }
                if (isDiscovering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning your local network…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = draft.localIp.orEmpty(),
                onValueChange = { value -> onDraftChange { it.copy(localIp = value.ifBlank { null }) } },
                label = { Text("Local IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Secure local connection (wss)", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.secureLocalIp,
                    onCheckedChange = { value -> onDraftChange { it.copy(secureLocalIp = value) } },
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.dns.orEmpty(),
                onValueChange = { value -> onDraftChange { it.copy(dns = value.ifBlank { null }) } },
                label = { Text("DNS / DDNS address (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Secure remote connection (wss)", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.secureDns,
                    onCheckedChange = { value -> onDraftChange { it.copy(secureDns = value) } },
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.username,
                onValueChange = { value -> onDraftChange { it.copy(username = value) } },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft.password,
                onValueChange = { value -> onDraftChange { it.copy(password = value) } },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                    androidx.compose.ui.text.input
                        .PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
