package com.neteinstein.donaclone.feature.settings

import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.model.ThemeMode
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(
    onManageHouses: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.loggedOut) {
        onLoggedOut()
        return
    }

    SettingsScreen(
        uiState = uiState,
        onManageHouses = onManageHouses,
        onLogout = viewModel::logout,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onBiometricEnabledChanged = viewModel::onBiometricEnabledChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onManageHouses: () -> Unit,
    onLogout: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onBiometricEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val biometricAvailable =
        remember {
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            ListItem(
                headlineContent = { Text(uiState.houseName.ifBlank { "No house selected" }) },
                supportingContent = { Text("Signed in as ${uiState.userName.ifBlank { "unknown" }}") },
                leadingContent = { Icon(Icons.Filled.Home, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Manage houses") },
                supportingContent = { Text("Add, edit or remove connection profiles") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageHouses),
            )

            SectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            if (biometricAvailable) {
                SectionHeader("Security")
                ListItem(
                    headlineContent = { Text("Unlock with fingerprint") },
                    supportingContent = { Text("Require a fingerprint scan to open the app") },
                    trailingContent = {
                        Switch(checked = uiState.biometricEnabled, onCheckedChange = onBiometricEnabledChanged)
                    },
                )
            }

            ListItem(
                headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(onClick = onLogout),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}
