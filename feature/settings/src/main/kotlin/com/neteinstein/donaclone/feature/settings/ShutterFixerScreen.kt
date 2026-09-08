package com.neteinstein.donaclone.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.designsystem.component.ErrorState
import com.neteinstein.donaclone.core.designsystem.component.LoadingState
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.model.shutterStateLabel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ShutterFixerRoute(
    onBack: () -> Unit,
    viewModel: ShutterFixerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ShutterFixerScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onInvertedChanged = viewModel::setInverted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShutterFixerScreen(
    uiState: ShutterFixerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onInvertedChanged: (Int, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shutter Fixer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            when {
                uiState.isLoading && uiState.shutters.isEmpty() -> LoadingState()
                uiState.errorMessage != null && uiState.shutters.isEmpty() ->
                    ErrorState(message = uiState.errorMessage, onRetry = onRetry)
                uiState.shutters.isEmpty() ->
                    EmptyState(message = "This hub doesn't report any shutters.", icon = Icons.Filled.Blinds)
                else ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text =
                                "Tick a shutter that opens when you tell it to close, or shows the wrong position. " +
                                    "Its readings and commands are then mirrored throughout the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        uiState.shutters.forEach { shutter ->
                            val inverted = shutter.id in uiState.invertedIds
                            ListItem(
                                headlineContent = { Text(shutter.name) },
                                supportingContent = { Text(shutter.supportingLabel(uiState.rooms)) },
                                leadingContent = { Icon(Icons.Filled.Blinds, contentDescription = null) },
                                trailingContent = {
                                    Checkbox(
                                        checked = inverted,
                                        onCheckedChange = { checked -> onInvertedChanged(shutter.id, checked) },
                                    )
                                },
                            )
                        }
                    }
            }
        }
    }
}

/** Room name plus the position as the app currently reads it — already corrected by any tick, so
 * the row is the quickest way to confirm the fix landed the right way round. */
private fun Device.Shutter.supportingLabel(rooms: List<Division>): String {
    val roomName = rooms.find { it.id == roomId }?.name
    val state = shutterStateLabel(percentage)
    return if (roomName.isNullOrBlank()) state else "$roomName · $state"
}
