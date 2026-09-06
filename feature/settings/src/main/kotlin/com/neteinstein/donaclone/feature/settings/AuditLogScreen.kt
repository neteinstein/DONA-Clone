package com.neteinstein.donaclone.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.AuditLogEntry
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AuditLogRoute(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AuditLogScreen(
        uiState = uiState,
        onBack = onBack,
        onObjectIdFilterChanged = viewModel::onObjectIdFilterChanged,
        onDateRangeSelected = viewModel::onDateRangeSelected,
        onApplyFilters = viewModel::refresh,
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    uiState: AuditLogUiState,
    onBack: () -> Unit,
    onObjectIdFilterChanged: (String) -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    onApplyFilters: () -> Unit,
    onRetry: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AuditLogFilters(
                uiState = uiState,
                onObjectIdFilterChanged = onObjectIdFilterChanged,
                onPickDateRange = { showDatePicker = true },
                onApplyFilters = onApplyFilters,
            )
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && uiState.entries.isEmpty() -> LoadingState()
                    uiState.errorMessage != null && uiState.entries.isEmpty() ->
                        ErrorState(message = uiState.errorMessage, onRetry = onRetry)
                    uiState.entries.isEmpty() ->
                        EmptyState(message = "No audit log entries found", icon = Icons.Filled.History)
                    else ->
                        AuditLogList(
                            entries = uiState.entries,
                            userNames = uiState.userNames,
                            objectNames = uiState.objectNames,
                        )
                }
            }
        }
    }

    if (showDatePicker) {
        AuditLogDateRangeDialog(
            initialFrom = uiState.fromDate,
            initialTo = uiState.toDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                onDateRangeSelected(from, to)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun AuditLogFilters(
    uiState: AuditLogUiState,
    onObjectIdFilterChanged: (String) -> Unit,
    onPickDateRange: () -> Unit,
    onApplyFilters: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.objectIdFilter,
            onValueChange = onObjectIdFilterChanged,
            label = { Text("Object ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onPickDateRange, modifier = Modifier.weight(1f)) {
                Text(dateRangeLabel(uiState.fromDate, uiState.toDate))
            }
            Button(onClick = onApplyFilters) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun AuditLogList(
    entries: List<AuditLogEntry>,
    userNames: Map<Int, String>,
    objectNames: Map<Int, String>,
) {
    val formatter =
        remember {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneOffset.systemDefault())
        }
    var expandedEntryId by remember { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = AuditLogEntry::id) { entry ->
            val isExpanded = expandedEntryId == entry.id
            ListItem(
                headlineContent = { Text(entry.description ?: "Event${entry.type?.let { " ($it)" } ?: ""}") },
                supportingContent = {
                    Text(
                        buildString {
                            append(formatter.format(entry.date))
                            entry.objectId?.let { append(" • ").append(objectDisplayName(it, objectNames)) }
                        },
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                    )
                },
                modifier =
                    Modifier.clickable {
                        expandedEntryId = if (isExpanded) null else entry.id
                    },
            )
            AnimatedVisibility(visible = isExpanded) {
                AuditLogEntryDetails(entry = entry, formatter = formatter, userNames = userNames, objectNames = objectNames)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AuditLogEntryDetails(
    entry: AuditLogEntry,
    formatter: DateTimeFormatter,
    userNames: Map<Int, String>,
    objectNames: Map<Int, String>,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        DetailRow(label = "Event ID", value = entry.id.toString())
        DetailRow(label = "Type", value = entry.type?.toString() ?: "Unknown")
        DetailRow(label = "Object", value = entry.objectId?.let { objectDisplayName(it, objectNames) } ?: "—")
        DetailRow(label = "User", value = entry.userId?.let { userDisplayName(it, userNames) } ?: "—")
        DetailRow(label = "Timestamp", value = formatter.format(entry.date))
    }
}

private fun userDisplayName(
    userId: Int,
    userNames: Map<Int, String>,
): String = userNames[userId] ?: "User #$userId"

/** Best-effort — the hub doesn't document what `objectId` refers to, so an unmatched id is
 * shown as-is rather than assumed to be a device/room that's simply missing. */
private fun objectDisplayName(
    objectId: Int,
    objectNames: Map<Int, String>,
): String = objectNames[objectId] ?: "Object #$objectId"

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogDateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
) {
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialFrom?.toEpochMillisUtc(),
            initialSelectedEndDateMillis = initialTo?.toEpochMillisUtc(),
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        state.selectedStartDateMillis?.toLocalDateUtc(),
                        state.selectedEndDateMillis?.toLocalDateUtc(),
                    )
                },
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

private fun dateRangeLabel(
    from: LocalDate?,
    to: LocalDate?,
): String =
    when {
        from == null && to == null -> "Any date"
        from != null && to != null -> "$from – $to"
        from != null -> "From $from"
        else -> "Until $to"
    }

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
