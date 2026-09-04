package com.neteinstein.donaclone.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveSessionStateUseCase
import com.neteinstein.donaclone.core.model.SessionStatus
import com.neteinstein.donaclone.feature.ambiences.AutomationEditorRoute
import com.neteinstein.donaclone.feature.devices.DeviceDetailRoute
import com.neteinstein.donaclone.feature.houses.HousesRoute
import com.neteinstein.donaclone.feature.login.LoginRoute
import org.koin.compose.koinInject

object DonaDestinations {
    const val LOGIN = "login"
    const val HOUSES = "houses"
    const val MAIN = "main"
    const val DEVICE_DETAIL_ARG = "deviceId"
    const val DEVICE_DETAIL = "device_detail/{$DEVICE_DETAIL_ARG}"
    const val AUTOMATION_EDITOR = "automation_editor"

    fun deviceDetailRoute(deviceId: Int) = "device_detail/$deviceId"
}

@Composable
fun DonaNavHost(navController: NavHostController = rememberNavController()) {
    // Reacts to a session drop that wasn't triggered by an explicit, user-initiated logout (see
    // AuthRepositoryImpl's reconnect-with-retry loop) by returning to Login. Explicit logouts
    // keep working through each screen's own onLoggedOut callback below; this observer is guarded
    // so it never double-navigates on top of that.
    val observeSessionState: ObserveSessionStateUseCase = koinInject()
    val sessionState by observeSessionState().collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(sessionState, backStackEntry) {
        val onAuthenticatedRoute =
            backStackEntry?.destination?.route?.let { route ->
                route == DonaDestinations.MAIN || route == DonaDestinations.DEVICE_DETAIL ||
                    route == DonaDestinations.AUTOMATION_EDITOR
            } == true
        if (sessionState == SessionStatus.DISCONNECTED && onAuthenticatedRoute) {
            navigateToLogin(navController)
        }
    }

    // A biometric unlock (see BiometricLockRoute) performs a full login before this composable
    // ever mounts, so starting fresh at LOGIN here would waste that and force a redundant manual
    // tap — start at MAIN whenever a session is already live.
    val getCurrentSession: GetCurrentSessionUseCase = koinInject()
    val startDestination = if (getCurrentSession() != null) DonaDestinations.MAIN else DonaDestinations.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 8 } },
    ) {
        composable(DonaDestinations.LOGIN) {
            LoginRoute(
                onLoggedIn = {
                    navController.navigate(DonaDestinations.MAIN) {
                        popUpTo(DonaDestinations.LOGIN) { inclusive = true }
                    }
                },
                onManageHouses = { navController.navigate(DonaDestinations.HOUSES) },
            )
        }

        composable(DonaDestinations.HOUSES) {
            HousesRoute(onDone = { navController.popBackStack() })
        }

        composable(DonaDestinations.MAIN) {
            MainScreen(
                onOpenDeviceDetail = { deviceId ->
                    navController.navigate(DonaDestinations.deviceDetailRoute(deviceId))
                },
                onOpenHouses = { navController.navigate(DonaDestinations.HOUSES) },
                onLoggedOut = { navigateToLogin(navController) },
                onCreateAutomation = { navController.navigate(DonaDestinations.AUTOMATION_EDITOR) },
            )
        }

        composable(
            DonaDestinations.DEVICE_DETAIL,
            arguments = listOf(navArgument(DonaDestinations.DEVICE_DETAIL_ARG) { type = NavType.IntType }),
        ) { backStack ->
            val deviceId = backStack.arguments?.getInt(DonaDestinations.DEVICE_DETAIL_ARG) ?: return@composable
            DeviceDetailRoute(deviceId = deviceId, onBack = { navController.popBackStack() })
        }

        composable(DonaDestinations.AUTOMATION_EDITOR) {
            AutomationEditorRoute(onDone = { navController.popBackStack() })
        }
    }
}

private fun navigateToLogin(navController: NavHostController) {
    navController.navigate(DonaDestinations.LOGIN) {
        popUpTo(DonaDestinations.MAIN) { inclusive = true }
    }
}
