package com.neteinstein.donaclone.feature.ambiences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
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
        removingTruncatesChain = viewModel::removingTruncatesChain,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onConsumeSaveMessage = {
            val shouldClose = uiState.closeAfterMessage
            viewModel.consumeSaveMessage()
            if (shouldClose) onDone()
        },
        onClose = onDone,
    )
}

/**
 * The "create a new automation" / "view and edit an existing automation" screen — name + enabled
 * toggle up top, then the hub's own trigger/action/condition/finalizer structure as four
 * editable sections. Tapping a section's "+" swaps the whole screen for [EntryConfigScreen] rather
 * than pushing a nav destination, since it's just refining state that lives in this same
 * [AutomationEditorViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutomationEditorScreen(
    uiState: AutomationEditorUiState,
    onNameChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onStartAdding: (AutomationSection) -> Unit,
    onCancelAdding: () -> Unit,
    onAddEntry: (AutomationSection, AutomationEntryDraft) -> Unit,
    onRemoveEntry: (AutomationSection, Long) -> Unit,
    removingTruncatesChain: (AutomationSection, Long) -> Boolean = { _, _ -> false },
    onSave: () -> Unit,
    onDelete: () -> Unit = {},
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
            title = { Text(if (uiState.closeAfterMessage) "Done" else "Couldn't save") },
            text = { Text(saveMessage) },
        )
    }

    var pendingRemoval by remember { mutableStateOf<Pair<AutomationSection, Long>?>(null) }
    pendingRemoval?.let { (section, entryId) ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveEntry(section, entryId)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
            title = { Text("Remove this action?") },
            text = { Text("This will also remove every action that comes after it in the chain.") },
        )
    }

    var showSaveConfirm by remember { mutableStateOf(false) }
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    onSave()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") } },
            title = { Text("Save this automation?") },
            text = {
                Text(
                    if (uiState.isEditing) {
                        "This will update \"${uiState.name}\" on the hub."
                    } else {
                        "This will create \"${uiState.name}\" on the hub."
                    },
                )
            },
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            title = { Text("Delete this automation?") },
            text = { Text("\"${uiState.name}\" will be permanently removed from the hub. This can't be undone.") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit automation" else "New automation") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = uiState.canDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete automation")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { showSaveConfirm = true }, enabled = uiState.canSave) { Text("Save") }
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
                label = { Text("Scenario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (uiState.enabled) "Enabled" else "Disabled", color = MaterialTheme.colorScheme.primary)
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
                // A scenario read back off the hub can easily have more entries than fit on one
                // line, so these wrap rather than squeezing (or hiding) the trailing "+" card.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    uiState.entriesBySection[section].orEmpty().forEach { entry ->
                        EntryChip(
                            entry = entry,
                            onRemove = {
                                if (removingTruncatesChain(section, entry.id)) {
                                    pendingRemoval = section to entry.id
                                } else {
                                    onRemoveEntry(section, entry.id)
                                }
                            },
                        )
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

/** The "Configure trigger/action/condition/finalizer" sub-screen: pick either a device (scoped
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
    var endHour by remember { mutableStateOf(23) }
    var endMinute by remember { mutableStateOf(59) }
    var daysOfWeek by remember { mutableStateOf((0..6).toSet()) }
    var lowerBoundText by remember { mutableStateOf("") }
    var upperBoundText by remember { mutableStateOf("") }
    var statusOn by remember { mutableStateOf(true) }
    var triggerEvent by remember { mutableStateOf(0) }
    var binaryOutOn by remember { mutableStateOf(true) }
    var shutterMode by remember { mutableStateOf(SHUTTER_MODE_OPEN) }
    var actionPercentageText by remember { mutableStateOf("100") }
    var withLast by remember { mutableStateOf(false) }
    var delaySecondsText by remember { mutableStateOf("0") }

    val floors = remember(rooms) { rooms.mapNotNull { it.floor }.distinct().sorted() }
    val roomsForFloor = remember(rooms, selectedFloor) { rooms.filter { selectedFloor == null || it.floor == selectedFloor } }
    val filteredDevices = remember(devices, selectedRoomId) { devices.filter { selectedRoomId == null || it.roomId == selectedRoomId } }

    val canSave = if (type == AutomationEntryType.BY_DEVICE) selectedDevice != null else true
    val showsRangeFields =
        type == AutomationEntryType.BY_DEVICE &&
            when (selectedDevice) {
                is Device.AnalogInput, is Device.Counter -> section != AutomationSection.ACTIONS
                is Device.Shutter, is Device.Dimmer -> section == AutomationSection.CONDITIONS
                else -> false
            }
    val showsStatusToggle =
        type == AutomationEntryType.BY_DEVICE && section == AutomationSection.CONDITIONS &&
            selectedDevice.let { it is Device.BinaryOutput || it is Device.BinaryInput || it is Device.Pulse }
    val showsEventPicker =
        type == AutomationEntryType.BY_DEVICE &&
            (section == AutomationSection.TRIGGERS || section == AutomationSection.FINALIZERS) &&
            selectedDevice is Device.BinaryInput
    val showsActionConfig = section == AutomationSection.ACTIONS && selectedDevice != null

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
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canSave,
                    onClick = {
                        val (actionCode, actionPercentage) =
                            if (showsActionConfig) {
                                actionCodeAndPercentageFor(selectedDevice, binaryOutOn, shutterMode, actionPercentageText)
                            } else {
                                null to null
                            }
                        val entry =
                            if (type == AutomationEntryType.BY_DEVICE) {
                                AutomationEntryDraft(
                                    id = 0,
                                    type = AutomationEntryType.BY_DEVICE,
                                    device = selectedDevice,
                                    lowerBound = if (showsRangeFields) lowerBoundText.toDoubleOrNull() else null,
                                    upperBound = if (showsRangeFields) upperBoundText.toDoubleOrNull() else null,
                                    statusOn = statusOn,
                                    event = if (showsEventPicker) triggerEvent else 0,
                                    actionCode = actionCode,
                                    actionPercentage = actionPercentage,
                                    withLast = if (showsActionConfig) withLast else false,
                                    delayFromLastSeconds = if (showsActionConfig) delaySecondsText.toIntOrNull() ?: 0 else 0,
                                )
                            } else if (section == AutomationSection.CONDITIONS) {
                                AutomationEntryDraft(
                                    id = 0,
                                    type = AutomationEntryType.TIMED,
                                    hour = hour,
                                    minute = minute,
                                    endHour = endHour,
                                    endMinute = endMinute,
                                    daysOfWeek = daysOfWeek,
                                )
                            } else {
                                AutomationEntryDraft(id = 0, type = AutomationEntryType.TIMED, hour = hour, minute = minute)
                            }
                        onSave(entry)
                    },
                ) { Text("Save") }
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
                            Text(if (option == AutomationEntryType.BY_DEVICE) "By device" else "Timed")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (type == AutomationEntryType.BY_DEVICE) {
                if (floors.isNotEmpty()) {
                    Text("Floors", style = MaterialTheme.typography.labelLarge)
                    ExposedDropdownMenuBox(expanded = floorExpanded, onExpandedChange = { floorExpanded = it }) {
                        OutlinedTextField(
                            value = selectedFloor?.let { "Floor $it" } ?: "Select a floor",
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
                                    text = { Text("Floor $floor") },
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

                Text("Rooms", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = roomsForFloor.firstOrNull { it.id == selectedRoomId }?.name ?: "Select a room",
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

                if (showsRangeFields) {
                    Spacer(Modifier.height(16.dp))
                    Text("Value range", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lowerBoundText,
                            onValueChange = { lowerBoundText = it },
                            label = { Text("Minimum") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = upperBoundText,
                            onValueChange = { upperBoundText = it },
                            label = { Text("Maximum") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }

                if (showsStatusToggle) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target state: " + if (statusOn) "On" else "Off")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = statusOn, onCheckedChange = { statusOn = it })
                    }
                }

                if (showsEventPicker) {
                    Spacer(Modifier.height(16.dp))
                    Text("Event", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Event 1", 1 to "Event 2").forEach { (value, label) ->
                            val selected = triggerEvent == value
                            TextButton(
                                onClick = { triggerEvent = value },
                                colors =
                                    if (selected) {
                                        ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        ButtonDefaults.textButtonColors()
                                    },
                            ) { Text(label) }
                        }
                    }
                }

                if (showsActionConfig) {
                    Spacer(Modifier.height(16.dp))
                    Text("Action", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    when (selectedDevice) {
                        is Device.BinaryOutput ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (binaryOutOn) "Turn on" else "Turn off")
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = binaryOutOn, onCheckedChange = { binaryOutOn = it })
                            }

                        is Device.Shutter -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(SHUTTER_MODE_OPEN to "Open", SHUTTER_MODE_CLOSE to "Close", SHUTTER_MODE_PERCENTAGE to "Percentage")
                                    .forEach { (value, label) ->
                                        val selected = shutterMode == value
                                        TextButton(
                                            onClick = { shutterMode = value },
                                            colors =
                                                if (selected) {
                                                    ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                                } else {
                                                    ButtonDefaults.textButtonColors()
                                                },
                                        ) { Text(label) }
                                    }
                            }
                            if (shutterMode == SHUTTER_MODE_PERCENTAGE) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = actionPercentageText,
                                    onValueChange = { actionPercentageText = it },
                                    label = { Text("Percentage (0-100)") },
                                    modifier = Modifier.width(160.dp),
                                    singleLine = true,
                                )
                            }
                        }

                        is Device.Dimmer ->
                            OutlinedTextField(
                                value = actionPercentageText,
                                onValueChange = { actionPercentageText = it },
                                label = { Text("Percentage (0-100)") },
                                modifier = Modifier.width(160.dp),
                                singleLine = true,
                            )

                        else -> Text("This device fires with a single action.", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("At the same time as the previous one")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = withLast, onCheckedChange = { withLast = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = delaySecondsText,
                        onValueChange = { delaySecondsText = it },
                        label = { Text("Delay (seconds)") },
                        modifier = Modifier.width(160.dp),
                        singleLine = true,
                    )
                }
            } else {
                Text("Start time:", style = MaterialTheme.typography.labelLarge)
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

                if (section == AutomationSection.CONDITIONS) {
                    Spacer(Modifier.height(16.dp))
                    Text("End time:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endHour.toString().padStart(2, '0'),
                            onValueChange = { text -> text.toIntOrNull()?.let { endHour = it.coerceIn(0, 23) } },
                            label = { Text("h") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = endMinute.toString().padStart(2, '0'),
                            onValueChange = { text -> text.toIntOrNull()?.let { endMinute = it.coerceIn(0, 59) } },
                            label = { Text("m") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Days of the week", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_LABELS.forEachIndexed { index, label ->
                            val selected = index in daysOfWeek
                            TextButton(
                                onClick = {
                                    daysOfWeek = if (selected) daysOfWeek - index else daysOfWeek + index
                                },
                                colors =
                                    if (selected) {
                                        ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        ButtonDefaults.textButtonColors()
                                    },
                            ) { Text(label) }
                        }
                    }
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

/** Monday..Sunday, matching [AutomationEntryDraft.daysOfWeek]'s 0=Monday..6=Sunday convention. */
private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// Shutter.Action wire codes (§4/§11.2): CLOSE=0, OPEN=1, PERCENTAGE=2. Dimmer.Action's
// PERCENTAGE=2 (the only code this UI ever sends for a dimmer) happens to share the same value.
private const val SHUTTER_MODE_CLOSE = 0
private const val SHUTTER_MODE_OPEN = 1
private const val SHUTTER_MODE_PERCENTAGE = 2
private const val DIMMER_ACTION_PERCENTAGE = 2

