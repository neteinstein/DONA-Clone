package com.neteinstein.donaclone.feature.ambiences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.neteinstein.donaclone.core.designsystem.component.SceneCard
import com.neteinstein.donaclone.core.model.Ambience
import org.koin.androidx.compose.koinViewModel

@Composable
fun AmbiencesRoute(viewModel: AmbiencesViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    AmbiencesScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onToggle = viewModel::toggle,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiencesScreen(
    uiState: AmbiencesUiState,
    onRefresh: () -> Unit,
    onToggle: (Ambience) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automations") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.ambiences.isEmpty() -> LoadingState(modifier = Modifier.padding(padding))
            uiState.errorMessage != null && uiState.ambiences.isEmpty() ->
                ErrorState(message = uiState.errorMessage, onRetry = onRefresh, modifier = Modifier.padding(padding))
            uiState.ambiences.isEmpty() ->
                EmptyState(
                    message = "No scenarios configured on this hub yet.",
                    icon = Icons.Filled.PlayCircle,
                    modifier = Modifier.padding(padding),
                )
            else ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .padding(padding)
                            .fillMaxSize(),
                ) {
                    items(uiState.ambiences, key = { it.id }) { ambience ->
                        SceneCard(
                            name = ambience.name,
                            icon = Icons.Filled.PlayCircle,
                            isPlaying = ambience.isPlaying,
                            onClick = { onToggle(ambience) },
                        )
                    }
                }
        }
    }
}
