package com.neteinstein.donaclone.feature.ambiences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AutomationEditorRoute(
    onDone: () -> Unit,
    ambienceId: Int? = null,
    viewModel: AutomationEditorViewModel = koinViewModel { parametersOf(ambienceId) },
) {
    val uiState by viewModel.uiState.collectAsState()

    AutomationEditorScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onEnabledChange = viewModel::onEnabledChange,
        onStartAdding = viewModel::startAdding,
        onCancelAdding = viewModel::cancelAdding,
        onAddEntry = viewModel::addEntry,
        onRemoveEntry = viewModel::removeEntry,
        onSave = viewModel::save,
        onConsumeSaveMessage = viewModel::consumeSaveMessage,
        onClose = onDone,
    )
}

/**
 * The "create a new automation" / "view and edit an existing automation" screen — name + enabled
 * toggle up top, then the hub's own Iniciadores/Ações/Condições/Finalizadores structure as four
 * editable sections. Tapping a section's "+" swaps the whole screen for [EntryConfigScreen] rather
 * than pushing a nav destination, since it's just refining state that lives in this same
 * [AutomationEditorViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditorScreen(
    uiState: AutomationEditorUiState,
    onNameChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onStartAdding: (AutomationSection) -> Unit,
    onCancelAdding: () -> Unit,
    onAddEntry: (AutomationSection, AutomationEntryDraft) -> Unit,
    onRemoveEntry: (AutomationSection, Long) -> Unit,
    onSave: () -> Unit,
    onConsumeSaveMessage: () -> Unit,
    onClose: () -> Unit,
) {
    val editingSection = uiState.editingSection
    if (editingSection != null) {
        EntryConfigScreen(
            section = editingSection,
            rooms = uiState.rooms,
            devices = uiState.devices,
            onCancel = onCancelAdding,
            onSave = { entry -> onAddEntry(editingSection, entry) },
        )
        return
    }

    val saveMessage = uiState.saveMessage
    if (saveMessage != null) {
        AlertDialog(
            onDismissRequest = onConsumeSaveMessage,
            confirmButton = { TextButton(onClick = onConsumeSaveMessage) { Text("OK") } },
            title = { Text("Can't save yet") },
            text = { Text(saveMessage) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit automation" else "New automation") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onSave, enabled = uiState.canSave) { Text("Guardar") }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Cenário") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (uiState.enabled) "Ativado" else "Desativado", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Switch(checked = uiState.enabled, onCheckedChange = onEnabledChange)
            }

            AutomationSection.entries.forEach { section ->
                Spacer(Modifier.height(24.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    uiState.entriesBySection[section].orEmpty().forEach { entry ->
                        EntryChip(entry = entry, onRemove = { onRemoveEntry(section, entry.id) })
                    }
                    AddEntryCard(onClick = { onStartAdding(section) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EntryChip(
    entry: AutomationEntryDraft,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = if (entry.type == AutomationEntryType.TIMED) Icons.Filled.AccessTime else iconForDevice(entry.device),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(entry.summary, color = MaterialTheme.colorScheme.onPrimaryContainer)
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AddEntryCard(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .size(width = 96.dp, height = 96.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The "Configurar iniciador/ação/condição/finalizador" sub-screen: pick either a device (scoped
 * by an optional floor/room filter) or a fixed time of day, then hand the resulting draft back to
 * [AutomationEditorScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryConfigScreen(
    section: AutomationSection,
    rooms: List<Division>,
    devices: List<Device>,
    onCancel: () -> Unit,
    onSave: (AutomationEntryDraft) -> Unit,
) {
    val allowsTimed = section != AutomationSection.ACTIONS
    var type by remember { mutableStateOf(AutomationEntryType.BY_DEVICE) }
    var floorExpanded by remember { mutableStateOf(false) }
    var selectedFloor by remember { mutableStateOf<Int?>(null) }
    var roomExpanded by remember { mutableStateOf(false) }
    var selectedRoomId by remember { mutableStateOf<Int?>(null) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var hour by remember { mutableStateOf(0) }
    var minute by remember { mutableStateOf(0) }

    val floors = remember(rooms) { rooms.mapNotNull { it.floor }.distinct().sorted() }
    val roomsForFloor = remember(rooms, selectedFloor) { rooms.filter { selectedFloor == null || it.floor == selectedFloor } }
    val filteredDevices = remember(devices, selectedRoomId) { devices.filter { selectedRoomId == null || it.roomId == selectedRoomId } }

    val canSave = if (type == AutomationEntryType.BY_DEVICE) selectedDevice != null else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(configureTitleFor(section)) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canSave,
                    onClick = {
                        val entry =
                            if (type == AutomationEntryType.BY_DEVICE) {
                                AutomationEntryDraft(id = 0, type = AutomationEntryType.BY_DEVICE, device = selectedDevice)
                            } else {
                                AutomationEntryDraft(id = 0, type = AutomationEntryType.TIMED, hour = hour, minute = minute)
                            }
                        onSave(entry)
                    },
                ) { Text("Guardar") }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (allowsTimed) {
                Text(typeLabelFor(section), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutomationEntryType.entries.forEach { option ->
                        val selected = type == option
                        TextButton(
                            onClick = { type = option },
                            colors =
                                if (selected) {
                                    ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                        ) {
                            Text(if (option == AutomationEntryType.BY_DEVICE) "Por dispositivo" else "Temporizado")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (type == AutomationEntryType.BY_DEVICE) {
                if (floors.isNotEmpty()) {
                    Text("Pisos", style = MaterialTheme.typography.labelLarge)
                    ExposedDropdownMenuBox(expanded = floorExpanded, onExpandedChange = { floorExpanded = it }) {
                        OutlinedTextField(
                            value = selectedFloor?.let { "Piso $it" } ?: "Selecione um piso",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = floorExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        )
                        ExposedDropdownMenu(expanded = floorExpanded, onDismissRequest = { floorExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("All floors") },
                                onClick = {
                                    selectedFloor = null
                                    selectedRoomId = null
                                    floorExpanded = false
                                },
                            )
                            floors.forEach { floor ->
                                DropdownMenuItem(
                                    text = { Text("Piso $floor") },
                                    onClick = {
                                        selectedFloor = floor
                                        selectedRoomId = null
                                        floorExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text("Divisões", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = roomsForFloor.firstOrNull { it.id == selectedRoomId }?.name ?: "Selecione uma divisão",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                    )
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All rooms") },
                            onClick = {
                                selectedRoomId = null
                                roomExpanded = false
                            },
                        )
                        roomsForFloor.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.name) },
                                onClick = {
                                    selectedRoomId = room.id
                                    roomExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                ) {
                    items(filteredDevices, key = { it.id }) { device ->
                        DevicePickerCell(
                            device = device,
                            selected = selectedDevice?.id == device.id,
                            onClick = { selectedDevice = device },
                        )
                    }
                }
            } else {
                Text("Tempo de início da ação:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour.toString().padStart(2, '0'),
                        onValueChange = { text -> text.toIntOrNull()?.let { hour = it.coerceIn(0, 23) } },
                        label = { Text("h") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = { text -> text.toIntOrNull()?.let { minute = it.coerceIn(0, 59) } },
                        label = { Text("m") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicePickerCell(
    device: Device,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = iconForDevice(device),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun configureTitleFor(section: AutomationSection): String =
    when (section) {
        AutomationSection.TRIGGERS -> "Configurar iniciador de cenário"
        AutomationSection.ACTIONS -> "Configurar ação"
        AutomationSection.CONDITIONS -> "Configurar condição"
        AutomationSection.FINALIZERS -> "Configurar finalizador de cenário"
    }

private fun typeLabelFor(section: AutomationSection): String =
    when (section) {
        AutomationSection.TRIGGERS -> "Tipo de iniciador"
        AutomationSection.CONDITIONS -> "Tipo de condicionador"
        AutomationSection.FINALIZERS -> "Tipo de finalizador"
        AutomationSection.ACTIONS -> ""
    }

/** A small, local icon mapping by [Device] subtype only — deliberately simpler than
 * `feature.devices`'s own `DeviceIcons.kt` (that lives in a sibling module this one doesn't, and
 * shouldn't, depend on) since this screen only needs "something recognizable", not the full
 * free-text-category catalogue. */
private fun iconForDevice(device: Device?): ImageVector =
    when (device) {
        is Device.BinaryOutput, is Device.Dimmer -> Icons.Filled.Lightbulb
        is Device.Shutter -> Icons.Filled.VerticalAlignCenter
        is Device.BinaryInput -> Icons.Filled.DoorFront
        is Device.AnalogInput, is Device.Counter -> Icons.Filled.Bolt
        is Device.Pulse -> Icons.Filled.Sensors
        else -> Icons.Filled.DeviceUnknown
    }