/** Reads this screen's action-config UI state into the `(action, percentage)` pair
 * [AutomationEntryDraft.actionCode]/[AutomationEntryDraft.actionPercentage] expect — `null` for a
 * device kind [EntryConfigScreen] doesn't show a picker for (e.g. [Device.Pulse], which only has
 * one `action` code), so [AutomationMapping]'s per-device-type default kicks in instead. */
private fun actionCodeAndPercentageFor(
    device: Device?,
    binaryOutOn: Boolean,
    shutterMode: Int,
    percentageText: String,
): Pair<Int?, Int?> =
    when (device) {
        is Device.BinaryOutput -> (if (binaryOutOn) 1 else 0) to null
        is Device.Shutter ->
            if (shutterMode == SHUTTER_MODE_PERCENTAGE) {
                SHUTTER_MODE_PERCENTAGE to (percentageText.toIntOrNull()?.coerceIn(0, 100) ?: 100)
            } else {
                shutterMode to null
            }
        is Device.Dimmer -> DIMMER_ACTION_PERCENTAGE to (percentageText.toIntOrNull()?.coerceIn(0, 100) ?: 100)
        else -> null to null
    }

private fun configureTitleFor(section: AutomationSection): String =
    when (section) {
        AutomationSection.TRIGGERS -> "Configure trigger"
        AutomationSection.ACTIONS -> "Configure action"
        AutomationSection.CONDITIONS -> "Configure condition"
        AutomationSection.FINALIZERS -> "Configure finalizer"
    }

private fun typeLabelFor(section: AutomationSection): String =
    when (section) {
        AutomationSection.TRIGGERS -> "Trigger type"
        AutomationSection.CONDITIONS -> "Condition type"
        AutomationSection.FINALIZERS -> "Finalizer type"
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
