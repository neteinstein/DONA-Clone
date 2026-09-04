package com.neteinstein.donaclone.feature.settings

import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
    }

    SettingsScreen(
        uiState = uiState,
        onManageHouses = onManageHouses,
        onLogout = viewModel::logout,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onBiometricEnabledChanged = viewModel::onBiometricEnabledChanged,
        onUpdateClicked = viewModel::onUpdateClicked,
        onEnableSideloadingClicked = viewModel::onEnableSideloadingClicked,
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
    onUpdateClicked: () -> Unit = {},
    onEnableSideloadingClicked: () -> Unit = {},
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

            SectionHeader("Updates")
            UpdateSection(
                status = uiState.updateStatus,
                onUpdateClicked = onUpdateClicked,
                onEnableSideloadingClicked = onEnableSideloadingClicked,
            )

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

/**
 * Button + live status for [UpdateStatus] - checks GitHub Releases and, if allowed, downloads and
 * installs a newer build (see [SettingsViewModel.onUpdateClicked]). [KeepScreenOnWhile] keeps the
 * screen awake while a check/download is in flight - both run on a plain coroutine, not a
 * background job, so nothing here survives the app leaving the foreground; keeping the screen on
 * for that whole stretch is what actually prevents that, right up through the moment
 * [SettingsViewModel] fires the install prompt.
 */
@Composable
private fun UpdateSection(
    status: UpdateStatus,
    onUpdateClicked: () -> Unit,
    onEnableSideloadingClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val isBusy = status is UpdateStatus.Checking || status is UpdateStatus.Downloading
        KeepScreenOnWhile(keepOn = isBusy)

        Button(onClick = onUpdateClicked, enabled = !isBusy) {
            Text(text = "Check for updates")
        }

        when (status) {
            is UpdateStatus.Idle -> Unit
            is UpdateStatus.Checking -> UpdateStatusRow(text = "Checking for updates…")
            is UpdateStatus.Downloading -> UpdateStatusRow(text = "Downloading update…")
            is UpdateStatus.UpToDate ->
                Text(
                    text = "You're on the latest version (${status.currentVersionName}).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.UpdateAvailable ->
                Text(
                    text = "Version ${status.update.versionName} is available. Tap to update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.Failed ->
                Text(
                    text = "Update check failed: ${status.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.SideloadingBlocked ->
                SideloadingWarning(onActionClick = onEnableSideloadingClicked)
        }
    }
}

@Composable
private fun SideloadingWarning(
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Allow this app to install updates to enable this action.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onActionClick) {
                Text(text = "Allow")
            }
        }
    }
}

@Composable
private fun UpdateStatusRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * Sets [android.view.View.setKeepScreenOn] for as long as [keepOn] is true, and always clears it
 * on dispose - including when this leaves composition entirely (e.g. the user backs out of
 * Settings mid-download) - so the flag can never get stuck on past the update flow that requested
 * it.
 */
@Composable
private fun KeepScreenOnWhile(keepOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}
