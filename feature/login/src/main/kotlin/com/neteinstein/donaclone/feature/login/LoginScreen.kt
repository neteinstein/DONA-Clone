@file:OptIn(ExperimentalMaterial3Api::class)

package com.neteinstein.donaclone.feature.login

import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.model.House
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginRoute(
    onLoggedIn: () -> Unit,
    onManageHouses: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val biometricAvailable =
        remember {
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    LaunchedEffect(uiState.loginSucceeded, uiState.showBiometricOptInPrompt) {
        if (uiState.loginSucceeded && !uiState.showBiometricOptInPrompt) {
            viewModel.consumeLoginSucceeded()
            onLoggedIn()
        }
    }

    LoginScreen(
        uiState = uiState,
        onSelectHouse = viewModel::selectHouse,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::login,
        onManageHouses = onManageHouses,
    )

    if (uiState.showBiometricOptInPrompt) {
        if (biometricAvailable) {
            AlertDialog(
                onDismissRequest = { viewModel.onBiometricOptInResult(false) },
                title = { Text("Enable fingerprint unlock?") },
                text = { Text("Skip typing your password next time — unlock the app with your fingerprint instead.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.onBiometricOptInResult(true) }) { Text("Enable") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onBiometricOptInResult(false) }) { Text("Not now") }
                },
            )
        } else {
            LaunchedEffect(Unit) { viewModel.onBiometricOptInResult(false) }
        }
    }
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onSelectHouse: (House) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onManageHouses: () -> Unit,
) {
    if (uiState.houses.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EmptyState(
                message = "No houses configured yet. Add your DPU's address to get started.",
                icon = Icons.Filled.Home,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onManageHouses,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
            ) {
                Text("Add a house")
            }
        }
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Log in to your home",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        HouseDropdown(
            houses = uiState.houses,
            selected = uiState.selectedHouse,
            onSelect = onSelectHouse,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(visible = uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLoginClick,
            enabled = !uiState.isLoading,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Log in")
            }
        }

        TextButton(
            onClick = onManageHouses,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Manage houses")
        }
    }
}

@Composable
private fun HouseDropdown(
    houses: List<House>,
    selected: House?,
    onSelect: (House) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("House") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            houses.forEach { house ->
                DropdownMenuItem(
                    text = { Text(house.name) },
                    onClick = {
                        onSelect(house)
                        expanded = false
                    },
                )
            }
        }
    }
}
