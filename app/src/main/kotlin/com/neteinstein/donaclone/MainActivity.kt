package com.neteinstein.donaclone

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neteinstein.donaclone.core.designsystem.theme.DonaTheme
import com.neteinstein.donaclone.core.model.ThemeMode
import com.neteinstein.donaclone.feature.houses.HousesRoute
import com.neteinstein.donaclone.feature.login.BiometricLockRoute
import com.neteinstein.donaclone.navigation.DonaNavHost
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel

/** A [FragmentActivity], not a plain [androidx.activity.ComponentActivity], because
 * [androidx.biometric.BiometricPrompt] (the app-level fingerprint lock) requires one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainActivityViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                            Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                            else -> Unit
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val darkTheme =
                when (uiState.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

            DonaTheme(darkTheme = darkTheme) {
                val snackbarHostState = remember { SnackbarHostState() }

                // Force-dismisses the banner the moment connectivity/reachability recovers, even
                // while the effect below is suspended inside showSnackbar.
                LaunchedEffect(snackbarHostState) {
                    snapshotFlow { uiState.showConnectivityBanner }.collect { show ->
                        if (!show) snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }
                // Shows the banner whenever needed; re-shows immediately after a failed manual
                // Retry (the flag stays true) without waiting for a fresh state change. A manual
                // dismiss silences it until connectivity actually recovers (showConnectivityBanner
                // goes false), rather than re-showing on the very next loop iteration.
                LaunchedEffect(snackbarHostState) {
                    while (true) {
                        viewModel.uiState.first { it.showConnectivityBanner }
                        val result =
                            snackbarHostState.showSnackbar(
                                message = "Can't reach your home hub",
                                actionLabel = "Retry",
                                withDismissAction = true,
                                duration = SnackbarDuration.Indefinite,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.retryConnectionNow()
                        } else {
                            viewModel.uiState.first { !it.showConnectivityBanner }
                        }
                    }
                }

                // Every top-level screen already handles its own top inset (a Scaffold with a
                // TopAppBar, or an explicit statusBarsPadding() on Login/BiometricLock) — exclude
                // the status bar here, or the top bar on every screen ends up pushed down by its
                // height a second time. Likewise exclude the navigation bar: MainScreen's own
                // Scaffold + bottom nav bar already reserves that space, so reserving it again
                // here would push the whole post-login shell (bottom bar included) up, leaving a
                // gap between it and the true bottom of the screen.
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets =
                        WindowInsets.safeDrawing
                            .exclude(WindowInsets.statusBars)
                            .exclude(WindowInsets.navigationBars),
                ) { padding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (uiState.isLocked) {
                                LockedContent(onUnlocked = viewModel::onUnlocked)
                            } else {
                                DonaNavHost()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the app shows while the biometric lock is up. [DonaNavHost] isn't mounted yet at this point,
 * so the lock screen's fallback login form has no navigation of its own — without this, its
 * "manage houses" button and its tap-to-edit credential fields (the only place credentials *can* be
 * edited) would be dead ends. One flag is enough: the Houses screen is the sole destination
 * reachable from here.
 */
@Composable
private fun LockedContent(onUnlocked: () -> Unit) {
    var editHouseName by rememberSaveable { mutableStateOf<String?>(null) }
    var showHouses by rememberSaveable { mutableStateOf(false) }

    if (showHouses) {
        // No NavHost here to pop, so back has to be handled by hand or it would leave the app.
        BackHandler { showHouses = false }
        HousesRoute(
            onDone = { showHouses = false },
            editHouseName = editHouseName,
        )
        return
    }

    BiometricLockRoute(
        onUnlocked = onUnlocked,
        onManageHouses = {
            editHouseName = null
            showHouses = true
        },
        onEditHouse = { house ->
            editHouseName = house.name
            showHouses = true
        },
    )
}
