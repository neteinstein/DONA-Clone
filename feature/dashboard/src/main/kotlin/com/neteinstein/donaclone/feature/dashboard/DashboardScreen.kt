package com.neteinstein.donaclone.feature.dashboard

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

data class DashboardDestination(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun DashboardRoute(
    onOpenDevices: () -> Unit,
    onOpenAmbiences: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.loggedOut) {
        onLoggedOut()
        return
    }

    DashboardScreen(
        uiState = uiState,
        destinations =
            listOf(
                DashboardDestination("Rooms & Devices", Icons.Filled.Lightbulb, onOpenDevices),
                DashboardDestination("Scenarios", Icons.Filled.PlayCircle, onOpenAmbiences),
                DashboardDestination("Settings", Icons.Filled.Settings, onOpenSettings),
            ),
        onLogout = viewModel::logout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    destinations: List<DashboardDestination>,
    onLogout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.houseName.ifBlank { "Home" }, style = MaterialTheme.typography.titleLarge)
                        if (uiState.userName.isNotBlank()) {
                            Text(
                                "Signed in as ${uiState.userName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            items(destinations) { destination ->
                DashboardCard(destination)
            }
        }
    }
}

@Composable
private fun DashboardCard(destination: DashboardDestination) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = destination.onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = destination.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

fun dashboardEnterTransition() = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
