@file:OptIn(ExperimentalMaterial3Api::class)

package com.neteinstein.donaclone.feature.login

import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neteinstein.donaclone.core.designsystem.component.EmptyState
import com.neteinstein.donaclone.core.model.House
import kotlinx.coroutines.delay
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
            // Let the button's success morph (spinner -> checkmark) actually be seen before the
            // screen navigates away — see MorphingLoginButton below.
            delay(LOGIN_SUCCESS_ANIMATION_MILLIS)
            viewModel.consumeLoginSucceeded()
            onLoggedIn()
        }
    }

    // Coming back from the background after a failed attempt shouldn't require tapping "Log in"
    // again — retry automatically once credentials are still filled in.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.retryLoginIfNeeded()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

private const val LOGIN_SUCCESS_ANIMATION_MILLIS = 450L

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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
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
            val phase =
                when {
                    uiState.loginSucceeded -> LoginButtonPhase.SUCCESS
                    uiState.isLoading -> LoginButtonPhase.LOADING
                    else -> LoginButtonPhase.IDLE
                }
            MorphingLoginButton(
                phase = phase,
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        IconButton(
            onClick = onManageHouses,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
        ) {
            Icon(Icons.Filled.Home, contentDescription = "Manage houses")
        }
    }
}

private enum class LoginButtonPhase { IDLE, LOADING, SUCCESS }

/** A submit button that collapses from a full-width pill into a small circle while [phase] is
 * [LoginButtonPhase.LOADING] (showing a spinner), then morphs the spinner into a checkmark on
 * [LoginButtonPhase.SUCCESS] — reverting smoothly back to the idle pill if login fails. */
@Composable
private fun MorphingLoginButton(
    phase: LoginButtonPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsed = phase != LoginButtonPhase.IDLE
    val cornerRadius by animateDpAsState(
        targetValue = if (collapsed) 24.dp else 12.dp,
        animationSpec = tween(300),
        label = "login-button-corner",
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val circleSize = 48.dp
        val widthFraction by animateFloatAsState(
            targetValue = if (collapsed) (circleSize / maxWidth) else 1f,
            animationSpec = tween(300),
            label = "login-button-width",
        )

        Button(
            onClick = onClick,
            enabled = phase == LoginButtonPhase.IDLE,
            shape = RoundedCornerShape(cornerRadius),
            contentPadding = PaddingValues(0.dp),
            modifier =
                Modifier
                    .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
                    .height(circleSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                AnimatedContent(targetState = phase, label = "login-button-content") { p ->
                    when (p) {
                        LoginButtonPhase.IDLE -> Text("Log in")
                        LoginButtonPhase.LOADING ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        LoginButtonPhase.SUCCESS ->
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                    }
                }
            }
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
