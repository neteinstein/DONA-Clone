package com.neteinstein.donaclone.feature.login

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.koin.androidx.compose.koinViewModel

/**
 * The app-level lock shown on cold start / resume-from-background when fingerprint unlock is
 * turned on (see `MainActivityViewModel.isLocked`). Falls back to the normal [LoginRoute] form
 * whenever biometric auth isn't available, fails, is cancelled, or the user asks for it via the
 * negative button — biometric never fully replaces manual login.
 */
@Composable
fun BiometricLockRoute(
    onUnlocked: () -> Unit,
    onManageHouses: () -> Unit,
    viewModel: BiometricLockViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) onUnlocked()
    }

    if (uiState.useFallback || activity == null) {
        // Credentials are edited on the Houses screen, so tapping a login field goes wherever
        // this surface's "manage houses" goes — the lock screen has no navigation of its own.
        LoginRoute(
            onLoggedIn = onUnlocked,
            onManageHouses = onManageHouses,
            onEditHouse = { onManageHouses() },
        )
        return
    }

    LaunchedEffect(Unit) {
        val canAuthenticate =
            BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.useFallbackLogin()
        } else {
            showBiometricPrompt(
                activity = activity,
                onSuccess = viewModel::onBiometricSucceeded,
                onFailure = viewModel::useFallbackLogin,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (uiState.isAuthenticating) {
            CircularProgressIndicator()
        } else {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Unlock with fingerprint",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            TextButton(onClick = viewModel::useFallbackLogin, modifier = Modifier.padding(top = 8.dp)) {
                Text("Use password instead")
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    val promptInfo =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle("Unlock DONA")
            .setSubtitle("Use your fingerprint to sign back in")
            .setNegativeButtonText("Use password instead")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) = onFailure()
            },
        )
    prompt.authenticate(promptInfo)
}
